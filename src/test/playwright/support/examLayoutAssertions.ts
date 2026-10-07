import { Locator, Page, expect } from '@playwright/test';
import {
    LayoutViewport,
    expectAligned,
    expectBelow,
    expectComputedStyle,
    expectFillsParent,
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

/** The height of a small TUM AET UI button outside a title row: the start and hand-in buttons, the hand-in early button of the exam bar, the back button of the summary. */
export const SMALL_BUTTON_HEIGHT = 34;

/**
 * The height of a button in a title row: the save button, the export button of the summary and the actions of the programming page. 30px leaves
 * 4.5px of air above and below it in the 40px row, where a small button of 34px would sit 3px from the rule below the title.
 */
export const TITLE_ROW_BUTTON_HEIGHT = 30;

/** The air a button in a title row keeps above it and below it, inside the row, in px. The row is 40px and the button 30px, so 4.5px are there; the rest is margin for rounding. */
const TITLE_ROW_BUTTON_AIR = 4;

/** The distance in px between the left edge of a card of the exam and the title in it, and between the left edge of the exam bar and its title. */
const CARD_INSET = 16;

/** The distance in px between the top of the card that holds a page of the exam and the title row of that page. */
const CARD_TOP_INSET = 12;

/** From this width in px the Code button, the result, Refresh and Submit of the programming page sit in its title row: the title keeps its room next to them. */
const PROGRAMMING_ACTIONS_IN_ROW_MIN_WIDTH = 1280;

/** Below this width in px the programming actions do not fit next to the title any more and stay below the row, in the bar of the editor. */
const PROGRAMMING_ACTIONS_BELOW_MAX_WIDTH = 480;

/** The width in px the title of a page keeps at least next to the programming actions (the product keeps 256px, a name that is cut off there is still readable). */
const MIN_TITLE_WIDTH_NEXT_TO_ACTIONS = 200;

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
 * Expects the title of a page of the running exam to start 16px from the left edge of the card that holds the page, and its title row 12px
 * from the top of it. The exam bar has the same inset (see {@link expectExamBarTitleInset}), so the titles of the bar and of every page
 * are set the same distance inside their card, and the card does not waste the room around the content.
 */
export async function expectTitleInCard(row: Locator, title: Locator, card: Locator, name: string): Promise<void> {
    await expectInset(title, card, 'left', CARD_INSET, { name: `title of ${name}` });
    await expectInset(row, card, 'top', CARD_TOP_INSET, { name: `title row of ${name}` });
}

/** Expects the title of the exam bar to start 16px from the left edge of the bar, the same inset as the titles of the pages in their card. */
export async function expectExamBarTitleInset(page: Page): Promise<void> {
    await expectInset(page.getByTestId('exam-bar-title'), page.getByTestId('exam-bar'), 'left', CARD_INSET, { name: 'title of the exam bar' });
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
