import { Locator, Page } from '@playwright/test';
import {
    LayoutViewport,
    expectAligned,
    expectComputedStyle,
    expectFillsParent,
    expectHeight,
    expectNoHorizontalOverflow,
    expectNoHorizontalScrollAround,
    expectSameComputedStyle,
    expectWithinViewport,
} from './layout';

/** The height of the title row, its rule included, that every page of the student exam starts with. It is the same on every page, so the pages do not jump when the student switches. */
const TITLE_ROW_HEIGHT = 40;

/** The height of the exam bar above the sidebar and the content. */
export const EXAM_BAR_HEIGHT = 40;

/** The height of a small TUM AET UI button: the save button, the hand-in button, the buttons of the summary. */
export const SMALL_BUTTON_HEIGHT = 34;

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

/** The width from which the complaint of the student and the response to it are set side by side, the `xl` breakpoint of the grid. Below it they are stacked. */
const COMPLAINT_COLUMNS_MIN_WIDTH = 1200;

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
 * Expects the exam summary the student has open to keep its layout at the current viewport: the title row and the buttons have their
 * sizes, the section headings look alike, the scroller fills the page that holds it and ends inside the window, and the page does not
 * scroll sideways.
 */
export async function expectExamSummaryLayout(page: Page, viewport: LayoutViewport): Promise<void> {
    const row = visibleTitleRow(page);
    await expectTitleRow(row, row.getByTestId('exam-exercise-title'), 'the summary');
    await expectHeight(row.getByTestId('exam-summary-export-button'), SMALL_BUTTON_HEIGHT, { name: 'export button of the summary' });
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
 * Expects the complaint area of the summary to be a block, and the complaint of the student and the response to it to be two equal text
 * areas, side by side from the `xl` breakpoint and one above the other below it: side by side they end on the same line, stacked they
 * start at the same edge. A custom element is inline by default, and its block child then splits it: margins on the element are
 * ignored and an empty line box opens above the area.
 */
export async function expectComplaintAreaLayout(page: Page, viewport: LayoutViewport): Promise<void> {
    // The id exists for the complaint form itself, which looks the text area up by it.
    const complaint = page.locator('#complainTextArea');
    const response = page.getByTestId('complainResponseTextArea');
    const sideBySide = viewport.width >= COMPLAINT_COLUMNS_MIN_WIDTH;
    const options = { name: `the complaint and the response to it, ${sideBySide ? 'side by side' : 'one above the other'}` };
    await expectComputedStyle(page.getByTestId('complaint-student-view').filter({ has: complaint }), 'display', 'block', { name: 'the complaint area' });
    await expectAligned([complaint, response], 'width', options);
    await expectAligned([complaint, response], 'height', options);
    // What holds for one arrangement fails for the other: stacked areas end on different lines, side by side ones start at different edges.
    await expectAligned([complaint, response], sideBySide ? 'bottom' : 'left', options);
}
