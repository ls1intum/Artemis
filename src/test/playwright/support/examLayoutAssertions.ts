import { Locator, Page, expect } from '@playwright/test';
import {
    LayoutViewport,
    expectAligned,
    expectBelow,
    expectComputedStyle,
    expectFillsParent,
    expectGap,
    expectHeight,
    expectInset,
    expectInside,
    expectInsideOrBelow,
    expectNoHorizontalOverflow,
    expectNoHorizontalScrollAround,
    expectNoOverlap,
    expectSameComputedStyle,
    expectWithinViewport,
    measure,
} from './layout';

/** The height of the title row, its rule included, that every page of the student exam starts with. It is the same on every page, so the pages do not jump when the student switches. */
const TITLE_ROW_HEIGHT = 40;

/** The height of the exam bar above the sidebar and the content. */
export const EXAM_BAR_HEIGHT = 40;

/** The height of a small TUM AET UI button outside a title row: the start and hand-in buttons and the back button of the summary. */
export const SMALL_BUTTON_HEIGHT = 34;

/**
 * The height of a button in a title row: the save button, the export button of the summary, the actions of the programming page and the hand-in
 * early button of the exam bar, which is 40px high like a title row. 30px leaves 4.5px of air above and below it in the 40px row, where a small
 * button of 34px would sit 3px from the rule below the title.
 */
export const TITLE_ROW_BUTTON_HEIGHT = 30;

/** The air a button in a title row keeps above it and below it, inside the row, in px. The row is 40px and the button 30px, so 4.5px are there; the rest is margin for rounding. */
const TITLE_ROW_BUTTON_AIR = 4;

/**
 * The distance in px between a card of the exam and what is in it, on every side: the title row of a page to the left, top and right edge of
 * the card, the end of the page to its bottom edge, and in the exam bar the title and the hand-in early button to the left and right edge.
 * The card does not waste the room around the content.
 */
const CARD_INSET = 12;

/** The width in px of the divider between two panels, the same everywhere in the application: `--spacing-divider` of the shell. */
export const DIVIDER_WIDTH = 6;

/** From this width in px the Code button, the result, Refresh and Submit of the programming page sit in its title row: the title keeps its room next to them. */
const PROGRAMMING_ACTIONS_IN_ROW_MIN_WIDTH = 1280;

/** Below this width in px the programming actions do not fit next to the title any more and stay below the row, in the bar of the editor. */
const PROGRAMMING_ACTIONS_BELOW_MAX_WIDTH = 480;

/** The width in px the title of a page keeps at least next to the programming actions (the product keeps 256px, a name that is cut off there is still readable). */
const MIN_TITLE_WIDTH_NEXT_TO_ACTIONS = 200;

/** From this width in px the panel of the file upload page has room for the file input and the Upload button in one row; below it the button wraps below the input. */
const FILE_UPLOAD_ROW_MIN_WIDTH = 1280;

/** The distance in px between the file input and the Upload button when they are in one row. */
const FILE_UPLOAD_GAP = 8;

/** The rule below the title row. The row draws it itself, inside its 40px, so a row without it is 40px high all the same. */
const TITLE_RULE_WIDTH = '1px';

const TITLE_FONT_SIZE = '18px';
const TITLE_FONT_WEIGHT = '600';

/**
 * The width from which the welcome page and the summary keep their sideways scroll contract, and the summary its button. Both sit
 * directly in the course shell, which keeps its sidebars on a phone: at 480px they are a 200px column, the name field of the welcome
 * page (40 characters wide) reaches beyond it, and the label of the back button of the summary wraps onto a second line. That is the
 * layout of the shell, not of the pages, so the phone width is not part of this contract. The wide tables of the summary scroll inside
 * wrappers of their own.
 */
export const COURSE_SHELL_MIN_WIDTH = 800;

/** The width from which the complaint of the student and the response to it are set side by side. Below it they are stacked. */
const COMPLAINT_COLUMNS_MIN_WIDTH = 1200;

/** A box that is not drawn: the computed background, or the border colour, of an element that has none. */
const TRANSPARENT = 'rgba(0, 0, 0, 0)';

/** The border around a complaint box. */
const COMPLAINT_BOX_BORDER_WIDTH = '1px';

/** The title row of the page the student has open. The exam keeps the pages it has shown in the document, hidden, each with a title row of its own. */
export function visibleTitleRow(page: Page): Locator {
    return page.getByTestId('exam-exercise-header').filter({ visible: true });
}

/**
 * Expects the page the student has open not to scroll sideways: neither the document nor a container it sits in. The pages of the exam
 * live in cards that scroll on their own, so a page that is too wide scrolls its card sideways while the document keeps the width of
 * the window.
 * @param titleRow the title row of the page, which sits in the same containers as the rest of the page
 */
export async function expectPageNotToScrollSideways(page: Page, titleRow: Locator): Promise<void> {
    await expectNoHorizontalOverflow(page);
    await expectNoHorizontalScrollAround(titleRow, { name: 'the title row' });
}

/**
 * Expects a title row of the exam to be 40px high with the rule below it, and its title to be set in the one size and weight every title of the exam has.
 * The title is a heading, which the global heading style would enlarge unless the utilities carry the important modifier.
 * @param name what the row belongs to, e.g. `the quiz page`
 */
export async function expectTitleRow(row: Locator, title: Locator, name: string): Promise<void> {
    await expectHeight(row, TITLE_ROW_HEIGHT, { name: `title row of ${name}` });
    await expectComputedStyle(row, 'border-bottom-width', TITLE_RULE_WIDTH, { name: `rule below the title row of ${name}` });
    await expectComputedStyle(title, 'font-size', TITLE_FONT_SIZE, { name: `title of ${name}` });
    await expectComputedStyle(title, 'font-weight', TITLE_FONT_WEIGHT, { name: `title of ${name}` });
}

/**
 * Expects a button in a title row to be 30px high and to keep air above and below it inside the row. The row is 40px high, so a button
 * of the small size (34px) would sit about 3px from the rule below the title.
 * @param name what the button is, e.g. `save button of the quiz page`
 */
export async function expectTitleRowButton(button: Locator, row: Locator, name: string): Promise<void> {
    await expectHeight(button, TITLE_ROW_BUTTON_HEIGHT, { name });
    await expectInside(button, row, ['top', 'bottom'], { margin: TITLE_ROW_BUTTON_AIR, name: `${name} in its title row` });
}

/**
 * Expects the title of a page to start 12px from the left edge of the card that holds the page, and its title row to be 12px from the top and
 * the right edge of it. The exam bar has the same inset (see {@link expectExamBarInsets}), so the titles of the bar and of every page are set
 * the same distance inside their card, and the card does not waste the room around the content.
 * @param scrollers the scroll containers the row sits in up to the card, whose scrollbars take room at the right edge (see {@link expectInset})
 */
export async function expectTitleInCard(row: Locator, title: Locator, card: Locator, name: string, scrollers: readonly Locator[] = []): Promise<void> {
    await expectInset(title, card, 'left', CARD_INSET, { name: `title of ${name}` });
    await expectInset(row, card, 'top', CARD_INSET, { name: `title row of ${name}` });
    await expectInset(row, card, 'right', CARD_INSET, { name: `title row of ${name}`, scrollers });
}

/**
 * Expects the page of the running exam the student has open to keep 12px to the card on all four sides: its title row to the left, top and
 * right edge (see {@link expectTitleInCard}), and the scroller that holds the page to keep 12px below it. The bottom edge is the padding of the
 * scroller and not the end of what is inside it, because the content of a page is as high as the page wants and may reach beyond the column
 * of a small window, where the scroller then ends flush with it.
 * @param name what the page is, e.g. `the quiz page`
 */
export async function expectExamContentInsets(page: Page, row: Locator, title: Locator, name: string): Promise<void> {
    const scroller = page.getByTestId('exam-content-scroller');
    // The scroller is shared by all pages and keeps its position when the student switches, so the top of the new page is not necessarily in view.
    await scroller.evaluate((element) => (element.scrollTop = 0));
    await expectTitleInCard(row, title, page.getByTestId('exam-content'), name, [scroller]);
    await expectComputedStyle(scroller, 'padding-bottom', `${CARD_INSET}px`, { name: `the space below ${name}` });
}

/**
 * Expects the welcome page to keep 12px to the edge of its cover: the title row to the left, top and right edge, and the space below the last
 * line, which is the padding of the cover because the page ends where its content ends.
 */
export async function expectStartViewInsets(page: Page): Promise<void> {
    const cover = page.getByTestId('exam-cover');
    const header = page.getByTestId('exam-start-header');
    for (const edge of ['left', 'top', 'right'] as const) {
        await expectInset(header, cover, edge, CARD_INSET, { name: 'title row of the welcome page', scrollers: [cover] });
    }
    await expectComputedStyle(cover, 'padding-bottom', `${CARD_INSET}px`, { name: 'the space below the welcome page' });
}

/**
 * Expects the hand-in page to keep 12px to the edge of its card, which is as wide as the exam bar above it: the title row to the left, top and
 * right edge, and the space below the last line, which is the padding of the cover inside the card.
 */
export async function expectHandInInsets(page: Page): Promise<void> {
    const card = page.getByTestId('exam-end-view');
    const header = page.getByTestId('exam-finished-header');
    const scrollers = [card, page.getByTestId('exam-cover')];
    for (const edge of ['left', 'top', 'right'] as const) {
        await expectInset(header, card, edge, CARD_INSET, { name: 'title row of the hand-in page', scrollers });
    }
    await expectComputedStyle(page.getByTestId('exam-cover'), 'padding-bottom', `${CARD_INSET}px`, { name: 'the space below the hand-in page' });
}

/**
 * Expects the exam bar to keep 12px to its edges: the title to the left edge, like the titles of the pages in their card, and the hand-in early
 * button to the right edge, where the label is hidden below the `sm` breakpoint but the button keeps its place. The button is a button of
 * a 40px row, so it is 30px high with air above and below it (see {@link expectTitleRowButton}).
 */
export async function expectExamBarInsets(page: Page): Promise<void> {
    const bar = page.getByTestId('exam-bar');
    const handInEarly = page.getByTestId('hand-in-early');
    await expectInset(page.getByTestId('exam-bar-title'), bar, 'left', CARD_INSET, { name: 'title of the exam bar' });
    await expectInset(handInEarly, bar, 'right', CARD_INSET, { name: 'hand-in early button of the exam bar' });
    await expectTitleRowButton(handInEarly, bar, 'hand-in early button of the exam bar');
}

/**
 * Expects the Code button, the result, Refresh and Submit of the programming page to sit where the room allows. From 1280px they are in the
 * title row, beside the title and not over it, and the row is not larger for them. At a phone width they are below the row, in the bar of the
 * editor. In between they are in the row where the title keeps its room, and otherwise below it, but never half in. The title row
 * itself is checked like that of every other page by the caller.
 * @param row the title row of the programming page
 */
export async function expectProgrammingActions(page: Page, row: Locator, viewport: LayoutViewport): Promise<void> {
    const buttons = [page.getByTestId('exam-programming-code').getByRole('button'), page.locator('#refresh_button'), page.locator('#submit_button')];
    const controls = [...buttons, page.getByTestId('exam-programming-result')];
    const options = { name: 'the Code button, the result, Refresh and Submit of the programming page' };
    const title = row.getByTestId('exam-exercise-title');

    let place: 'inside' | 'below';
    if (viewport.width >= PROGRAMMING_ACTIONS_IN_ROW_MIN_WIDTH) {
        for (const control of controls) {
            await expectInside(control, row, ['top', 'right', 'bottom', 'left'], { name: `${String(control)} of the programming page in its title row` });
        }
        place = 'inside';
    } else if (viewport.width <= PROGRAMMING_ACTIONS_BELOW_MAX_WIDTH) {
        await expectBelow(controls, row, options);
        place = 'below';
    } else {
        place = await expectInsideOrBelow(controls, row, options);
    }

    if (place === 'inside') {
        for (const button of buttons) {
            await expectTitleRowButton(button, row, `${String(button)} of the programming page`);
        }
        await expectNoOverlap([title, page.getByTestId('programming-toolbar')], { name: 'the title and the actions of the programming page' });
        await expect
            .poll(async () => (await measure(title)).width, {
                message: `the title of the programming page at ${viewport.width}x${viewport.height} is squeezed by the actions next to it`,
            })
            .toBeGreaterThanOrEqual(MIN_TITLE_WIDTH_NEXT_TO_ACTIONS);
    }
}

/**
 * Expects the file input of the file upload page and its Upload button to belong together: both are as high as a small button, and where the
 * panel has room (from 1280px) the button follows the input in the same row, 8px from its end and at the same vertical centre, instead of
 * floating at the far end of the panel; where it has not, the button is below the input and starts at the same edge.
 */
export async function expectFileUploadRow(page: Page, viewport: LayoutViewport): Promise<void> {
    const field = page.getByTestId('file-upload-field').filter({ visible: true });
    const upload = page.getByTestId('file-upload-submit').filter({ visible: true });
    await expectHeight(field, SMALL_BUTTON_HEIGHT, { name: 'the file input' });
    await expectHeight(upload, SMALL_BUTTON_HEIGHT, { name: 'the Upload button' });
    if (viewport.width >= FILE_UPLOAD_ROW_MIN_WIDTH) {
        // both are 34px high, so the same top is the same vertical centre
        await expectAligned([field, upload], 'top', { name: 'the file input and the Upload button' });
        await expectGap(field, upload, 'horizontal', FILE_UPLOAD_GAP, { name: 'the file input and the Upload button' });
    } else {
        await expectBelow([upload], field, { name: 'the Upload button' });
        await expectAligned([field, upload], 'left', { name: 'the file input and the Upload button' });
    }
}

/** The panel of the solution and the panel of the problem statement of the text, modeling and file upload page the student has open. */
function problemStatementPanels(page: Page): { left: Locator; right: Locator; collapsed: Locator } {
    return {
        left: page.getByTestId('resizeable-container-left').filter({ visible: true }),
        right: page.getByTestId('resizeable-container-right').filter({ visible: true }),
        collapsed: page.getByTestId('resizeable-container-collapsed').filter({ visible: true }),
    };
}

/**
 * Expects the divider between the solution and the problem statement of a text, modeling or file upload page to be 6px wide, the same as the
 * divider of every other page of the application, and not a handle of 30 to 40px with a grip icon. Unlike the course exercise page, which has
 * a panel layout of its own, these pages use the shared resizeable container, so a gap that grows again is a regression of that component.
 * @param name what the page is, e.g. `the text page`
 */
export async function expectProblemStatementDivider(page: Page, name: string): Promise<void> {
    const { left, right } = problemStatementPanels(page);
    await expectGap(left, right, 'horizontal', DIVIDER_WIDTH, { name: `the divider between the solution and the problem statement of ${name}` });
}

/**
 * Expects the problem statement to keep the divider when the student collapses it and expands it again: the collapsed tab is 6px from the panel
 * of the solution as well. Leaves the problem statement expanded.
 * @param name what the page is, e.g. `the text page`
 */
export async function expectProblemStatementDividerWhileCollapsed(page: Page, name: string): Promise<void> {
    const { left, right, collapsed } = problemStatementPanels(page);
    // the header of the problem statement is the first button in its panel, and a click on it collapses the panel
    await right.getByRole('button').first().click();
    await expectGap(left, collapsed, 'horizontal', DIVIDER_WIDTH, { name: `the collapsed problem statement of ${name}` });
    await collapsed.click();
    await expectGap(left, right, 'horizontal', DIVIDER_WIDTH, { name: `the divider of ${name} after the problem statement was expanded again` });
}

/**
 * Drags a divider with the mouse by `drag` px and expects the panel it resizes to change its width by `change` px, so that a divider that
 * got thinner is still one the student can take hold of. Drags back afterwards, so that the page keeps its size.
 * @param divider the handle between two panels
 * @param panel the panel that is resized by it
 */
export async function expectDividerResizesPanel(page: Page, divider: Locator, panel: Locator, drag: number, change: number, name: string): Promise<void> {
    const before = (await measure(panel)).width;
    const dragDivider = async (by: number) => {
        const handle = await measure(divider);
        const x = (handle.left + handle.right) / 2;
        const y = (handle.top + handle.bottom) / 2;
        await page.mouse.move(x, y);
        await page.mouse.down();
        await page.mouse.move(x + by, y, { steps: 8 });
        await page.mouse.up();
    };
    await dragDivider(drag);
    await expect
        .poll(async () => Math.abs((await measure(panel)).width - (before + change)), {
            message: `${name}: dragging the divider by ${drag}px should change the width of the panel from ${before}px by ${change}px`,
        })
        .toBeLessThanOrEqual(1);
    await dragDivider(-drag);
    await expect
        .poll(async () => Math.abs((await measure(panel)).width - before), { message: `${name}: dragging the divider back should restore the width of the panel (${before}px)` })
        .toBeLessThanOrEqual(1);
}

/**
 * Expects the panel of the problem statement to be resized by its divider: dragging 100px to the left makes it 100px wider.
 * @param name what the page is, e.g. `the text page`
 */
export async function expectProblemStatementDividerResizes(page: Page, name: string): Promise<void> {
    await expectDividerResizesPanel(
        page,
        page.getByTestId('resizeable-container-divider').filter({ visible: true }),
        problemStatementPanels(page).right,
        -100,
        100,
        `the divider of ${name}`,
    );
}

/**
 * Expects the panels of the online code editor on the programming page to be set apart by the 6px divider: the file browser, the editor and
 * the instructions side by side, and the build output below all three. Each divider is as thin as the one between the solution and the
 * problem statement of the other pages, where it used to be 20px wide and high.
 */
export async function expectProgrammingPanelDividers(page: Page): Promise<void> {
    const files = page.getByTestId('cardFiles').filter({ visible: true });
    const editor = page.getByTestId('cardEditor').filter({ visible: true });
    const instructions = page.getByTestId('cardInstructions').filter({ visible: true });
    const buildOutput = page.getByTestId('cardBuildOutput').filter({ visible: true });
    await expectGap(files, editor, 'horizontal', DIVIDER_WIDTH, { name: 'the divider between the file browser and the editor' });
    await expectGap(editor, instructions, 'horizontal', DIVIDER_WIDTH, { name: 'the divider between the editor and the instructions' });
    for (const [panel, name] of [
        [files, 'the file browser'],
        [editor, 'the editor'],
        [instructions, 'the instructions'],
    ] as const) {
        await expectGap(panel, buildOutput, 'vertical', DIVIDER_WIDTH, { name: `the divider between ${name} and the build output` });
    }
}

/** Expects the file browser of the online code editor to be resized by its divider: dragging 60px to the right makes it 60px wider. */
export async function expectFileBrowserDividerResizes(page: Page): Promise<void> {
    await expectDividerResizesPanel(
        page,
        page.getByTestId('draggableIconForFileBrowser'),
        page.getByTestId('cardFiles').filter({ visible: true }),
        60,
        60,
        'the divider of the file browser',
    );
}

/**
 * Expects the exam summary the student has open to keep its layout at the current viewport: the title row and the buttons have their
 * sizes, the section headings look alike, the scroller fills the page that holds it and ends inside the window, and the page does not
 * scroll sideways.
 */
export async function expectExamSummaryLayout(page: Page, viewport: LayoutViewport): Promise<void> {
    const row = visibleTitleRow(page);
    await expectTitleRow(row, row.getByTestId('exam-exercise-title'), 'the summary');
    await expectTitleRowButton(row.getByTestId('exam-summary-export-button'), row, 'export button of the summary');
    await expectSameComputedStyle(page.getByTestId('exam-summary-heading'), ['font-size', 'font-weight'], { name: 'the section headings of the summary' });

    const scroller = page.getByTestId('exam-summary-scroll');
    await expectFillsParent(scroller, scroller.locator('xpath=..'), ['top', 'right', 'bottom', 'left'], { name: 'the scroller of the summary' });
    // the scroller is the card of the summary: the title row keeps the 12px of every card of the exam, and so does the end of the page
    for (const edge of ['left', 'top', 'right'] as const) {
        await expectInset(row, scroller, edge, CARD_INSET, { name: 'title row of the summary', scrollers: [scroller] });
    }
    await expectComputedStyle(scroller, 'padding-bottom', `${CARD_INSET}px`, { name: 'the space below the summary' });
    // The scroller and the page that holds it both take a percentage of the same chain of heights. Should the chain lose its definite
    // height, both grow to the height of their content and still agree, and the summary no longer scrolls inside its card.
    await expectWithinViewport(scroller, ['bottom'], { name: 'the scroller of the summary' });
    if (viewport.width >= COURSE_SHELL_MIN_WIDTH) {
        await expectHeight(page.getByTestId('exam-summary-back-button'), SMALL_BUTTON_HEIGHT, { name: 'back to overview button of the summary' });
        await expectNoHorizontalOverflow(page, [scroller]);
    }
}

/**
 * Expects a complaint box (the form of a new complaint, the complaint of the student, the response to it) to have a border on all four
 * sides, in a colour that shows, so that the complaint is set apart from the page around it and does not float on it.
 * @param name what the box is, e.g. `the complaint of the student`
 */
export async function expectComplaintBox(box: Locator, name: string): Promise<void> {
    for (const side of ['top', 'right', 'bottom', 'left']) {
        await expectComputedStyle(box, `border-${side}-width`, COMPLAINT_BOX_BORDER_WIDTH, { name: `${side} border of ${name}` });
    }
    await expectComputedStyle(box, 'border-top-style', 'solid', { name: `border of ${name}` });
    await expect(box, `the border of ${name} is transparent`).not.toHaveCSS('border-top-color', TRANSPARENT);
}

/**
 * Expects the complaint area of the summary to be a block, with the complaint of the student and the response to it each in a box, and the
 * two texts to be two equal text areas, side by side from 1200px and one above the other below it: side by side they end on the same
 * line, stacked they start at the same edge. A custom element is inline by default, and its block child then splits it: margins on the
 * element are ignored and an empty line box opens above the area.
 */
export async function expectComplaintAreaLayout(page: Page, viewport: LayoutViewport): Promise<void> {
    // The id exists for the complaint form itself, which looks the text area up by it.
    const complaint = page.locator('#complainTextArea');
    const response = page.getByTestId('complainResponseTextArea');
    const requestBox = page.getByTestId('complaint-request-card');
    const responseBox = page.getByTestId('complaint-response-card');
    const sideBySide = viewport.width >= COMPLAINT_COLUMNS_MIN_WIDTH;
    const options = { name: `the complaint and the response to it, ${sideBySide ? 'side by side' : 'one above the other'}` };
    await expectComputedStyle(page.getByTestId('complaint-student-view').filter({ has: complaint }), 'display', 'block', { name: 'the complaint area' });
    await expectComplaintBox(requestBox, 'the complaint of the student');
    await expectComplaintBox(responseBox, 'the response to the complaint');
    await expectInside(complaint, requestBox, ['top', 'right', 'bottom', 'left'], { name: 'the text of the complaint in its box' });
    await expectInside(response, responseBox, ['top', 'right', 'bottom', 'left'], { name: 'the text of the response in its box' });
    await expectAligned([requestBox, responseBox], 'width', options);
    await expectAligned([complaint, response], 'width', options);
    await expectAligned([complaint, response], 'height', options);
    // What holds for one arrangement fails for the other: stacked areas end on different lines, side by side ones start at different edges.
    await expectAligned([complaint, response], sideBySide ? 'bottom' : 'left', options);
    await expectAligned([requestBox, responseBox], sideBySide ? 'bottom' : 'left', options);
}

/**
 * Expects the rating of the feedback ("How useful is this feedback to you?") to be quiet: no tinted box around it, so it does not stand out
 * from the feedback it belongs to. The rating used to be an alert box in the colour of the info alerts.
 */
export async function expectQuietRating(rating: Locator): Promise<void> {
    // The element and the host around it: the host used to carry the alert classes
    for (const [box, name] of [
        [rating, 'the rating'],
        [rating.locator('xpath=..'), 'the host of the rating'],
    ] as const) {
        await expectComputedStyle(box, 'background-color', TRANSPARENT, { name: `background of ${name}` });
        await expectComputedStyle(box, 'border-top-width', '0px', { name: `border of ${name}` });
    }
}
