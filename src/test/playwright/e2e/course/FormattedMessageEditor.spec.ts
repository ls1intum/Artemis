import { expect, type Locator, type Page } from '@playwright/test';
import { test as artemisTest } from '../../support/fixtures';
import { admin, studentOne, studentTwo, studentThree, studentFour, UserRole } from '../../support/users';
import { generateUUID, readResponseJson } from '../../support/utils';
import {
    expectAligned,
    expectBelow,
    expectInside,
    expectNoHorizontalOverflow,
    expectNoHorizontalScrollAround,
    expectWithinViewport,
    forEachViewport,
    LAYOUT_VIEWPORTS,
} from '../../support/layout';
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
const test = artemisTest.extend<{ composer: Composer; expandedToolbar: boolean; mentionUsers: { users: string[]; prefix: string } }>({
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
    mentionUsers: async ({ composer, login, courseManagementAPIRequests, userManagementAPIRequests }, use) => {
        const users: string[] = [];
        const prefix = `mention-${generateUUID().slice(0, 8)}`;
        try {
            await login(admin);
            for (let index = 0; index < 10; index++) {
                const username = `${prefix}-${index}`;
                const response = await userManagementAPIRequests.createUser(username, username, UserRole.Student);
                expect(response.status()).toBe(201);
                users.push(username);
                await courseManagementAPIRequests.addStudentToCourse(composer.course, { username, password: username });
            }
            await use({ users, prefix });
        } finally {
            // Depending on composer keeps its course/page alive until every account cleanup finishes.
            // Fixture teardown uses Playwright's existing cleanup phase, rather than the keyboard test's body budget.
            if (users.length) {
                await login(admin);
                const results: PromiseSettledResult<void>[] = [];
                for (let offset = 0; offset < users.length; offset += 3) {
                    results.push(
                        ...(await Promise.allSettled(
                            users.slice(offset, offset + 3).map(async (username) => expect((await userManagementAPIRequests.deleteUser(username)).ok()).toBe(true)),
                        )),
                    );
                }
                for (const result of results) expect(result.status, result.status === 'rejected' ? String(result.reason) : undefined).toBe('fulfilled');
            }
        }
    },
});

/** Wait for CDK's asynchronous Handset observer to mount the composer for the current viewport. */
async function waitForResponsiveComposer(page: Page) {
    const handset = await page.evaluate(() => matchMedia('(max-width: 599.98px) and (orientation: portrait), (max-width: 959.98px) and (orientation: landscape)').matches);
    await expect(page.locator('#scrollableDiv').getByTestId('formatted-message-input')).toHaveCount(handset ? 1 : 0);
}

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

async function send(page: Page, composer: Composer, keyboard: boolean | string = false): Promise<Post> {
    const responsePromise = page.waitForResponse(
        (response) => response.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`) && response.request().method() === 'POST',
    );
    await expect(page.getByTestId('send-formatted-message')).toBeEnabled();
    if (typeof keyboard === 'string') {
        await composer.editor.press(keyboard);
    } else if (keyboard) {
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

/**
 * Reads the lines a user sees by where each character and mention is drawn, so the editor can be compared with the sent message.
 * innerText is not used: it reports a line break around a mention in a flex layout that is not visible. A mention's @ sign is an icon in the posting.
 */
async function visibleLines(content: Locator): Promise<string[]> {
    return content.evaluate((root) => {
        const pieces: { text: string; middle: number }[] = [];
        const walk = (node: Node): void => {
            if (node.nodeType === Node.TEXT_NODE) {
                const text = node.textContent ?? '';
                for (let index = 0; index < text.length; index++) {
                    const range = document.createRange();
                    range.setStart(node, index);
                    range.setEnd(node, index + 1);
                    const box = range.getClientRects()[0];
                    if (box?.height) pieces.push({ text: text[index], middle: box.top + box.height / 2 });
                }
            } else if (node instanceof Element) {
                if (node.matches('[data-message-reference], [data-posting-reference]')) {
                    const box = node.getBoundingClientRect();
                    pieces.push({ text: ` ${(node.textContent ?? '').replace(/^@/, '').trim()} `, middle: box.top + box.height / 2 });
                } else {
                    node.childNodes.forEach(walk);
                }
            }
        };
        walk(root);
        const lines: string[] = [];
        let previousMiddle = Number.NEGATIVE_INFINITY;
        for (const piece of pieces) {
            // Text and a mention on the same line differ by a few pixels at most, the next line is a full line height further down.
            if (piece.middle - previousMiddle > 10) lines.push('');
            lines[lines.length - 1] += piece.text;
            previousMiddle = piece.middle;
        }
        return lines.map((line) => line.replace(/[\u200b\s]+/g, ' ').trim()).filter((line) => line.length > 0);
    });
}

/** Types a mention of Student One and accepts the completion, as a user does. */
async function insertMention(page: Page) {
    await page.keyboard.type(`@${studentOne.username}`);
    const suggestions = page.getByTestId('message-completion-list');
    await expect(suggestions).toBeVisible();
    await expect(suggestions.getByRole('option', { selected: true })).toContainText(studentOne.displayName!);
    await page.keyboard.press('Enter');
    await expect(suggestions).toBeHidden();
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
    test('retains a live message when the initial HTTP page snapshot arrives later', async ({ page, composer, communicationAPIRequests }) => {
        let captureSnapshot!: () => void;
        let releaseSnapshot!: () => void;
        const captured = new Promise<void>((resolve) => (captureSnapshot = resolve));
        const released = new Promise<void>((resolve) => (releaseSnapshot = resolve));
        let pageRequests = 0;
        const matcher = `**/api/communication/courses/${composer.course.id}/messages?**`;
        await page.route(matcher, async (route) => {
            const url = new URL(route.request().url());
            if (url.searchParams.get('conversationIds') !== String(composer.channel.id) || url.searchParams.get('pagingEnabled') !== 'true') {
                await route.continue();
                return;
            }
            pageRequests++;
            const snapshot = await route.fetch();
            expect(snapshot.ok()).toBe(true);
            expect(await snapshot.json()).toEqual([]);
            captureSnapshot();
            await released;
            await route.fulfill({ response: snapshot });
        });
        try {
            await page.reload();
            await captured;
            await expect(composer.editor).toBeVisible();
            const saved = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', 'Live arrival survives an older snapshot');
            const posting = page.locator(`#item-${saved.id}`);
            await expect(posting).toBeVisible();
            const initialRequests = pageRequests;
            const response = page.waitForResponse((result) => {
                const url = new URL(result.url());
                return url.pathname === `/api/communication/courses/${composer.course.id}/messages` && url.searchParams.get('pagingEnabled') === 'true';
            });
            releaseSnapshot();
            await response;
            // Let the response and Angular's next paint settle before checking that the live row survives.
            await page.evaluate(() => new Promise<void>((resolve) => requestAnimationFrame(() => requestAnimationFrame(() => resolve()))));
            await expect(postContent(page, saved.id!)).toHaveText(saved.content!);
            expect(pageRequests).toBe(initialRequests);
            const reopened = await reopen(page, saved);
            await expectText(reopened.editor, saved.content!);
        } finally {
            releaseSnapshot();
            await page.unroute(matcher);
        }
    });

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

    for (const operation of ['copy', 'cut']) {
        test(`native ${operation}/paste retains mentions and image attachments through save and reopen`, async ({ page, composer, communicationAPIRequests }) => {
            const source = `**Read** [user]${studentOne.displayName}(${studentOne.username})[/user] ![copy.png](/api/core/files/attachments/copy.png)`;
            await page.route('**/copy.png*', (route) =>
                route.fulfill({
                    contentType: 'image/png',
                    body: Buffer.from('iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mP8/x8AAwMCAO+aP1sAAAAASUVORK5CYII=', 'base64'),
                }),
            );
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', source);
            const { posting, editor } = await reopen(page, original);
            await editor.press('ControlOrMeta+a');
            await editor.press(operation === 'copy' ? 'ControlOrMeta+c' : 'ControlOrMeta+x');
            if (operation === 'copy') await editor.press('Backspace');
            await expectText(editor, '');
            await editor.press('ControlOrMeta+v');
            await expect(editor.locator('[data-reference-type="user"]')).toContainText(studentOne.displayName!);
            await expect(editor.locator('img')).toHaveAttribute('src', '/api/core/files/attachments/copy.png');
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(source);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator('[data-reference-type="user"]')).toContainText(studentOne.displayName!);
            await expect(reopened.editor.locator('img')).toHaveAttribute('src', '/api/core/files/attachments/copy.png');
        });
    }

    for (const block of ['ul', 'ol', 'blockquote', 'pre']) {
        for (const shortcut of ['Enter', 'ControlOrMeta+Enter']) {
            test(`${shortcut} sends from ${block} while Shift+Enter continues editing without sending`, async ({ page, composer }) => {
                await composer.editor.pressSequentially('first');
                await page.getByTestId(`message-block-${block}`).click();
                await composer.editor.press('Shift+Enter');
                await composer.editor.pressSequentially('second');
                await expectText(composer.editor, 'firstsecond');
                if (block === 'ul' || block === 'ol') await expect(composer.editor.locator(`${block} > li`)).toHaveText(['first', 'second']);
                await expect(page.locator('[id^="item-"]')).toHaveCount(0);
                const saved = await send(page, composer, shortcut);
                expect(saved.content).toBe(
                    block === 'pre' ? '```\nfirst\nsecond\n```' : block === 'blockquote' ? '> first\n> second' : block === 'ul' ? '- first\n- second' : '1. first\n2. second',
                );
            });
        }
    }

    for (const list of ['ul', 'ol']) {
        test(`starts an empty ${list} from the toolbar and continues it with Shift+Enter`, async ({ page, composer }) => {
            const { editor } = composer;
            await editor.click();
            await page.getByTestId(`message-block-${list}`).click();
            await editor.pressSequentially('first');
            await editor.press('Shift+Enter');
            await editor.pressSequentially('second');
            await expect(editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
            await expect(page.locator('[id^="item-"]')).toHaveCount(0);
            const saved = await send(page, composer, 'Enter');
            expect(saved.content).toBe(list === 'ul' ? '- first\n- second' : '1. first\n2. second');
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
        });

        test(`Shift+Enter exits an empty ${list} item, supports undo and persists both items`, async ({ page, composer }) => {
            const { editor } = composer;
            await editor.pressSequentially('first');
            await page.getByTestId(`message-block-${list}`).click();
            await editor.press('Shift+Enter');
            await editor.pressSequentially('second');
            await editor.press('Shift+Enter');
            await expect(editor.locator('li')).toHaveCount(3);
            await editor.press('Shift+Enter');
            await expect(editor.locator('li')).toHaveText(['first', 'second']);
            await editor.press('ControlOrMeta+z');
            await expect(editor.locator('li')).toHaveCount(3);
            await editor.press('Shift+Enter');
            await editor.pressSequentially('after');
            const saved = await send(page, composer);
            expect(saved.content).toBe(list === 'ul' ? '- first\n- second\n\nafter' : '1. first\n2. second\n\nafter');
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
            await expect(reopened.editor.locator(':scope > p')).toHaveText('after');
        });
    }

    test('keeps an empty line typed with Shift+Enter in the sent message and when it is edited again', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('first');
        await editor.press('Shift+Enter');
        await editor.press('Shift+Enter');
        await editor.pressSequentially('second');
        const saved = await send(page, composer);
        expect(saved.content).toBe('first\n\nsecond');
        await expect(postContent(page, saved.id!).locator('p')).toHaveText(['first', 'second']);
        const { posting } = await reopen(page, saved);
        const edited = await saveEdited(page, composer, posting, saved);
        expect(edited.content).toBe('first\n\nsecond');
    });

    test('keeps each further empty line typed with Shift+Enter as a blank paragraph that survives editing', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('first');
        for (let emptyLine = 0; emptyLine < 3; emptyLine++) {
            await editor.press('Shift+Enter');
        }
        await editor.pressSequentially('second');
        const saved = await send(page, composer);
        expect(saved.content).toBe('first\n\n&nbsp;\n\nsecond');
        await expect(postContent(page, saved.id!).locator('p')).toHaveCount(3);
        const { posting } = await reopen(page, saved);
        const edited = await saveEdited(page, composer, posting, saved);
        expect(edited.content).toBe('first\n\n&nbsp;\n\nsecond');
    });

    test('keeps a single line break a line break and sends nothing for empty lines only', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('first');
        await editor.press('Shift+Enter');
        await editor.pressSequentially('second');
        const saved = await send(page, composer);
        expect(saved.content).toBe('first\nsecond');
        await expect(postContent(page, saved.id!).locator('p')).toHaveCount(1);

        for (let emptyLine = 0; emptyLine < 3; emptyLine++) {
            await editor.press('Shift+Enter');
        }
        await expect(page.getByTestId('send-formatted-message')).toBeDisabled();
    });

    for (const list of ['ul', 'ol']) {
        test(`Backspace at the start of the ${list} on the first line removes the list and keeps its text`, async ({ page, composer }) => {
            const { editor } = composer;
            await editor.click();
            await page.getByTestId(`message-block-${list}`).click();
            await editor.pressSequentially('first');
            await editor.press('Shift+Enter');
            await editor.pressSequentially('second');
            await expect(editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
            await selectText(editor, 0, 0);
            await editor.press('Backspace');
            // only the first item leaves the list, the other one stays in it
            await expect(editor.locator(`${list} > li`)).toHaveText(['second']);
            await expect(editor.locator(':scope > p').first()).toHaveText('first');
            const saved = await send(page, composer);
            // like with the list button, an ordered list keeps the number of the item that stays in it
            expect(saved.content).toBe(list === 'ul' ? 'first\n\n- second' : 'first\n\n2. second');
        });
    }

    for (const list of ['ul', 'ol']) {
        test(`repeated native ${list} item creation and two Backspaces retain the original content and height`, async ({ page, composer }) => {
            await composer.editor.pressSequentially('first');
            await page.getByTestId(`message-block-${list}`).click();
            const original = await composer.editor.evaluate((element) => ({ html: element.innerHTML, height: element.getBoundingClientRect().height }));
            for (let iteration = 0; iteration < 4; iteration++) {
                // Mobile paragraph input has no desktop Enter keydown; exercise the browser's native editing path.
                await composer.editor.evaluate(() => document.execCommand('insertParagraph'));
                await expect(composer.editor.locator('li')).toHaveCount(2);
                await composer.editor.press('Backspace');
                await composer.editor.press('Backspace');
                await expect(composer.editor.locator('li')).toHaveText(['first']);
                const content = await composer.editor.evaluate((element) => {
                    const copy = element.cloneNode(true) as HTMLElement;
                    // Firefox keeps one empty paragraph after merging the native list boundary. It neither grows
                    // nor changes the saved content; retain exact comparisons for every nonempty node and line break.
                    const emptyParagraphs = Array.from(copy.children).filter((child) => child.tagName === 'P' && child.innerHTML === '');
                    emptyParagraphs.forEach((paragraph) => paragraph.remove());
                    return { html: copy.innerHTML, emptyParagraphs: emptyParagraphs.length };
                });
                expect(content.html).toBe(original.html);
                expect(content.emptyParagraphs).toBeLessThanOrEqual(1);
                expect(await composer.editor.evaluate((element) => element.getBoundingClientRect().height)).toBe(original.height);
            }
            expect((await send(page, composer)).content).toBe(list === 'ul' ? '- first' : '1. first');
        });
    }

    for (const list of ['ul', 'ol']) {
        test(`native paragraph input continues ${list} items and exits an empty item without sending`, async ({ page, composer }) => {
            const { editor } = composer;
            const sentRequests: string[] = [];
            page.on('request', (request) => {
                if (request.method() === 'POST' && request.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`)) sentRequests.push(request.url());
            });
            await editor.pressSequentially('first');
            await page.getByTestId(`message-block-${list}`).click();
            await expect(editor.locator(`${list} > li`)).toHaveText(['first']);
            await editor.evaluate(() => document.execCommand('insertParagraph'));
            await page.keyboard.type('second');
            await expect(editor.locator(`${list} > li`)).toHaveText(['first', 'second']);
            await editor.evaluate(() => document.execCommand('insertParagraph'));
            await page.keyboard.press('Backspace');
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
            await editor.evaluate(() => document.execCommand('insertParagraph'));
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

    test('code Shift+Enter preserves literal lines when returning to a paragraph', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.pressSequentially('const answer = 42;');
        await page.getByTestId('message-block-pre').click();
        await page.keyboard.press('Shift+Enter');
        const emptyLine = await editor.locator('pre > code').evaluate((code) => ({
            height: code.getBoundingClientRect().height,
            lineHeight: Number.parseFloat(getComputedStyle(code).lineHeight),
        }));
        // The blank caret line must participate in layout before the user types the next character.
        expect(emptyLine.height).toBeGreaterThanOrEqual(2 * emptyLine.lineHeight - 1);
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

    for (const kind of ['pre', 'blockquote']) {
        test(`writes normal text before and after a ${kind} without losing its formatting`, async ({ page, composer }) => {
            const { editor } = composer;
            await editor.pressSequentially('inside');
            await page.getByTestId(`message-block-${kind}`).click();
            await selectText(editor, 0, 0);
            await editor.press('ArrowUp');
            await page.keyboard.type('Before');
            await selectText(editor, 12, 12);
            await editor.press('ArrowDown');
            await page.keyboard.type('After');
            await expect(editor.locator(kind)).toHaveText('inside');
            await expect(editor.locator(':scope > p')).toHaveText(['Before', 'After']);
            const saved = await send(page, composer);
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(kind)).toHaveText('inside');
            await expect(reopened.editor.locator(':scope > p')).toHaveText(['Before', 'After']);
        });
    }

    test('saves a bare hostname as an external HTTPS link', async ({ page, composer }) => {
        await composer.editor.pressSequentially('Guide');
        await selectText(composer.editor, 0, 5, 'Guide');
        await page.getByTestId('message-link-button').click();
        await page.getByTestId('message-link-url').fill('example.org/path');
        await page.getByTestId('apply-message-link').click();
        const saved = await send(page, composer);
        expect(saved.content).toBe('[Guide](https://example.org/path)');
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('a')).toHaveAttribute('href', 'https://example.org/path');
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
        await page.keyboard.press('Shift+Enter');
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

    for (const kind of ['mention', 'channel'] as const) {
        test(`rejects links over a protected ${kind} and renders an adjacent link correctly in the posting view`, async ({ page, composer, communicationAPIRequests }) => {
            const reference = kind === 'mention' ? '[user]Student One(artemis_test_user_1)[/user]' : `[channel]${composer.channel.name}(${composer.channel.id})[/channel]`;
            const label = kind === 'mention' ? '@Student One' : `#${composer.channel.name}`;
            const visible = `Hello ${label} today`;
            const original = await communicationAPIRequests.createCourseMessage(composer.course, composer.channel.id!, 'channel', `Hello ${reference} today`);
            const { posting, editor } = await reopen(page, original);
            await selectText(editor, 0, visible.length, visible);
            await expect(posting.getByTestId('message-link-button')).toBeDisabled();
            await page.keyboard.press('ControlOrMeta+k');
            await expect(posting.getByTestId('message-link-url')).toBeHidden();
            await expect(editor.locator('[data-message-reference]')).toHaveAttribute('data-message-reference', reference);
            await expect(editor.locator('a')).toHaveCount(0);

            await selectText(editor, 0, 5, 'Hello');
            await expect(posting.getByTestId('message-link-button')).toBeEnabled();
            await posting.getByTestId('message-link-button').click();
            await posting.getByTestId('message-link-url').fill('https://example.org/guide');
            await posting.getByTestId('apply-message-link').click();
            const saved = await saveEdited(page, composer, posting, original);
            expect(saved.content).toBe(`[Hello](https://example.org/guide) ${reference} today`);
            const rendered = postContent(page, saved.id!);
            await expect(rendered.getByRole('link', { name: 'Hello', exact: true })).toHaveAttribute('href', 'https://example.org/guide');
            await expect(rendered).toContainText(kind === 'mention' ? 'Student One' : composer.channel.name!);
            await expect(rendered).toContainText('today');
            await expect(rendered).not.toContainText('[Hello');
            await expect(rendered).not.toContainText(kind === 'mention' ? '[user]' : '[channel]');
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator('a')).toHaveText('Hello');
            await expect(reopened.editor.locator('a [data-message-reference]')).toHaveCount(0);
            await expect(reopened.editor.locator('[data-message-reference]')).toHaveAttribute('data-message-reference', reference);
        });
    }

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

    test('Enter sends the unchanged message after member completion finishes with no results', async ({ page, composer }) => {
        const text = 'ping @zzzznomember';
        await composer.editor.pressSequentially(text);
        const popup = page.getByTestId('message-completion-list');
        await expect(popup.getByRole('status')).toContainText('No results.');
        await expect(popup.getByRole('option')).toHaveCount(0);
        await expect(page.getByTestId('send-formatted-message')).toBeEnabled();
        const sent = page.waitForResponse(
            (response) => response.request().method() === 'POST' && response.url().endsWith(`/api/communication/courses/${composer.course.id}/messages`),
        );

        await composer.editor.press('Enter');

        const response = await sent;
        expect(response.status()).toBe(201);
        const saved = await readResponseJson<Post>(response);
        expect(saved.content).toBe(text);
        expect(response.request().postDataJSON().content).toBe(text);
        await expect(postContent(page, saved.id!)).toContainText(text);
        await expect(popup).toBeHidden();
        await expectText(composer.editor, '');
        await expect(page.getByTestId('send-formatted-message')).toBeDisabled();
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

    test('course-reference suggestions filter on every typed character and backspace', async ({ page, composer }) => {
        const overview = `**/api/communication/courses/${composer.course.id}/channels/public-overview`;
        await page.route(overview, (route) =>
            route.fulfill({ json: ['announcements', 'annex', 'analog', 'aside', 'alpha'].map((name, index) => ({ id: composer.channel.id! + index, name })) }),
        );
        try {
            await composer.editor.pressSequentially('#');
            const popup = page.getByTestId('message-completion-list');
            const announcement = popup.getByRole('option').filter({ hasText: '#announcements' });
            await expect(announcement).toBeVisible();
            await expect(popup.getByRole('option')).toHaveCount(4);
            for (const [index, character] of Array.from('anno').entries()) {
                await composer.editor.pressSequentially(character);
                // Every query has a different count, so stale rows cannot satisfy the next assertion.
                await expect(popup.getByRole('option')).toHaveCount([5, 3, 2, 1][index]);
                await expect(announcement).toBeVisible();
            }
            for (const count of [2, 3, 5, 4]) {
                await composer.editor.press('Backspace');
                await expect(popup.getByRole('option')).toHaveCount(count);
                await expect(announcement).toBeVisible();
            }
            await composer.editor.press('Escape');
            await expect(popup).toBeHidden();
        } finally {
            await page.unroute(overview);
        }
    });

    test('keyboard mention navigation scrolls the active option into view in both directions and wraps', async ({ page, composer, login, mentionUsers }) => {
        const { users, prefix } = mentionUsers;
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
            await waitForResponsiveComposer(page);
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

    for (const state of ['populated', 'loading']) {
        test(`Shift+Enter inserts a line break while mention suggestions are ${state}`, async ({ page, composer }) => {
            const query = studentOne.username;
            let release = () => {};
            if (state === 'loading') {
                const gate = new Promise<void>((resolve) => (release = resolve));
                await page.route(
                    (url) => url.pathname === `/api/course/courses/${composer.course.id}/members/search` && url.searchParams.get('loginOrName') === query,
                    async (route) => {
                        const response = await route.fetch();
                        await gate;
                        await route.fulfill({ response });
                    },
                );
            }
            try {
                await composer.editor.pressSequentially(`@${query}`);
                const suggestions = page.getByTestId('message-completion-list');
                await expect(suggestions).toBeVisible();
                if (state === 'populated') {
                    await expect(suggestions.getByRole('option')).not.toHaveCount(0);
                }
                await composer.editor.press('Shift+Enter');
                await expect(suggestions).toBeHidden();
                release();
                await composer.editor.pressSequentially('next');
                const saved = await send(page, composer);
                expect(saved.content).toBe(`@${query.replaceAll('_', '\\_')}\nnext`);
            } finally {
                release();
            }
        });
    }

    test('the sent message keeps the line breaks next to mentions exactly as shown in the editor', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.click();
        await insertMention(page);
        await page.keyboard.press('Shift+Enter');
        await page.keyboard.type('More text ');
        await insertMention(page);
        await page.keyboard.press('Shift+Enter');
        await page.keyboard.press('Shift+Enter');
        await insertMention(page);
        await page.keyboard.type('some text');
        await page.keyboard.press('Shift+Enter');
        await insertMention(page);
        const lines = ['Student One', 'More text Student One', 'Student One some text', 'Student One'];
        await expect.poll(() => visibleLines(editor)).toEqual(lines);

        const saved = await send(page, composer);

        const mention = `[user]${studentOne.displayName}(${studentOne.username})[/user]`;
        expect(saved.content).toBe(`${mention}\nMore text ${mention}\n\n${mention} some text\n${mention}`);
        await expect.poll(() => visibleLines(postContent(page, saved.id!))).toEqual(lines);
        const reopened = await reopen(page, saved);
        await expect.poll(() => visibleLines(reopened.editor)).toEqual(lines);
    });

    test('the sent message renders a bullet list with mentions without extra or empty bullets', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.click();
        await page.keyboard.type('- ');
        await expect(editor.locator('ul')).toHaveCount(1);
        await insertMention(page);
        await page.keyboard.press('Shift+Enter');
        await page.keyboard.type('More text ');
        await insertMention(page);
        await page.keyboard.press('Shift+Enter');
        await insertMention(page);
        await page.keyboard.type('some text');
        await page.keyboard.press('Shift+Enter');
        await insertMention(page);
        const items = ['@Student One', 'More text @Student One', '@Student One some text', '@Student One'];
        await expect(editor.locator('li')).toHaveText(items);

        const saved = await send(page, composer);

        const posted = postContent(page, saved.id!);
        await expect(posted.locator('li')).toHaveText(items.map((item) => item.replace('@Student', 'Student')));
        await expect(posted.locator('ul')).toHaveCount(1);
        const reopened = await reopen(page, saved);
        await expect(reopened.editor.locator('li')).toHaveText(items);
    });

    test('typing keeps a neutral message field while toolbar keyboard focus stays visible', async ({ page, composer, browserName }) => {
        const { editor } = composer;
        await editor.click();
        await editor.pressSequentially('Draft');
        await expect(editor).toBeFocused();
        await expectText(editor, 'Draft');
        await expect(editor).toHaveCSS('outline-style', 'none');
        await page.getByTestId('message-format-strong').focus();
        // Safari on macOS includes buttons in sequential keyboard navigation with Option+Tab.
        await page.keyboard.press(browserName === 'webkit' && process.platform === 'darwin' ? 'Alt+Tab' : 'Tab');
        const emphasis = page.getByTestId('message-format-em');
        await expect(emphasis).toBeFocused();
        await expect(emphasis).toHaveCSS('outline-style', 'solid');
        await expect.poll(() => emphasis.evaluate((element) => Number.parseFloat(getComputedStyle(element).outlineWidth))).toBeGreaterThan(0);
        await expectText(editor, 'Draft');
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

    test('keeps the toolbar on one line at every viewport and shows the controls that do not fit in a bar above the editor', async ({ page, composer }) => {
        const host = page.locator('jhi-message-editor').filter({ has: composer.editor });
        const toggle = host.getByTestId('message-toolbar-toggle');
        const advanced = host.getByTestId('message-advanced-toolbar');
        const line = host.getByTestId('message-toolbar-line');
        await forEachViewport(page, async (viewport) => {
            await waitForResponsiveComposer(page);
            await host.scrollIntoViewIfNeeded();
            await expect(toggle).toHaveAttribute('aria-expanded', 'false');
            await expect(toggle).toHaveAccessibleName('More options');
            await expect(advanced).toBeHidden();
            await expect(host.getByTestId('message-emoji-button')).toBeVisible();
            await expect(host.getByTestId('message-format-strong')).toBeVisible();
            await expect(host.getByTestId('message-attachment-button')).toBeHidden();
            // Every button of the line is next to the others: the emoji button never takes a line of its own.
            const lineButtons = await line.getByRole('button').all();
            expect(lineButtons.length).toBeGreaterThanOrEqual(3);
            await expectAligned(lineButtons, 'top', { name: `the toolbar is one line at ${viewport.width}px` });
            for (const button of lineButtons) {
                await expectWithinViewport(button, ['left', 'right'], { name: `toolbar button at ${viewport.width}px` });
            }
            const editorTop = (await composer.editor.boundingBox())!.y;
            const { y: toolbarTop, height: toolbarHeight } = (await host.getByTestId('message-editor-toolbar').boundingBox())!;

            await toggle.click();
            await expect(toggle).toHaveAttribute('aria-expanded', 'true');
            await expect(toggle).toHaveAccessibleName('Fewer options');
            await expect(toggle).toHaveAttribute('aria-controls', (await advanced.getAttribute('id'))!);
            // Wherever it sits, each control is reachable: on the line or in the bar, and none is wider than the screen.
            for (const id of [
                'message-format-strong',
                'message-format-em',
                'message-format-ins',
                'message-format-s',
                'message-format-code',
                'message-link-button',
                'message-mention-button',
                'message-reference-button',
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
            // The bar floats above the toolbar, so opening it neither moves the editor nor adds a line to the toolbar.
            // The buttons of the bar wrap one by one, not group by group, so it needs at most two rows at these widths.
            const barRows = new Set(await advanced.getByRole('button').evaluateAll((buttons) => buttons.map((button) => Math.round(button.getBoundingClientRect().top))));
            expect(barRows.size, `rows of the bar at ${viewport.width}px`).toBeLessThanOrEqual(2);
            const bar = (await advanced.boundingBox())!;
            expect(bar.y + bar.height, `the bar ends above the toolbar at ${viewport.width}px`).toBeLessThanOrEqual(toolbarTop + 1);
            expect((await composer.editor.boundingBox())!.y).toBe(editorTop);
            expect((await host.getByTestId('message-editor-toolbar').boundingBox())!.y).toBe(toolbarTop);
            expect((await host.getByTestId('message-editor-toolbar').boundingBox())!.height, `toolbar height with the bar open at ${viewport.width}px`).toBe(toolbarHeight);
            await expectNoHorizontalOverflow(page, [host], { name: 'expanded message composer' });
            await expectNoHorizontalScrollAround(host, { name: 'expanded message composer' });
            await toggle.click();
            await expect(advanced).toBeHidden();
            await expectNoHorizontalOverflow(page, [host], { name: 'compact message composer' });
            await expectNoHorizontalScrollAround(host, { name: 'compact message composer' });
        });
    });

    test('shortens the hint below the editor to at most two lines and keeps the full text in a tooltip', async ({ page, composer }) => {
        const host = page.locator('jhi-message-editor').filter({ has: composer.editor });
        const hint = host.getByTestId('message-editor-hint');
        await forEachViewport(page, async (viewport) => {
            await waitForResponsiveComposer(page);
            await host.scrollIntoViewIfNeeded();
            await expect(hint).toBeVisible();
            await expect(hint).not.toContainText('Ctrl+Enter');
            await expect(hint).toContainText('Enter sends');
            const { height, lineHeight } = await hint.evaluate((element) => ({
                height:
                    element.getBoundingClientRect().height - Number.parseFloat(getComputedStyle(element).paddingTop) - Number.parseFloat(getComputedStyle(element).paddingBottom),
                lineHeight: Number.parseFloat(getComputedStyle(element).lineHeight),
            }));
            expect(Math.round(height / lineHeight), `hint lines at ${viewport.width}px`).toBeLessThanOrEqual(2);
            await expectNoHorizontalOverflow(page, [host], { name: 'message editor hint' });
        });
        await host.getByTestId('message-editor-help').hover();
        await expect(page.getByRole('tooltip')).toContainText('Shift+Enter adds a new line or the next list item');
        await expect(page.getByRole('tooltip')).not.toContainText('Ctrl+Enter');
    });

    test('names the conversation in light grey while the editor is empty', async ({ page, composer }) => {
        const placeholder = page.getByTestId('message-editor-placeholder');
        await expect(placeholder).toHaveText(`Message ${composer.channel.name}`);
        await composer.editor.pressSequentially('Hi');
        await expect(placeholder).toBeHidden();
        await composer.editor.press('ControlOrMeta+a');
        await composer.editor.press('Backspace');
        await expect(placeholder).toBeVisible();
        // The hint is not a part of the message and does not take the pointer from the editor.
        await expectText(composer.editor, '');
        await placeholder.click({ force: true });
        await expect(composer.editor).toBeFocused();
    });

    test('keeps the toolbar toggle clickable beneath a hoverable quote tooltip', async ({ page, composer }) => {
        await page.setViewportSize({ width: 1024, height: 768 });
        const toggle = page.getByTestId('message-toolbar-toggle');
        await toggle.click();
        await page.getByTestId('message-block-blockquote').hover();
        await expect(page.getByRole('tooltip', { name: 'Quote', exact: true })).toBeVisible();

        await toggle.click();

        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByTestId('message-advanced-toolbar')).toBeHidden();
        await expect(composer.editor).toBeEditable();
    });

    test('reveals block formatting on a text selection while emoji stays available in compact mode', async ({ page, composer }) => {
        const toggle = page.getByTestId('message-toolbar-toggle');
        await expect(page.getByTestId('message-emoji-button')).toBeVisible();
        await composer.editor.pressSequentially('Selected words');
        await selectText(composer.editor, 0, 8, 'Selected');
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        await expect(page.getByTestId('message-block-ul')).toBeVisible();
        await composer.editor.press('ArrowRight');
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByTestId('message-advanced-toolbar')).toBeHidden();
        await selectText(composer.editor, 0, 8, 'Selected');
        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
        await toggle.click();
        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
        await page.getByTestId('message-format-ins').click();
        await expect(composer.editor.locator('ins')).toHaveText('Selected');
    });

    for (const marker of ['-', '*', '+', '1.', '1)']) {
        test(`converts a typed ${marker} marker to a list and preserves it through send and reopen`, async ({ page, composer }) => {
            await composer.editor.pressSequentially(`${marker} Item`);
            const list = marker.startsWith('1') ? 'ol' : 'ul';
            await expect(composer.editor.locator(`${list} > li`)).toHaveText('Item');
            await composer.editor.press('Shift+Enter');
            await composer.editor.pressSequentially('Next');
            await expect(composer.editor.locator(`${list} > li`)).toHaveText(['Item', 'Next']);
            const saved = await send(page, composer);
            expect(saved.content).toBe(marker.startsWith('1') ? '1. Item\n2. Next' : '- Item\n- Next');
            const reopened = await reopen(page, saved);
            await expect(reopened.editor.locator(`${list} > li`)).toHaveText(['Item', 'Next']);
        });
    }

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

    // Each viewport owns a fresh composer: the responsive conversation layout replaces it at breakpoints.
    for (const viewport of LAYOUT_VIEWPORTS) {
        test.describe(`Upload feedback at ${viewport.width}px`, () => {
            test.use({ viewport });
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
                            data.items.add(new File(['attachment fixture'], 'missing-attachment-with-a-long-name-that-must-wrap-within-the-editor.png', { type: 'image/png' }));
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
                        await expect(sendButton).toBeDisabled();
                        release();
                        await expect(host.getByRole('alert')).toBeVisible();
                        await expect(host.getByRole('alert')).toContainText('Could not attach “missing-attachment-with-a-long-name-that-must-wrap-within-the-editor.png”');
                        await expect(host.getByRole('status')).toBeHidden();
                        await expect(sendButton).toBeEnabled();
                        await expect(toggle).toHaveAttribute('aria-expanded', 'false');
                        await expect(host.getByTestId('message-attachment-button')).toBeHidden();
                        await expectText(editor, 'Keep my draft');
                        await expect(editor.locator('img')).toHaveCount(0);
                        await toggle.click();
                        await expect(toggle).toHaveAttribute('aria-expanded', 'true');
                        const alert = host.getByTestId('message-upload-error');
                        await expectBelow([alert], host.getByTestId('message-attachment-button'), { name: `upload error below controls at ${viewport.width}px` });
                        await expectBelow([alert], toggle, { name: `upload error below basic toolbar at ${viewport.width}px` });
                        await expectInside(alert, host.getByTestId('message-editor-toolbar'), ['left', 'right', 'top', 'bottom'], { name: 'wrapped upload error' });
                        await expectNoHorizontalOverflow(page, [host], { name: 'upload failure' });
                    } finally {
                        release();
                    }
                });
            }
        });
    }

    test('keeps formatting shortcuts available in compact mode', async ({ page, composer }) => {
        const { editor } = composer;
        await editor.focus();
        await page.keyboard.press('ControlOrMeta+u');
        await editor.pressSequentially('underlined');
        await expect(editor.locator('ins')).toHaveText('underlined');
        await expect(page.getByTestId('message-toolbar-toggle')).toHaveAttribute('aria-expanded', 'false');
        await expect(page.getByTestId('message-format-ins')).toBeVisible();
        expect((await send(page, composer)).content).toBe('<ins>underlined</ins>');
    });
});
