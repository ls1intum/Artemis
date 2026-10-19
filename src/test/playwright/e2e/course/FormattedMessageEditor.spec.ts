import { expect, type Locator, type Page } from '@playwright/test';
import { test as artemisTest } from '../../support/fixtures';
import { admin, studentOne, studentTwo, studentThree, studentFour, UserRole } from '../../support/users';
import { generateUUID, readResponseJson } from '../../support/utils';
import { expectAligned, expectBelow, expectInside, expectNoHorizontalOverflow, expectNoHorizontalScrollAround, expectWithinViewport, forEachViewport } from '../../support/layout';
import { Course } from 'app/course/shared/entities/course.model';
import { Channel } from 'app/communication/shared/entities/conversation/channel.model';
import { Post } from 'app/communication/shared/entities/post.model';

interface Composer {
    course: Course;
    channel: Channel;
    editor: Locator;
    route: string;
}

// Every test owns its course and conversations. Browser editing never depends on seed message IDs,
// another worker's messages, or direct DOM content assignment that bypasses beforeinput/input.
const test = artemisTest.extend<{ composer: Composer; expandedToolbar: boolean }>({
    expandedToolbar: [true, { option: true }],
    composer: async ({ page, login, courseManagementAPIRequests, communicationAPIRequests, expandedToolbar }, use) => {
        await login(admin);
        const course = await courseManagementAPIRequests.createCourse({ courseName: `Formatted editor ${generateUUID()}` });
        try {
            await courseManagementAPIRequests.addStudentToCourse(course, studentOne);
            const channel = await communicationAPIRequests.createCourseMessageChannel(course, `editor-${generateUUID().slice(0, 8)}`, 'Native editing regressions', false, true);
            expect(channel.id).toBeDefined();
            await communicationAPIRequests.joinUserIntoChannel(course, channel.id!, studentOne);
            await login(studentOne);
            await communicationAPIRequests.acceptCodeOfConduct(course);
            const route = `/courses/${course.id}/communication?conversationId=${channel.id}`;
            await page.goto(route);
            const editor = page.getByTestId('formatted-message-input');
            await expect(editor).toBeVisible();
            await expect(editor).toHaveAttribute('contenteditable', 'true');
            if (expandedToolbar) {
                await page.getByTestId('message-toolbar-toggle').click();
                await expect(page.getByTestId('message-advanced-toolbar')).toBeVisible();
            }
            await use({ course, channel, editor, route });
        } finally {
            await courseManagementAPIRequests.deleteCourse(course, admin);
        }
    },
});

/** Set a real browser selection, including across formatting elements, without changing its DOM. */
async function selectText(editor: Locator, start: number, end: number, expectedText = '') {
    await editor.evaluate(
        (element, offsets) => {
            const walker = document.createTreeWalker(element, NodeFilter.SHOW_TEXT);
            const points: { node: Node; start: number; end: number }[] = [];
            let length = 0;
            while (walker.nextNode()) {
                const node = walker.currentNode;
                points.push({ node, start: length, end: length + (node.textContent?.length ?? 0) });
                length += node.textContent?.length ?? 0;
            }
            const locate = (offset: number) => {
                const point = points.find((candidate) => candidate.end >= offset);
                if (!point) throw new Error(`Selection offset ${offset} exceeds editor length ${length}`);
                return { node: point.node, offset: offset - point.start };
            };
            const from = locate(offsets.start);
            const to = locate(offsets.end);
            const range = document.createRange();
            range.setStart(from.node, from.offset);
            range.setEnd(to.node, to.offset);
            (element as HTMLElement).focus();
            const selection = window.getSelection()!;
            selection.removeAllRanges();
            selection.addRange(range);
        },
        { start, end },
    );
    // Range text follows the text-node offsets above; Selection text adds rendered paragraph breaks
    // in Chromium even when those breaks are not part of the selected document text nodes.
    await expect
        .poll(() =>
            editor.evaluate(() => {
                const selection = window.getSelection();
                return selection?.rangeCount ? selection.getRangeAt(0).toString() : undefined;
            }),
        )
        .toBe(expectedText);
}

async function expectCaret(editor: Locator, offset: number) {
    await expect
        .poll(() =>
            editor.evaluate((element) => {
                const selection = window.getSelection();
                if (!selection?.isCollapsed || !selection.anchorNode || !element.contains(selection.anchorNode)) return -1;
                const before = document.createRange();
                before.selectNodeContents(element);
                before.setEnd(selection.anchorNode, selection.anchorOffset);
                return before.toString().replace(/\u200b/g, '').length;
            }),
        )
        .toBe(offset);
    await expect(editor).toBeFocused();
}

async function expectText(editor: Locator, text: string) {
    await expect.poll(() => editor.evaluate((element) => element.textContent?.replace(/\u200b/g, ''))).toBe(text);
}

async function send(page: Page, composer: Composer, keyboard = false): Promise<Post> {
    const responsePromise = page.waitForResponse(
        (response) => response.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`) && response.request().method() === 'POST',
    );
    await expect(page.getByTestId('send-formatted-message')).toBeEnabled();
    if (keyboard) {
        await page.getByTestId('send-formatted-message').press('Enter');
    } else {
        await page.getByTestId('send-formatted-message').click();
    }
    const response = await responsePromise;
    expect(response.status()).toBe(201);
    const saved = await readResponseJson<Post>(response);
    expect(saved.id).toBeDefined();
    expect(saved.conversation?.id).toBe(composer.channel.id);
    expect(response.request().postDataJSON().content).toBe(saved.content);
    await expect(page.locator(`#item-${saved.id}`)).toBeVisible();
    await expectText(composer.editor, '');
    await expect(page.getByTestId('send-formatted-message')).toBeDisabled();
    return saved;
}

function postContent(page: Page, id: number) {
    return page.locator(`#item-${id}`).locator('jhi-posting-content');
}

async function reopen(page: Page, post: Post) {
    const posting = page.locator(`#item-${post.id}`);
    await posting.hover();
    await posting.getByTestId('posting-reaction-edit').click();
    const editor = posting.getByTestId('formatted-message-input');
    await expect(editor).toBeVisible();
    return { posting, editor };
}

async function saveEdited(page: Page, composer: Composer, posting: Locator, original: Post): Promise<Post> {
    const responsePromise = page.waitForResponse(
        (response) => response.request().method() === 'PUT' && response.url().endsWith(`/api/communication/courses/${composer.course.id}/messages/${original.id}`),
    );
    await expect(posting.locator('#save')).toBeEnabled();
    await posting.locator('#save').click();
    const response = await responsePromise;
    expect(response.status()).toBe(200);
    const saved = await readResponseJson<Post>(response);
    expect(saved.id).toBe(original.id);
    expect(response.request().postDataJSON().content).toBe(saved.content);
    await expect(posting.getByTestId('formatted-message-input')).toBeHidden();
    await expect(postContent(page, saved.id!)).toBeVisible();
    return saved;
}

test.describe('Formatted message editor native editing', { tag: '@fast' }, () => {
    test('literal dollars stay text through keyboard sending, reopening and saving without a Markdown editor', async ({ page, composer }) => {
        await composer.editor.fill('$x^2$');
        await expect(page.locator('jhi-markdown-editor-monaco')).toHaveCount(0);
        const saved = await send(page, composer, true);
        expect(saved.content).toBe('\\$x^2\\$');
        await expect(postContent(page, saved.id!)).toContainText('$x^2$');
        const { posting, editor } = await reopen(page, saved);
        await expectText(editor, '$x^2$');
        await selectText(editor, 5, 5);
        await expectCaret(editor, 5);
        await editor.pressSequentially(' literal');
        const edited = await saveEdited(page, composer, posting, saved);
        expect(edited.content).toBe('\\$x^2\\$ literal');
        await expect(postContent(page, edited.id!)).toContainText('$x^2$ literal');
        await expect(page.locator('jhi-markdown-editor-monaco')).toHaveCount(0);
    });

    test('unsupported existing formulas cannot be destructively saved and Cancel leaves normal composing available', async ({ page, composer, communicationAPIRequests }) => {
        const source = '$ x $';
        const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', source);
        const posting = page.locator(`#item-${original.id}`);
        const updates: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'PUT' && request.url().endsWith(`/messages/${original.id}`)) updates.push(request.url());
        });
        await expect(postContent(page, original.id!)).toContainText('x');
        await posting.hover();
        await posting.getByTestId('posting-reaction-edit').click();
        await expect(posting.getByTestId('unsupported-message-formatting')).toBeVisible();
        await expect(posting.getByTestId('formatted-message-input')).toHaveCount(0);
        await expect(page.locator('jhi-markdown-editor-monaco')).toHaveCount(0);
        await expect(posting.locator('#save')).toBeDisabled();
        await posting.getByRole('button', { name: 'Cancel', exact: true }).click();
        await expect(posting.getByTestId('unsupported-message-formatting')).toHaveCount(0);
        await expect(postContent(page, original.id!)).toContainText('x');
        await composer.editor.fill('A normal course message');
        const saved = await send(page, composer);
        expect(saved.content).toBe('A normal course message');
        expect(updates).toHaveLength(0);
    });

    for (const mark of [
        { tag: 'strong', markdown: '**marked**' },
        { tag: 'em', markdown: '*marked*' },
        { tag: 'ins', markdown: '<ins>marked</ins>' },
        { tag: 's', markdown: '~~marked~~' },
        { tag: 'code', markdown: '`marked`' },
    ]) {
        test(`typing can enter and leave ${mark.tag} formatting at a collapsed caret`, async ({ page, composer }) => {
            const { editor } = composer;
            await editor.pressSequentially('plain ');
            await page.getByTestId(`message-format-${mark.tag}`).click();
            await expectCaret(editor, 6);
            await page.keyboard.type('marked');
            await expect(editor.locator(mark.tag)).toHaveText('marked');
            await expectCaret(editor, 12);
            await page.getByTestId(`message-format-${mark.tag}`).click();
            await page.keyboard.type(' tail');
            await expectText(editor, 'plain marked tail');
            await expect(editor.locator(mark.tag)).toHaveText('marked');
            await expectCaret(editor, 17);

            const saved = await send(page, composer);
            expect(saved.content).toBe(`plain ${mark.markdown} tail`);
            await expect(postContent(page, saved.id!).locator(mark.tag === 's' ? 's, del' : mark.tag)).toHaveText('marked');
            await expect(postContent(page, saved.id!)).toContainText('plain marked tail');
        });
    }

    test('native IME composition commits one bold text run and typing can resume without formatting', async ({ page, context, browserName, composer }) => {
        test.skip(browserName !== 'chromium', 'Native IME protocol available in Chromium');
        const { editor } = composer;
        const sentRequests: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'POST' && request.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`)) sentRequests.push(request.url());
        });
        await editor.pressSequentially('plain ');
        await page.getByTestId('message-format-strong').click();
        await expectCaret(editor, 6);
        const session = await context.newCDPSession(page);
        try {
            await session.send('Input.imeSetComposition', { text: '漢字', selectionStart: 2, selectionEnd: 2 });
            await expect(editor).toContainText('漢字');
            expect(sentRequests).toHaveLength(0);
            await session.send('Input.insertText', { text: '漢字' });
            await expectText(editor, 'plain 漢字');
            await expect(editor.locator('strong')).toHaveText('漢字');
            await expectCaret(editor, 8);
            expect(sentRequests).toHaveLength(0);
            await page.keyboard.press('ControlOrMeta+z');
            await expectText(editor, 'plain ');
            await expectCaret(editor, 6);
            await page.keyboard.press('ControlOrMeta+Shift+z');
            await expectText(editor, 'plain 漢字');
            await expect(editor.locator('strong')).toHaveText('漢字');
            await expectCaret(editor, 8);
            await page.getByTestId('message-format-strong').click();
            await page.keyboard.type(' tail');
            await expectText(editor, 'plain 漢字 tail');
            await expect(editor.locator('strong')).toHaveText('漢字');
            await expectCaret(editor, 13);
            const saved = await send(page, composer);
            expect(saved.content).toBe('plain **漢字** tail');
            expect(sentRequests).toHaveLength(1);
            await expect(postContent(page, saved.id!).locator('strong')).toHaveText('漢字');
        } finally {
            await session.detach();
        }
    });

    test('partial removal across mixed marks keeps italic text and native replacement at the selection', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('alpha beta gamma');
        await selectText(editor, 0, 10, 'alpha beta');
        await page.getByTestId('message-format-strong').click();
        await expect(editor.locator('strong')).toHaveText('alpha beta');
        await selectText(editor, 6, 16, 'beta gamma');
        await page.getByTestId('message-format-em').click();
        await expectText(editor, 'alpha beta gamma');
        await expect.poll(async () => (await editor.locator('em').allTextContents()).join('')).toBe('beta gamma');
        await selectText(editor, 6, 10, 'beta');
        await page.getByTestId('message-format-strong').click();
        await expect(editor.locator('strong')).toHaveText('alpha ');
        await expect.poll(async () => (await editor.locator('em').allTextContents()).join('')).toBe('beta gamma');
        await selectText(editor, 7, 10, 'eta');
        await page.keyboard.insertText('ETA');
        await expectText(editor, 'alpha bETA gamma');
        await expectCaret(editor, 10);
        await expect.poll(async () => (await editor.locator('em').allTextContents()).join('')).toBe('bETA gamma');

        const saved = await send(page, composer);
        expect(saved.content).toBe('**alpha** *bETA gamma*');
        await expect(postContent(page, saved.id!).locator('strong')).toHaveText('alpha');
        await expect(postContent(page, saved.id!).locator('em')).toHaveText('bETA gamma');
    });

    test('native replacement across paragraphs merges them and persists one paragraph', async ({ page, composer, communicationAPIRequests }) => {
        const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'First paragraph\n\nSecond paragraph');
        const { posting, editor } = await reopen(page, original);
        await expect(editor.locator(':scope > p')).toHaveText(['First paragraph', 'Second paragraph']);
        await selectText(editor, 6, 21, 'paragraphSecond');
        await page.keyboard.insertText('new');
        await expect(editor.locator(':scope > p')).toHaveCount(1);
        await expectText(editor, 'First new paragraph');
        await expectCaret(editor, 9);
        const saved = await saveEdited(page, composer, posting, original);
        expect(saved.content).toBe('First new paragraph');
        await expect(postContent(page, saved.id!)).toContainText('First new paragraph');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator(':scope > p')).toHaveText(['First new paragraph']);
        await expectText(reopened.editor, 'First new paragraph');
    });

    test('keyboard undo and redo restore formatting, replacement text and the native selection', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('alpha beta');
        await selectText(editor, 6, 10, 'beta');
        await page.keyboard.press('ControlOrMeta+b');
        await expect(editor.locator('strong')).toHaveText('beta');
        await page.keyboard.press('ControlOrMeta+z');
        await expect(editor.locator('strong')).toHaveCount(0);
        await expectText(editor, 'alpha beta');
        await page.keyboard.press('ControlOrMeta+Shift+z');
        await expect(editor.locator('strong')).toHaveText('beta');
        await expect.poll(() => editor.evaluate(() => window.getSelection()?.toString())).toBe('beta');
        await page.keyboard.insertText('BETA');
        await expectText(editor, 'alpha BETA');
        await expectCaret(editor, 10);
        await page.keyboard.press('ControlOrMeta+z');
        await expectText(editor, 'alpha beta');
        await expect(editor.locator('strong')).toHaveText('beta');
        await page.keyboard.press('ControlOrMeta+Shift+z');
        await expectText(editor, 'alpha BETA');
        await expect(editor.locator('strong')).toHaveText('BETA');
        await expectCaret(editor, 10);
        expect((await send(page, composer)).content).toBe('alpha **BETA**');
    });

    for (const list of ['ul', 'ol']) {
        test(`native Enter continues ${list} items and exits an empty item without sending`, async ({ page, composer }) => {
            const { editor } = composer;
            const sentRequests: string[] = [];
            page.on('request', (request) => {
                if (request.method() === 'POST' && request.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`)) sentRequests.push(request.url());
            });
            await editor.pressSequentially('first');
            await page.getByTestId(`message-block-${list}`).click();
            await expect(editor.locator(`${list} > li`)).toHaveText(['first']);
            await page.keyboard.press('Enter');
            await page.keyboard.type('second');
            await expect(editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
            await page.keyboard.press('Enter');
            await page.keyboard.press('Enter');
            await page.keyboard.type('outside');
            await expect(editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
            await expectText(editor, 'firstsecondoutside');
            expect(sentRequests).toHaveLength(0);
            await expectCaret(editor, 18);
            const saved = await send(page, composer);
            expect(saved.content).toBe(list === 'ul' ? '- first\n- second\n\noutside' : '1. first\n2. second\n\noutside');
            await expect(postContent(page, saved.id!).locator(`${list} > li`)).toHaveText(['first', 'second']);
            await expect(postContent(page, saved.id!)).toContainText('outside');
        });
    }

    for (const list of ['ul', 'ol']) {
        test(`Backspace exits an empty ${list} item onto its own line between surrounding paragraphs`, async ({ page, composer, communicationAPIRequests }) => {
            const listMarkdown = list === 'ul' ? '- first' : '1. first';
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', `Before\n\n${listMarkdown}`);
            const { posting, editor } = await reopen(page, original);
            await expect(editor.locator(`${list} > li`)).toHaveText(['first']);
            await selectText(editor, 11, 11);
            await page.keyboard.press('Enter');
            await expect(editor.locator(`${list} > li`)).toHaveCount(2);
            await page.keyboard.press('Backspace');
            await expect(editor.locator(`${list} > li`)).toHaveText(['first']);
            await expectCaret(editor, 11);
            // Text offsets alone cannot distinguish the end of the item from the new empty line.
            await expect
                .poll(() =>
                    editor.evaluate((element) => {
                        const node = window.getSelection()?.anchorNode;
                        const parent = node instanceof Element ? node : node?.parentElement;
                        return !!parent && element.contains(parent) && !parent.closest('li, ul, ol');
                    }),
                )
                .toBe(true);
            await page.keyboard.type('After');
            await expect(editor.locator(':scope > p')).toHaveText(['Before', 'After']);
            await expectCaret(editor, 16);
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(`Before\n\n${listMarkdown}\n\nAfter`);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(`${list} > li`)).toHaveText(['first']);
            await expect(reopened.editor.locator(':scope > p')).toHaveText(['Before', 'After']);
        });
    }

    test('toolbar pressed states follow native caret movement and pending format toggles', async ({ page, composer, communicationAPIRequests }) => {
        const original = await communicationAPIRequests.createCourseMessage(
            composer.course,
            composer.channel.id!,
            'channel',
            'plain **bold** *italic*\n\n- bullet\n\n1. numbered\n\n> quote\n\n```\ncode\n```\n\nafter',
        );
        const { posting, editor } = await reopen(page, original);
        const controls = ['strong', 'em', 'ul', 'ol', 'blockquote', 'pre'];
        for (const target of [
            { text: 'bold', active: 'strong' },
            { text: 'italic', active: 'em' },
            { text: 'plain', active: undefined },
            { text: 'bullet', active: 'ul' },
            { text: 'numbered', active: 'ol' },
            { text: 'quote', active: 'blockquote' },
            { text: 'code', active: 'pre' },
            { text: 'after', active: undefined },
        ]) {
            const offset = (await editor.textContent())!.indexOf(target.text) + 1;
            expect(offset).toBeGreaterThan(0);
            await selectText(editor, offset, offset);
            await page.keyboard.press('ArrowRight');
            for (const control of controls) {
                const kind = ['strong', 'em'].includes(control) ? 'format' : 'block';
                await expect(posting.getByTestId(`message-${kind}-${control}`)).toHaveAttribute('aria-pressed', String(control === target.active));
            }
        }
        const bold = posting.getByTestId('message-format-strong');
        const italic = posting.getByTestId('message-format-em');
        await bold.click();
        await expect(bold).toHaveAttribute('aria-pressed', 'true');
        await italic.click();
        await expect(bold).toHaveAttribute('aria-pressed', 'true');
        await expect(italic).toHaveAttribute('aria-pressed', 'true');
        await page.keyboard.type('X');
        await expect(editor.locator('strong em, em strong')).toHaveText('X');
        await bold.click();
        await expect(bold).toHaveAttribute('aria-pressed', 'false');
        await expect(italic).toHaveAttribute('aria-pressed', 'true');
        await italic.click();
        await expect(italic).toHaveAttribute('aria-pressed', 'false');
        await page.keyboard.type('Y');
        await expect(editor.locator('strong em, em strong')).toHaveText('X');
        await expect(editor).toContainText('afXYter');
    });

    for (const list of ['ul', 'ol']) {
        test(`applying ${list} after Shift+Enter changes only the current line and preserves editing history`, async ({ page, composer }) => {
            const { editor } = composer;
            await editor.pressSequentially('Lecture notes');
            await editor.press('Shift+Enter');
            await page.keyboard.type('Review recurrence');
            await page.getByTestId(`message-block-${list}`).click();
            await expect(editor.locator(':scope > p')).toHaveText(['Lecture notes']);
            await expect(editor.locator(`${list} > li`)).toHaveText(['Review recurrence']);
            await expectCaret(editor, 30);
            await editor.press('ControlOrMeta+z');
            await expect(editor.locator('ul, ol')).toHaveCount(0);
            await expect(editor.locator('br')).toHaveCount(1);
            await expectText(editor, 'Lecture notesReview recurrence');
            await editor.press('ControlOrMeta+Shift+z');
            await expect(editor.locator(`${list} > li`)).toHaveText(['Review recurrence']);
            await page.keyboard.type(' today');
            await expect(editor.locator(':scope > p')).toHaveText(['Lecture notes']);
            await expect(editor.locator(`${list} > li`)).toHaveText(['Review recurrence today']);
            const saved = await send(page, composer);
            expect(saved.content).toBe(`Lecture notes\n\n${list === 'ul' ? '-' : '1.'} Review recurrence today`);
            await expect(postContent(page, saved.id!).locator(`${list} > li`)).toHaveText(['Review recurrence today']);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(':scope > p')).toHaveText(['Lecture notes']);
            await expect(reopened.editor.locator(`${list} > li`)).toHaveText(['Review recurrence today']);
        });
    }

    test('code Enter and Shift+Enter preserve literal lines when returning to a paragraph', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('const answer = 42;');
        await page.getByTestId('message-block-pre').click();
        await page.keyboard.press('Enter');
        await page.keyboard.type('console.log(answer);');
        await page.keyboard.press('Shift+Enter');
        await page.keyboard.type('// done');
        await expect(editor.locator('pre code')).toHaveCount(1);
        await expect
            .poll(() => editor.locator('pre code').evaluate((code) => (code as HTMLElement).innerText.replace(/\u200b/g, '')))
            .toBe('const answer = 42;\nconsole.log(answer);\n// done');
        await page.getByTestId('message-block-pre').click();
        await expect(editor.locator('pre')).toHaveCount(0);
        await page.keyboard.type(' tail');
        await expectText(editor, 'const answer = 42;console.log(answer);// done tail');
        const saved = await send(page, composer);
        expect(saved.content).toBe('const answer = 42;\nconsole.log(answer);\n// done tail');
        await expect(postContent(page, saved.id!)).toContainText('// done tail');
    });

    test('changing quote formatting retains a native soft line break and typed suffix', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('quoted');
        await page.getByTestId('message-block-blockquote').click();
        await page.keyboard.press('Shift+Enter');
        await page.keyboard.type('second line');
        await expect(editor.locator('blockquote')).toContainText('second line');
        await page.getByTestId('message-block-blockquote').click();
        await expect(editor.locator('blockquote')).toHaveCount(0);
        await page.keyboard.type(' tail');
        await expectText(editor, 'quotedsecond line tail');
        const saved = await send(page, composer);
        expect(saved.content).toBe('quoted\nsecond line tail');
        await expect(postContent(page, saved.id!)).toContainText('second line tail');
    });

    test('link toolbar keeps the selection while rejecting an unsafe URL and applying a valid link', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('Read guide today');
        await selectText(editor, 5, 10, 'guide');
        await page.getByTestId('message-link-button').click();
        await expect(page.getByTestId('message-link-text')).toHaveValue('guide');
        await page.getByTestId('message-link-url').fill('javascript:alert(1)');
        await page.getByTestId('apply-message-link').click();
        await expect(page.getByTestId('message-link-url')).toHaveAttribute('aria-invalid', 'true');
        await expect(editor.locator('a')).toHaveCount(0);
        await expectText(editor, 'Read guide today');
        await page.getByTestId('message-link-url').fill('https://example.com/guide?section=2');
        await page.getByTestId('apply-message-link').click();
        await expect(page.getByTestId('message-link-url')).toBeHidden();
        await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.com/guide?section=2');
        await expect(editor.locator('a')).toHaveText('guide');
        await expectText(editor, 'Read guide today');
        await expectCaret(editor, 10);
        const saved = await send(page, composer);
        expect(saved.content).toBe('Read [guide](https://example.com/guide?section=2) today');
        await expect(postContent(page, saved.id!).getByRole('link', { name: 'guide', exact: true })).toHaveAttribute('href', 'https://example.com/guide?section=2');
    });

    for (const trigger of ['toolbar', 'shortcut']) {
        test(`${trigger} edits the existing link at a native caret without nesting or duplicating anchors`, async ({ page, composer, communicationAPIRequests }) => {
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'Read [guide](https://example.com/old) today');
            const { posting, editor } = await reopen(page, original);
            await selectText(editor, 7, 7);
            if (trigger === 'toolbar') {
                await posting.getByTestId('message-link-button').click();
            } else {
                await page.keyboard.press('ControlOrMeta+k');
            }
            await expect(posting.getByTestId('message-link-text')).toHaveValue('guide');
            await expect(posting.getByTestId('message-link-url')).toHaveValue('https://example.com/old');
            await posting.getByTestId('message-link-url').fill('https://example.com/new');
            await posting.getByTestId('apply-message-link').click();
            await expect(editor.locator('a')).toHaveCount(1);
            await expect(editor.locator('a')).toHaveText('guide');
            await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.com/new');
            await expectCaret(editor, 10);
            await page.keyboard.type('!');
            await expect(editor.locator('a')).toHaveText('guide');
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe('Read [guide](https://example.com/new)! today');
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator('a')).toHaveCount(1);
            await expect(reopened.editor.locator('a')).toHaveAttribute('href', 'https://example.com/new');
            await expectText(reopened.editor, 'Read guide! today');
        });
    }

    for (const trigger of ['toolbar', 'shortcut']) {
        test(`${trigger} updates and unlinks a code-formatted link label across saves and reopening`, async ({ page, composer, communicationAPIRequests }) => {
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'Read [`List`](https://example.com/old) today');
            const { posting, editor } = await reopen(page, original);
            await selectText(editor, 7, 7);
            await expect(posting.getByTestId('message-link-button')).toBeEnabled();
            if (trigger === 'toolbar') {
                await posting.getByTestId('message-link-button').click();
            } else {
                await page.keyboard.press('ControlOrMeta+k');
            }
            await expect(posting.getByTestId('message-link-text')).toHaveValue('List');
            await expect(posting.getByTestId('message-link-url')).toHaveValue('https://example.com/old');
            await posting.getByTestId('message-link-url').fill('https://example.com/new');
            await posting.getByTestId('apply-message-link').click();
            await expect(editor.locator('a')).toHaveCount(1);
            await expect(editor.locator('a code')).toHaveText('List');
            await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.com/new');
            const updated = await saveEdited(page, composer, posting, original);
            expect(updated.content).toBe('Read [`List`](https://example.com/new) today');
            const second = await reopen(page, updated);
            await expect(second.editor.locator('a code')).toHaveText('List');
            await expect(second.editor.locator('a')).toHaveAttribute('href', 'https://example.com/new');
            await second.editor.getByRole('link', { name: 'List', exact: true }).click();
            await expect(second.posting.getByTestId('message-link-url')).toHaveValue('https://example.com/new');
            await second.posting.getByTestId('remove-message-link').click();
            await expect(second.editor.locator('a')).toHaveCount(0);
            await expect(second.editor.locator('code')).toHaveText('List');
            const unlinked = await saveEdited(page, composer, second.posting, updated);
            expect(unlinked.content).toBe('Read `List` today');
            const third = await reopen(page, unlinked);
            await expect(third.editor.locator('a')).toHaveCount(0);
            await expect(third.editor.locator('code')).toHaveText('List');
            await expectText(third.editor, 'Read List today');
        });
    }

    test('link form retains input focus but dismisses on a second toolbar click or a moved mouse and keyboard caret', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('Read guide today');
        await selectText(editor, 5, 10, 'guide');
        await page.getByTestId('message-link-button').click();
        await expect(page.getByTestId('message-link-text')).toHaveValue('guide');
        await page.getByTestId('message-link-url').fill('https://example.com/unsaved');
        await expect(page.getByTestId('message-link-url')).toBeFocused();
        await expect(page.getByTestId('message-link-url')).toBeVisible();
        await page.getByTestId('message-link-button').click();
        await expect(page.getByTestId('message-link-url')).toBeHidden();
        await expect(editor).toBeFocused();
        await page.getByTestId('message-link-button').click();
        await expect(page.getByTestId('message-link-url')).toBeVisible();
        await editor.click({ position: { x: 10, y: 12 } });
        await expect(page.getByTestId('message-link-url')).toBeHidden();
        await expect(editor).toBeFocused();
        await selectText(editor, 5, 10, 'guide');
        await page.keyboard.press('ControlOrMeta+k');
        await expect(page.getByTestId('message-link-url')).toBeVisible();
        await page.keyboard.press('ArrowLeft');
        await expect(page.getByTestId('message-link-url')).toBeHidden();
        await expectCaret(editor, 5);
        await page.keyboard.type('new ');
        await expectText(editor, 'Read new guide today');
        await expect(editor.locator('a')).toHaveCount(0);
        const saved = await send(page, composer);
        expect(saved.content).toBe('Read new guide today');
    });

    for (const code of [
        { name: 'inline code', markdown: 'Before `literal` after', selector: 'p > code', content: 'Before `lite!ral` after' },
        { name: 'code block', markdown: '```\nliteral\n```', selector: 'pre code', content: '```\nlite!ral\n```' },
    ]) {
        test(`link insertion stays disabled inside ${code.name} through the toolbar and keyboard shortcut`, async ({ page, composer, communicationAPIRequests }) => {
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', code.markdown);
            const { posting, editor } = await reopen(page, original);
            const offset = (await editor.textContent())!.indexOf('literal') + 3;
            await selectText(editor, offset, offset);
            await page.keyboard.press('ArrowRight');
            await expect(posting.getByTestId('message-link-button')).toBeDisabled();
            await page.keyboard.press('ControlOrMeta+k');
            await expect(posting.getByTestId('message-link-url')).toBeHidden();
            await expect(editor.locator('a')).toHaveCount(0);
            await expect(editor.locator(code.selector)).toHaveText('literal');
            await expectCaret(editor, offset + 1);
            await page.keyboard.type('!');
            await expect(editor.locator(code.selector)).toHaveText('lite!ral');
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(code.content);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator('a')).toHaveCount(0);
            await expect(reopened.editor.locator(code.selector)).toHaveText('lite!ral');
        });
    }

    test('native multiline code is persisted literally and reopened as one editable code block', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('<b>literal</b>');
        await page.getByTestId('message-block-pre').click();
        await page.keyboard.press('Enter');
        await page.keyboard.type('**not bold**');
        await expect(editor.locator('pre code')).toHaveCount(1);
        await expect(editor.locator('strong, b')).toHaveCount(0);
        const saved = await send(page, composer);
        expect(saved.content).toBe('```\n<b>literal</b>\n**not bold**\n```');
        await expect(postContent(page, saved.id!).locator('pre code')).toHaveText('<b>literal</b>\n**not bold**');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('pre code')).toHaveCount(1);
        await expect(reopened.editor.locator('strong, b')).toHaveCount(0);
        await expect(reopened.editor.locator('pre code')).toContainText('**not bold**');
        await expect(reopened.editor.locator('pre code')).toContainText('<b>literal</b>');
    });

    test('link and channel reference survive partial formatting, save and a second edit', async ({ page, composer, communicationAPIRequests }) => {
        const reference = `[channel]${composer.channel.name}(${composer.channel.id})[/channel]`;
        const original = await communicationAPIRequests.createCourseMessage(
            composer.course,
            composer.channel.id!,
            'channel',
            `Read [guide](https://example.com/guide) and ${reference} today`,
        );
        await expect(postContent(page, original.id!)).toContainText('guide');
        const { posting, editor } = await reopen(page, original);
        await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.com/guide');
        await expect(editor.locator('[data-message-reference]')).toHaveAttribute('data-message-reference', reference);
        await selectText(editor, 5, 10, 'guide');
        await posting.getByTestId('message-format-strong').click();
        await expect(editor.locator('a')).toHaveText('guide');
        await expect(editor.locator('a strong, strong a')).toHaveText('guide');
        const length = (await editor.textContent())!.length;
        await selectText(editor, length, length);
        await expectCaret(editor, length);
        await page.keyboard.type('!');
        const updatedResponse = page.waitForResponse((response) => response.request().method() === 'PUT' && response.url().endsWith(`/messages/${original.id}`));
        await posting.locator('#save').click();
        const response = await updatedResponse;
        expect(response.ok()).toBe(true);
        expect(response.request().postDataJSON().content).toContain(reference);
        expect(response.request().postDataJSON().content).toContain('https://example.com/guide');
        expect(response.request().postDataJSON().content).toContain('today!');
        await expect(editor).toBeHidden();
        const reopened = await reopen(page, original);
        await expect(reopened.editor.locator('a')).toHaveAttribute('href', 'https://example.com/guide');
        await expect(reopened.editor.locator('a strong, strong a')).toHaveText('guide');
        await expect(reopened.editor.locator('[data-message-reference]')).toHaveAttribute('data-message-reference', reference);
        await expect(reopened.editor.locator('[data-message-reference]')).toHaveAttribute('contenteditable', 'false');
        await expect(reopened.editor).toContainText('today!');
    });

    test('creating a link preserves separate bold and italic runs through save and reopen', async ({ page, composer, communicationAPIRequests }) => {
        const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'Read **bold** and *italic* next');
        const { posting, editor } = await reopen(page, original);
        await selectText(editor, 5, 20, 'bold and italic');
        await posting.getByTestId('message-link-button').click();
        await expect(posting.getByTestId('message-link-text')).toHaveValue('bold and italic');
        await posting.getByTestId('message-link-url').fill('https://example.org/guide');
        await posting.getByTestId('apply-message-link').click();
        await expect(editor.locator('a strong')).toHaveText('bold');
        await expect(editor.locator('a em')).toHaveText('italic');
        await expectText(editor, 'Read bold and italic next');
        const saved = await saveEdited(page, composer, posting, original);
        expect(saved.content).toBe('Read [**bold** and *italic*](https://example.org/guide) next');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('a')).toHaveAttribute('href', 'https://example.org/guide');
        await expect(reopened.editor.locator('a strong')).toHaveText('bold');
        await expect(reopened.editor.locator('a em')).toHaveText('italic');
        await expectText(reopened.editor, 'Read bold and italic next');
    });

    test('links a single paragraph selected with native Select All and keeps its inline formatting after reopening', async ({ page, composer, communicationAPIRequests }) => {
        const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'Read **course** guide');
        const { posting, editor } = await reopen(page, original);
        await editor.click();
        await page.keyboard.press('ControlOrMeta+a');
        await expect(posting.getByTestId('message-link-button')).toBeEnabled();
        await posting.getByTestId('message-link-button').click();
        await expect(posting.getByTestId('message-link-text')).toHaveValue('Read course guide');
        await posting.getByTestId('message-link-url').fill('https://example.org/guide');
        await posting.getByTestId('apply-message-link').click();
        await expect(editor.locator('p')).toHaveCount(1);
        await expect(editor.locator('p > a strong')).toHaveText('course');
        const saved = await saveEdited(page, composer, posting, original);
        expect(saved.content).toBe('[Read **course** guide](https://example.org/guide)');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('p > a strong')).toHaveText('course');
        await expect(reopened.editor.locator('a')).toHaveAttribute('href', 'https://example.org/guide');
        await expectText(reopened.editor, 'Read course guide');
    });

    for (const example of [
        {
            kind: 'paragraphs',
            message: 'First paragraph\n\nSecond paragraph',
            text: 'First paragraphSecond paragraph',
            selector: 'p',
            expected: '[First](https://example.org/guide) paragraph\n\nSecond paragraph',
        },
        {
            kind: 'list items',
            message: '- First item\n- Second item',
            text: 'First itemSecond item',
            selector: 'li',
            expected: '- [First](https://example.org/guide) item\n- Second item',
        },
    ]) {
        test(`link actions reject selections across ${example.kind} and preserve the blocks when linking within one`, async ({ page, composer, communicationAPIRequests }) => {
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', example.message);
            const { posting, editor } = await reopen(page, original);
            await selectText(editor, 0, example.text.length, example.text);
            await expect(posting.getByTestId('message-link-button')).toBeDisabled();
            const html = await editor.innerHTML();
            await page.keyboard.press('ControlOrMeta+k');
            await expect(posting.getByTestId('message-link-url')).toBeHidden();
            expect(await editor.innerHTML()).toBe(html);
            await expect(editor.locator(example.selector)).toHaveCount(2);

            await selectText(editor, 0, 5, 'First');
            await expect(posting.getByTestId('message-link-button')).toBeEnabled();
            await posting.getByTestId('message-link-button').click();
            await posting.getByTestId('message-link-url').fill('https://example.org/guide');
            await posting.getByTestId('apply-message-link').click();
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(example.expected);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(example.selector)).toHaveCount(2);
            await expect(reopened.editor.locator('a')).toHaveAttribute('href', 'https://example.org/guide');
            await expect(reopened.editor.locator('a')).toHaveText('First');
        });
    }

    for (const fieldId of ['message-link-text', 'message-link-url']) {
        test(`IME confirmation in ${fieldId} leaves the link form open until normal Enter`, async ({ page, composer, communicationAPIRequests }) => {
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', '[Guide](https://example.org/old)');
            const { posting, editor } = await reopen(page, original);
            await editor.getByRole('link', { name: 'Guide', exact: true }).click();
            const input = posting.getByTestId(fieldId);
            await input.fill(fieldId === 'message-link-text' ? '日本語' : 'https://example.org/new');
            await input.dispatchEvent('compositionstart');
            for (const options of [{}, { isComposing: true }, { keyCode: 229 }]) {
                const prevented = await input.evaluate((element, init) => {
                    const event = new KeyboardEvent('keydown', { key: 'Enter', bubbles: true, cancelable: true, ...init });
                    element.dispatchEvent(event);
                    return event.defaultPrevented;
                }, options);
                expect(prevented).toBe(false);
                await expect(input).toBeVisible();
                await expect(editor.locator('a')).toHaveText('Guide');
                await expect(editor.locator('a')).toHaveAttribute('href', 'https://example.org/old');
                await input.dispatchEvent('compositionend');
            }
            await input.press('Enter');
            await expect(input).toBeHidden();
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(fieldId === 'message-link-text' ? '[日本語](https://example.org/old)' : '[Guide](https://example.org/new)');
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator('a')).toHaveText(fieldId === 'message-link-text' ? '日本語' : 'Guide');
            await expect(reopened.editor.locator('a')).toHaveAttribute('href', fieldId === 'message-link-url' ? 'https://example.org/new' : 'https://example.org/old');
        });
    }

    for (const selection of [
        { start: 0, end: 26, label: 'Read guide and notes today', markdown: '[Read **guide** and *notes* today](https://example.org/new)', links: 1 },
        { start: 7, end: 26, label: 'ide and notes today', markdown: 'Read [**gu**](https://example.org/old)[**ide** and *notes* today](https://example.org/new)', links: 2 },
        {
            start: 0,
            end: 7,
            label: 'Read gu',
            markdown: '[Read **gu**](https://example.org/new)[**ide**](https://example.org/old) and [*notes*](https://example.org/notes) today',
            links: 3,
        },
    ]) {
        test(`relinking from ${selection.start} to ${selection.end} preserves marks and one new destination after reopening`, async ({
            page,
            composer,
            communicationAPIRequests,
        }) => {
            const original = await communicationAPIRequests.createCourseMessage(
                composer.course,
                composer.channel.id!,
                'channel',
                'Read [**guide**](https://example.org/old) and [*notes*](https://example.org/notes) today',
            );
            const { posting, editor } = await reopen(page, original);
            await selectText(editor, selection.start, selection.end, selection.label);
            await posting.getByTestId('message-link-button').click();
            await expect(posting.getByTestId('message-link-text')).toHaveValue(selection.label);
            await posting.getByTestId('message-link-url').fill('https://example.org/new');
            await posting.getByTestId('apply-message-link').click();
            await expect(editor.locator('a a')).toHaveCount(0);
            await expect(editor.locator('a')).toHaveCount(selection.links);
            await expect(editor.locator('a[href="https://example.org/new"]')).toHaveText(selection.label);
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(selection.markdown);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator('a a')).toHaveCount(0);
            await expect(reopened.editor.locator('a')).toHaveCount(selection.links);
            await expect(reopened.editor.locator('a[href="https://example.org/new"]')).toHaveText(selection.label);
            await expect(reopened.editor.locator('a[href="https://example.org/new"] strong')).toHaveCount(1);
            await expectText(reopened.editor, 'Read guide and notes today');
        });
    }

    test('Safari-style IME confirmation after compositionend cannot bubble to send', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('日本語');
        await editor.dispatchEvent('compositionstart');
        await editor.dispatchEvent('compositionend', { data: '日本語' });
        const handled = await editor.evaluate((element) => {
            let bubbled = false;
            const parent = element.parentElement!;
            const listener = () => {
                bubbled = true;
            };
            parent.addEventListener('keydown', listener);
            const enter = new KeyboardEvent('keydown', { key: 'Enter', keyCode: 229, isComposing: false, bubbles: true, cancelable: true });
            element.dispatchEvent(enter);
            parent.removeEventListener('keydown', listener);
            return { bubbled, prevented: enter.defaultPrevented };
        });
        expect(handled).toEqual({ bubbled: false, prevented: false });
        await expectText(editor, '日本語');
        const saved = await send(page, composer);
        expect(saved.content).toBe('日本語');
    });

    test('editing a bold italic link URL keeps its label marks and resumes native typing outside the link', async ({ page, composer, communicationAPIRequests }) => {
        const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'Read [***guide***](https://example.com/old) today');
        const { posting, editor } = await reopen(page, original);
        const link = editor.getByRole('link', { name: 'guide', exact: true });
        await expect(link.locator('strong em, em strong')).toHaveText('guide');
        await link.click();
        await expect(posting.getByTestId('message-link-text')).toHaveValue('guide');
        await expect(posting.getByTestId('message-link-url')).toHaveValue('https://example.com/old');
        await posting.getByTestId('message-link-url').fill('https://example.com/new?chapter=2');
        await posting.getByTestId('apply-message-link').click();
        await expect(link).toHaveAttribute('href', 'https://example.com/new?chapter=2');
        await expect(link.locator('strong em, em strong')).toHaveText('guide');
        await expectCaret(editor, 10);
        await page.keyboard.type('!');
        await expectText(editor, 'Read guide! today');
        await expectCaret(editor, 11);
        await expect(link).toHaveText('guide');
        await expect(link.locator('strong em, em strong')).toHaveText('guide');
        const saved = await saveEdited(page, composer, posting, original);
        expect(saved.content).toBe('Read [***guide***](https://example.com/new?chapter=2)! today');
        await expect(postContent(page, saved.id!).getByRole('link', { name: 'guide', exact: true })).toHaveAttribute('href', 'https://example.com/new?chapter=2');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('a strong em, a em strong')).toHaveText('guide');
        await expectText(reopened.editor, 'Read guide! today');
    });

    test('unlinking keeps the bold italic label and places the caret after it for native typing', async ({ page, composer, communicationAPIRequests }) => {
        const original = await communicationAPIRequests.createCourseMessage(
            composer.course,
            composer.channel.id!,
            'channel',
            'Read [***guide***](https://example.com/guide) today',
        );
        const { posting, editor } = await reopen(page, original);
        await editor.getByRole('link', { name: 'guide', exact: true }).click();
        await posting.getByTestId('remove-message-link').click();
        await expect(editor.locator('a')).toHaveCount(0);
        await expect(editor.locator('strong em, em strong')).toHaveText('guide');
        await expectText(editor, 'Read guide today');
        await expectCaret(editor, 10);
        await page.keyboard.type('!');
        await expectText(editor, 'Read guide! today');
        await expectCaret(editor, 11);
        const saved = await saveEdited(page, composer, posting, original);
        expect(saved.content).not.toContain('https://example.com/guide');
        expect(saved.content).not.toContain('[');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('a')).toHaveCount(0);
        await expectText(reopened.editor, 'Read guide! today');
        await expect(reopened.editor.locator('strong')).toContainText('guide');
        await expect(reopened.editor.locator('em')).toContainText('guide');
    });

    for (const kind of ['link', 'channel'] as const) {
        test(`Backspace selects the complete atomic ${kind} before deleting it and undo restores its wire payload`, async ({ page, composer, communicationAPIRequests }) => {
            const payload = kind === 'link' ? '[guide](https://example.com/guide)' : `[channel]${composer.channel.name}(${composer.channel.id})[/channel]`;
            const label = kind === 'link' ? 'guide' : `#${composer.channel.name}`;
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', `Before ${payload} after`);
            const { posting, editor } = await reopen(page, original);
            const item = editor.locator(kind === 'link' ? 'a' : '[data-message-reference]');
            await expectText(editor, `Before ${label} after`);
            // Completion-style caret: one separator after the item. The separator is consumed with it.
            await selectText(editor, 7 + label.length + 1, 7 + label.length + 1);
            await page.keyboard.press('Backspace');
            await expectText(editor, `Before ${label} after`);
            await expect(item).toHaveAttribute('data-message-selected', '');
            await expect(editor.locator('[data-message-selected]')).toHaveCount(1);
            await expect.poll(() => editor.evaluate(() => window.getSelection()?.toString())).toBe(label);
            await page.keyboard.press('Backspace');
            await expect(item).toHaveCount(0);
            await expectText(editor, 'Before after');
            await expectCaret(editor, 7);
            await page.keyboard.press('ControlOrMeta+z');
            await expect(item).toHaveText(label);
            await expectText(editor, `Before ${label} after`);
            if (kind === 'link') {
                await expect(item).toHaveAttribute('href', 'https://example.com/guide');
            } else {
                await expect(item).toHaveAttribute('data-message-reference', payload);
                await expect(item).toHaveAttribute('contenteditable', 'false');
            }
            const length = (await editor.textContent())!.length;
            await selectText(editor, length, length);
            await page.keyboard.type('!');
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(`Before ${payload} after!`);
            await expect(postContent(page, saved.id!)).toContainText('Before');
            await expect(postContent(page, saved.id!)).toContainText('after!');
            const reopened = await reopen(page, saved);
            await expectText(reopened.editor, `Before ${label} after!`);
            await expect(reopened.editor.locator(kind === 'link' ? 'a' : '[data-message-reference]')).toHaveText(label);
        });
    }

    test('repeated overlapping mark toggles and partial removal roundtrip without nested duplicates or lost text', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('alpha beta gamma');
        await selectText(editor, 0, 16, 'alpha beta gamma');
        for (const mark of ['strong', 'em', 'strong', 'strong', 'em']) {
            await page.getByTestId(`message-format-${mark}`).click();
            await expectText(editor, 'alpha beta gamma');
            await expect.poll(() => editor.evaluate(() => window.getSelection()?.toString())).toBe('alpha beta gamma');
            await expect(editor.locator('strong strong, em em')).toHaveCount(0);
        }
        await expect(editor.locator('strong')).toHaveText('alpha beta gamma');
        await expect(editor.locator('em')).toHaveCount(0);
        await selectText(editor, 6, 10, 'beta');
        await page.getByTestId('message-format-strong').click();
        await expect.poll(async () => (await editor.locator('strong').allTextContents()).join('')).toBe('alpha  gamma');
        for (const mark of ['ins', 's', 's']) {
            await page.getByTestId(`message-format-${mark}`).click();
        }
        await expect(editor.locator('s, del')).toHaveCount(0);
        await expect(editor.locator('ins')).toHaveText('beta');
        await page.keyboard.insertText('BETA');
        await expectText(editor, 'alpha BETA gamma');
        await expectCaret(editor, 10);
        const saved = await send(page, composer);
        expect(saved.content).toBe('**alpha** <ins>BETA</ins> **gamma**');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('strong')).toHaveText(['alpha', 'gamma']);
        await expect(reopened.editor.locator('ins')).toHaveText('BETA');
        await expect(reopened.editor.locator('strong strong, em, s, del')).toHaveCount(0);
        await expectText(reopened.editor, 'alpha BETA gamma');
    });

    test('colon emoji keyboard acceptance replaces only the shortcode and does not submit the message', async ({ page, composer }) => {
        const { editor } = composer;
        const sentRequests: string[] = [];
        page.on('request', (request) => {
            if (request.method() === 'POST' && request.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`)) sentRequests.push(request.url());
        });
        await editor.pressSequentially('Nice :jo');
        const suggestions = page.getByRole('listbox');
        await expect(suggestions).toBeVisible();
        const selected = suggestions.getByRole('option', { selected: true });
        await expect(selected).toHaveCount(1);
        const selectedLabel = (await selected.textContent())!;
        const shortcode = /:([a-zA-Z0-9_+-]+):/.exec(selectedLabel);
        expect(shortcode).not.toBeNull();
        const glyph = selectedLabel.slice(0, selectedLabel.indexOf(':')).trim();
        expect(glyph.length).toBeGreaterThan(0);
        await page.keyboard.press('Enter');
        await expect(suggestions).toBeHidden();
        await expectText(editor, `Nice ${glyph} `);
        await expectCaret(editor, 6 + glyph.length);
        expect(sentRequests).toHaveLength(0);
        await page.keyboard.type('thanks');
        await expectText(editor, `Nice ${glyph} thanks`);
        const saved = await send(page, composer);
        expect(saved.content).toBe(`Nice ${glyph} thanks`);
        expect(saved.content).not.toContain(':jo');
        expect(sentRequests).toHaveLength(1);
        await expect(postContent(page, saved.id!)).toContainText(`Nice ${glyph} thanks`);
    });

    test('keyboard mention navigation scrolls the active option into view in both directions and wraps', async ({
        page,
        composer,
        login,
        courseManagementAPIRequests,
        userManagementAPIRequests,
    }) => {
        const users: string[] = [];
        const prefix = `mention-${generateUUID().slice(0, 8)}`;
        await login(admin);
        try {
            for (let index = 0; index < 10; index++) {
                const username = `${prefix}-${index}`;
                const response = await userManagementAPIRequests.createUser(username, username, UserRole.Student);
                expect(response.status()).toBe(201);
                users.push(username);
                await courseManagementAPIRequests.addStudentToCourse(composer.course, { username, password: username });
            }
            await login(studentOne, composer.route);
            const { editor } = composer;
            // Keep pointer hover from choosing a row while exercising keyboard-only navigation.
            await page.mouse.move(0, 0);
            await editor.pressSequentially(`@${prefix}`);
            const popup = page.getByTestId('message-completion-list');
            const options = popup.getByRole('option');
            await expect(options).toHaveCount(10);
            expect(await popup.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
            const documentScroll = await page.evaluate(() => ({ x: window.scrollX, y: window.scrollY }));
            const selected = popup.getByRole('option', { selected: true });
            const expectSelectionVisible = async (index: number) => {
                await expect(options.nth(index)).toHaveAttribute('aria-selected', 'true');
                await expect(selected).toHaveCount(1);
                await expect(editor).toHaveAttribute('aria-activedescendant', (await selected.getAttribute('id'))!);
                await expect
                    .poll(() =>
                        selected.evaluate((element) => {
                            const bounds = element.getBoundingClientRect();
                            const container = element.closest('[data-testid="message-completion-list"]')!.getBoundingClientRect();
                            return bounds.top >= container.top && bounds.bottom <= container.bottom;
                        }),
                    )
                    .toBe(true);
                await expect(editor).toBeFocused();
                expect(await page.evaluate(() => ({ x: window.scrollX, y: window.scrollY }))).toEqual(documentScroll);
            };
            await expectSelectionVisible(0);
            for (let index = 1; index < 10; index++) {
                await editor.press('ArrowDown');
                await expectSelectionVisible(index);
            }
            expect(await popup.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
            await editor.press('ArrowDown');
            await expectSelectionVisible(0);
            await editor.press('ArrowUp');
            await expectSelectionVisible(9);
            for (let index = 8; index >= 0; index--) {
                await editor.press('ArrowUp');
                await expectSelectionVisible(index);
            }
            const label = (await selected.locator('span').first().textContent())!;
            const selectedLogin = users.find((username) => label === `@${username} ${username}`);
            expect(selectedLogin).toBeDefined();
            await editor.press('Enter');
            await expect(popup).toBeHidden();
            await expect(editor.locator('[data-reference-type="user"]')).toHaveText(label);
            await expect(page.locator('[id^="item-"]')).toHaveCount(0);
            const saved = await send(page, composer);
            expect(saved.content).toBe(`[user]${label.slice(1)}(${selectedLogin})[/user]`);
            await expect(postContent(page, saved.id!)).toContainText(label.slice(1));
        } finally {
            await login(admin);
            for (const username of users) {
                expect((await userManagementAPIRequests.deleteUser(username)).ok()).toBe(true);
            }
        }
    });

    for (const kind of ['slides', 'faq'] as const) {
        for (const viewport of [
            { width: 1440, height: 1000 },
            { width: 390, height: 844 },
        ]) {
            test(`${kind} picker keeps overflowing keyboard selections and search visible at ${viewport.width}px`, async ({ page, composer }) => {
                await page.setViewportSize(viewport);
                await page.goto(composer.route);
                await expect(composer.editor).toBeVisible();
                await page.getByTestId('message-toolbar-toggle').click();
                await expect(page.getByTestId('message-advanced-toolbar')).toBeVisible();
                // These read-only resource fixtures isolate the layout contract; the composer/course are real.
                if (kind === 'slides') {
                    await page.route(`**/api/lecture/courses/${composer.course.id}/lectures-with-slides`, (route) =>
                        route.fulfill({
                            json: [
                                {
                                    id: 1001,
                                    title: 'Algorithms',
                                    course: composer.course,
                                    lectureUnits: [
                                        {
                                            id: 1002,
                                            name: 'Recurrence relations',
                                            type: 'attachment',
                                            visibleToStudents: true,
                                            attachment: {
                                                id: 1003,
                                                name: 'Recurrence relations',
                                                link: '/api/core/files/attachments/editor-layout/recurrences.pdf',
                                                studentVersion: '/api/core/files/attachments/editor-layout/recurrences_student.pdf',
                                                version: 1,
                                            },
                                            slides: Array.from({ length: 40 }, (_, index) => ({ id: 2001 + index, slideNumber: index + 1 })),
                                        },
                                    ],
                                },
                            ],
                        }),
                    );
                    await page.route('**/api/core/files/slides/*', (route) =>
                        route.fulfill({
                            contentType: 'image/svg+xml',
                            body: '<svg xmlns="http://www.w3.org/2000/svg" width="160" height="90"><text x="8" y="45">Recurrence relations</text></svg>',
                        }),
                    );
                } else {
                    await page.route(`**/api/communication/courses/${composer.course.id}/faq-state/ACCEPTED`, (route) =>
                        route.fulfill({
                            json: Array.from({ length: 40 }, (_, index) => ({
                                id: 3001 + index,
                                questionTitle: `Recurrence relations question ${index + 1}`,
                                questionAnswer: 'Use the recurrence covered in the lecture.',
                                faqState: 'ACCEPTED',
                                course: composer.course,
                                categories: [],
                            })),
                        }),
                    );
                }
                const { editor } = composer;
                await editor.pressSequentially('Reference: ');
                await page.getByTestId(`message-${kind === 'slides' ? 'slides' : 'faq'}-button`).click();
                await page.mouse.move(0, 0);
                const input = page.getByTestId('message-resource-search');
                await input.fill('recurrence');
                const content = page.getByTestId('message-resource-content');
                const options = content.getByRole('option');
                const count = kind === 'slides' ? 41 : 40;
                await expect(options).toHaveCount(count);
                expect(await content.evaluate((element) => element.scrollHeight > element.clientHeight)).toBe(true);
                const inputTop = (await input.boundingBox())!.y;
                const documentScroll = await page.evaluate(() => ({ x: window.scrollX, y: window.scrollY }));
                const expectActiveVisible = async (index: number) => {
                    const active = options.nth(index);
                    await expect(active).toHaveAttribute('aria-selected', 'true');
                    await expect(input).toHaveAttribute('aria-activedescendant', (await active.getAttribute('id'))!);
                    await expect(input).toBeFocused();
                    await expectInside(active, content, ['top', 'bottom'], { name: `active ${kind} result ${index}` });
                    await expect.poll(() => input.evaluate((element, top) => Math.abs(element.getBoundingClientRect().top - top), inputTop)).toBeLessThanOrEqual(1);
                    expect(await page.evaluate(() => ({ x: window.scrollX, y: window.scrollY }))).toEqual(documentScroll);
                };
                await expectActiveVisible(0);
                for (let index = 1; index < count; index++) {
                    await input.press('ArrowDown');
                    await expectActiveVisible(index);
                }
                expect(await content.evaluate((element) => element.scrollTop)).toBeGreaterThan(0);
                await input.press('ArrowDown');
                await expectActiveVisible(0);
                await input.press('ArrowUp');
                await expectActiveVisible(count - 1);
                for (let index = count - 2; index >= 0; index--) {
                    await input.press('ArrowUp');
                    await expectActiveVisible(index);
                }
                await input.press('ArrowUp');
                await expectActiveVisible(count - 1);
                if (kind === 'slides') {
                    const preview = page.getByTestId('message-slide-preview');
                    await expect(preview.locator('img')).toHaveAttribute('src', '/api/core/files/slides/2040');
                    await expectWithinViewport(preview, ['top', 'right', 'bottom', 'left'], { name: 'active slide preview' });
                }
                await input.press('Enter');
                await expect(content).toBeHidden();
                await expect(editor).toBeFocused();
                const expected =
                    kind === 'slides'
                        ? '[slide]Recurrence relations Slide 40(#2040)[/slide]'
                        : `[faq]Recurrence relations question 40(/courses/${composer.course.id}/faq?faqId=3040)[/faq]`;
                await expect(editor.locator('[data-message-reference]')).toHaveAttribute('data-message-reference', expected);
                await page.keyboard.type('reviewed');
                await expectText(editor, `Reference: Recurrence relations ${kind === 'slides' ? 'Slide' : 'question'} 40 reviewed`);
                await expect(page.locator('[id^="item-"]')).toHaveCount(0);
            });
        }
    }

    test('mention completions stay beside the caret on the first line of a multiline editor at every viewport', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('Context details');
        for (let line = 0; line < 7; line++) {
            await page.keyboard.press('Shift+Enter');
            await page.keyboard.type(`Later line ${line}`);
        }
        await forEachViewport(page, async () => {
            await editor.scrollIntoViewIfNeeded();
            await selectText(editor, 0, 0);
            await page.keyboard.type(`@${studentOne.username}`);
            const suggestions = page.getByTestId('message-completion-list');
            await expect(suggestions).toBeVisible();
            await expect(suggestions.getByRole('option')).not.toHaveCount(0);
            await expectWithinViewport(suggestions, ['top', 'right', 'bottom', 'left'], { name: 'mention completions' });
            await expect
                .poll(
                    () =>
                        suggestions.evaluate((popup) => {
                            const selection = window.getSelection();
                            if (!selection?.rangeCount) return Number.POSITIVE_INFINITY;
                            const caret = selection.getRangeAt(0).getBoundingClientRect();
                            const box = popup.getBoundingClientRect();
                            return Math.min(Math.abs(box.top - caret.bottom), Math.abs(caret.top - box.bottom));
                        }),
                    { message: 'completion popup should be within 16px above or below the active caret line' },
                )
                .toBeLessThanOrEqual(16);
            await page.keyboard.press('Escape');
            await expect(suggestions).toBeHidden();
            await selectText(editor, 0, 1 + studentOne.username.length, `@${studentOne.username}`);
            await page.keyboard.press('Backspace');
            await expectText(editor, 'Context detailsLater line 0Later line 1Later line 2Later line 3Later line 4Later line 5Later line 6');
        });
    });

    test('async mention results reposition the growing popup inside a narrow viewport', async ({
        page,
        composer,
        login,
        courseManagementAPIRequests,
        communicationAPIRequests,
    }) => {
        await login(admin);
        for (const student of [studentTwo, studentThree, studentFour]) {
            await courseManagementAPIRequests.addStudentToCourse(composer.course, student);
            await communicationAPIRequests.joinUserIntoChannel(composer.course, composer.channel.id!, student);
        }
        await login(studentOne, composer.route);
        await page.setViewportSize({ width: 390, height: 844 });
        const { editor } = composer;
        await expect(editor).toBeVisible();
        const query = 'artemis_test_user_';
        let releaseResponse!: () => void;
        const responseGate = new Promise<void>((resolve) => (releaseResponse = resolve));
        let responseReady!: () => void;
        const realResponseReady = new Promise<void>((resolve) => (responseReady = resolve));
        await page.route(
            (url) => url.pathname === `/api/course/courses/${composer.course.id}/members/search` && url.searchParams.get('loginOrName') === query,
            async (route) => {
                const response = await route.fetch();
                expect(response.ok()).toBe(true);
                responseReady();
                await responseGate;
                await route.fulfill({ response });
            },
        );
        try {
            await editor.pressSequentially('Formatting stays visible while you write.');
            await page.keyboard.press('Shift+Enter');
            await page.keyboard.type(`@${query}`);
            await realResponseReady;
            const suggestions = page.getByTestId('message-completion-list');
            await expect(suggestions).toBeVisible();
            await expect(suggestions.getByRole('option')).toHaveCount(0);
            releaseResponse();
            await expect(suggestions.getByRole('option')).toHaveCount(4);
            for (const student of [studentOne, studentTwo, studentThree, studentFour]) {
                await expect(suggestions.getByRole('option').getByText(`@${student.displayName}`, { exact: true })).toBeVisible();
            }
            await expectWithinViewport(suggestions, ['top', 'right', 'bottom', 'left'], { name: 'loaded mention completions at 390px' });
            await expect(suggestions.getByRole('option').last()).toBeInViewport({ ratio: 1 });
            await expectText(editor, `Formatting stays visible while you write.@${query}`);
        } finally {
            releaseResponse();
        }
    });

    test('an empty dirty draft keeps a neutral border after native blur while sending stays disabled', async ({ page, composer }) => {
        const { editor } = composer;
        const host = page.locator('jhi-posting-markdown-editor').filter({ has: editor });
        await editor.pressSequentially('x');
        await expect(page.getByTestId('send-formatted-message')).toBeEnabled();
        await editor.press('ControlOrMeta+a');
        await editor.press('Backspace');
        await expectText(editor, '');
        await editor.press('Tab');
        await expect(editor).not.toBeFocused();
        await expect(host).toHaveClass(/\bng-dirty\b/);
        await expect(host).toHaveClass(/\bng-invalid\b/);
        await expect(page.getByTestId('send-formatted-message')).toBeDisabled();
        await expect.poll(() => host.evaluate((element) => getComputedStyle(element).borderLeftWidth)).toBe('0px');
    });

    test('formatted draft reload keeps marks and selection editing sends the restored content once', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('draft text');
        await selectText(editor, 0, 5, 'draft');
        await page.getByTestId('message-format-strong').click();
        await expect(page.getByTestId('send-formatted-message')).toBeEnabled();
        await expect
            .poll(() => page.evaluate(() => Object.entries(localStorage).some(([key, value]) => key.includes('message_draft_') && value.includes('**draft** text'))))
            .toBe(true);
        await page.goto(composer.route);
        await expect(editor.locator('strong')).toHaveText('draft');
        await expectText(editor, 'draft text');
        await selectText(editor, 6, 10, 'text');
        await page.keyboard.insertText('restored');
        await expectText(editor, 'draft restored');
        await expectCaret(editor, 14);
        const saved = await send(page, composer);
        expect(saved.content).toBe('**draft** restored');
        await expect(postContent(page, saved.id!).locator('strong')).toHaveText('draft');
        await page.goto(composer.route);
        await expect(editor).toBeVisible();
        await expectText(editor, '');
        await expect(postContent(page, saved.id!)).toContainText('draft restored');
    });
});

test.describe('Compact formatted message toolbar', { tag: '@fast' }, () => {
    test.use({ expandedToolbar: false });

    test('shows basic controls by default and expands inline or wraps to fit each viewport', async ({ page, composer }) => {
        const host = page.locator('jhi-message-editor').filter({ has: composer.editor });
        const toggle = host.getByTestId('message-toolbar-toggle');
        const advanced = host.getByTestId('message-advanced-toolbar');
        await forEachViewport(page, async (viewport) => {
            await host.scrollIntoViewIfNeeded();
            await expect(toggle).toHaveAttribute('aria-expanded', 'false');
            await expect(toggle).toHaveAccessibleName('More options');
            await expect(advanced).toBeHidden();
            for (const id of ['message-format-strong', 'message-format-em', 'message-link-button', 'message-mention-button', 'message-reference-button']) {
                await expect(host.getByTestId(id)).toBeVisible();
                await expectWithinViewport(host.getByTestId(id), ['left', 'right'], { name: id });
            }
            await expect(host.getByTestId('message-format-ins')).toBeHidden();
            await expect(host.getByTestId('message-attachment-button')).toBeHidden();
            await toggle.click();
            await expect(toggle).toHaveAttribute('aria-expanded', 'true');
            await expect(toggle).toHaveAccessibleName('Fewer options');
            await expect(toggle).toHaveAttribute('aria-controls', (await advanced.getAttribute('id'))!);
            for (const id of [
                'message-format-ins',
                'message-format-s',
                'message-format-code',
                'message-block-ul',
                'message-block-ol',
                'message-block-blockquote',
                'message-block-pre',
                'message-attachment-button',
                'message-slides-button',
                'message-faq-button',
                'message-emoji-button',
            ]) {
                await expect(host.getByTestId(id)).toBeVisible();
                await expectWithinViewport(host.getByTestId(id), ['left', 'right'], { name: id });
            }
            if (viewport.width === 1728) {
                await expectAligned([host.getByTestId('message-format-strong'), ...(await advanced.getByRole('button').all())], 'top', {
                    name: 'all expanded formatting and resource controls share the basic toolbar row when space permits',
                });
            }
            if (viewport.width === 480) {
                await expectBelow([host.getByTestId('message-format-ins'), host.getByTestId('message-emoji-button')], toggle, {
                    name: 'expanded controls wrap below the basic toolbar on a narrow screen',
                });
            }
            await expectNoHorizontalOverflow(page, [host], { name: 'expanded message composer' });
            await expectNoHorizontalScrollAround(host, { name: 'expanded message composer' });
            await toggle.click();
            await expect(advanced).toBeHidden();
            await expectNoHorizontalOverflow(page, [host], { name: 'compact message composer' });
            await expectNoHorizontalScrollAround(host, { name: 'compact message composer' });
        });
    });

    test('preserves a native range and caret through toolbar toggles and formats the selected text', async ({ page, composer }) => {
        const { editor } = composer;
        const toggle = page.getByTestId('message-toolbar-toggle');
        await editor.pressSequentially('alpha beta gamma');
        await selectText(editor, 6, 10, 'beta');
        await toggle.click();
        await expect.poll(() => editor.evaluate(() => window.getSelection()?.toString())).toBe('beta');
        await page.getByTestId('message-format-ins').click();
        await expect(editor.locator('ins')).toHaveText('beta');
        await toggle.click();
        await expect.poll(() => editor.evaluate(() => window.getSelection()?.toString())).toBe('beta');
        await page.getByTestId('message-format-strong').click();
        await expect(editor.locator('strong ins, ins strong')).toHaveText('beta');
        await selectText(editor, 16, 16);
        await toggle.click();
        await expectCaret(editor, 16);
        await toggle.click();
        await expectCaret(editor, 16);
        await page.keyboard.type(' tail');
        expect((await send(page, composer)).content).toBe('alpha **<ins>beta</ins>** gamma tail');
    });

    test('supports repeated keyboard disclosure activation without moving focus', async ({ page, composer }) => {
        const toggle = page.getByTestId('message-toolbar-toggle');
        await composer.editor.pressSequentially('Draft text');
        await toggle.focus();
        await toggle.press('Space');
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        await expect(toggle).toBeFocused();
        await toggle.press('Space');
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
        await expect(toggle).toBeFocused();
        await toggle.press('Enter');
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        await expect(toggle).toBeFocused();
        await expectText(composer.editor, 'Draft text');
    });

    for (const eventType of ['paste', 'drop'] as const) {
        test(`shows pending and failed ${eventType} uploads while the advanced controls stay collapsed`, async ({ page, composer }) => {
            const { editor } = composer;
            const host = page.locator('jhi-message-editor').filter({ has: editor });
            const toggle = host.getByTestId('message-toolbar-toggle');
            const sendButton = page.getByTestId('send-formatted-message');
            let release!: () => void;
            const responseGate = new Promise<void>((resolve) => (release = resolve));
            let requested!: () => void;
            const uploadRequested = new Promise<void>((resolve) => (requested = resolve));
            await page.route(
                (url) => url.pathname === `/api/core/files/courses/${composer.course.id}/conversations/${composer.channel.id}`,
                async (route) => {
                    requested();
                    await responseGate;
                    await route.fulfill({ status: 503, contentType: 'application/json', body: '{}' });
                },
            );
            try {
                await editor.pressSequentially('Keep my draft');
                await expect(sendButton).toBeEnabled();
                await editor.evaluate((element, type) => {
                    const data = new DataTransfer();
                    data.items.add(new File(['attachment fixture'], 'missing.png', { type: 'image/png' }));
                    const event =
                        type === 'paste'
                            ? new ClipboardEvent('paste', { clipboardData: data, bubbles: true, cancelable: true })
                            : new DragEvent('drop', { dataTransfer: data, bubbles: true, cancelable: true });
                    // Firefox strips files from constructed ClipboardEvents; provide the synthetic clipboard payload explicitly.
                    if (type === 'paste') Object.defineProperty(event, 'clipboardData', { value: data });
                    element.dispatchEvent(event);
                }, eventType);
                await uploadRequested;
                await expect(host.getByRole('status')).toBeVisible();
                await expect(host.getByRole('status')).toHaveText('Uploading attachments…');
                await expect(toggle).toHaveAttribute('aria-expanded', 'false');
                await expect(host.getByTestId('message-attachment-button')).toBeHidden();
                await expect(host.getByTestId('message-format-ins')).toBeHidden();
                await expect(sendButton).toBeDisabled();
                release();
                await expect(host.getByRole('alert')).toBeVisible();
                await expect(host.getByRole('alert')).toContainText('Could not attach “missing.png”');
                await expect(host.getByRole('status')).toBeHidden();
                await expect(sendButton).toBeEnabled();
                await expect(toggle).toHaveAttribute('aria-expanded', 'false');
                await expect(host.getByTestId('message-attachment-button')).toBeHidden();
                await expectText(editor, 'Keep my draft');
                await expect(editor.locator('img')).toHaveCount(0);
            } finally {
                release();
            }
        });
    }

    test('keeps hidden formatting shortcuts available while advanced controls stay collapsed', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('underlined');
        await selectText(editor, 0, 10, 'underlined');
        await page.keyboard.press('ControlOrMeta+u');
        await expect(editor.locator('ins')).toHaveText('underlined');
        await expect(page.getByTestId('message-toolbar-toggle')).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByTestId('message-format-ins')).toBeHidden();
        expect((await send(page, composer)).content).toBe('<ins>underlined</ins>');
    });
});
