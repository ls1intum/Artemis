import { Page, expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import {
    LAYOUT_VIEWPORTS,
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
    expectSameValue,
    expectScrollPositionKept,
    expectWithinViewport,
    forEachViewport,
    measure,
} from '../../support/layout';

/**
 * The layout helpers have to fail when a layout is wrong, or a contract test that uses them passes without checking anything. This
 * renders small documents of known geometry and runs every helper against one that holds and one that does not. It needs no login
 * and no page of its own: every document is rendered with `page.setContent`.
 */

// A violated expectation is retried for its timeout, so the cases that are meant to fail should not wait for long. An attempt may look
// for its element for half of it, which leaves a browser round trip on a loaded runner enough time to report what is really wrong.
const FAILING = { timeout: 600 };

async function render(page: Page, html: string) {
    await page.setContent(`<!doctype html><html><body style="margin: 0">${html}</body></html>`);
}

test.describe('Layout helpers', { tag: '@fast' }, () => {
    test.use({ viewport: { width: 800, height: 600 } });

    test('measure reads the border box and refuses an element that is not rendered', async ({ page }) => {
        await render(
            page,
            `<div id="box" style="position: absolute; top: 10px; left: 20px; width: 100px; height: 40px; box-sizing: border-box; border: 1px solid"></div>
             <div id="hidden" style="display: none"></div>`,
        );

        expect(await measure(page.locator('#box'))).toEqual({ top: 10, left: 20, right: 120, bottom: 50, width: 100, height: 40 });
        await expect(measure(page.locator('#hidden'), FAILING)).rejects.toThrow(/not rendered/);
        await expect(measure(page.locator('#missing'), FAILING)).rejects.toThrow(/no element found within 600 ms/);
    });

    test('expectHeight fails with the element, the viewport and the numbers', async ({ page }) => {
        await render(
            page,
            `<div id="exact" style="height: 40px"></div>
             <div id="close" style="height: 40.4px"></div>
             <div id="off" style="height: 41px"></div>`,
        );

        await expectHeight(page.locator('#exact'), 40);
        await expectHeight(page.locator('#close'), 40);
        await expectHeight(page.locator('#off'), 40, { tolerance: 1 });
        await expect(expectHeight(page.locator('#off'), 40, { ...FAILING, name: 'title row' })).rejects.toThrow(
            /title row at 800x600: height is 41px, expected 40px \(tolerance 0.5px\)/,
        );
        await expect(expectHeight(page.locator('#off'), 40, FAILING)).rejects.toThrow(/locator\('#off'\) at 800x600: height is 41px, expected 40px/);
    });

    test('expectHeight waits for a layout that is still settling', async ({ page }) => {
        await render(page, `<div id="late" style="height: 30px"></div><script>setTimeout(() => (document.getElementById('late').style.height = '40px'), 400)</script>`);

        await expectHeight(page.locator('#late'), 40);
    });

    test('expectAligned compares edges and sizes of several elements', async ({ page }) => {
        await render(
            page,
            `<div id="a" style="position: absolute; top: 0; left: 0; width: 100px; height: 100px"></div>
             <div id="same" style="position: absolute; top: 0; left: 150px; width: 100px; height: 100px"></div>
             <div id="lower" style="position: absolute; top: 0; left: 300px; width: 100px; height: 103px"></div>`,
        );
        const [a, same, lower] = [page.locator('#a'), page.locator('#same'), page.locator('#lower')];

        await expectAligned([a, same], 'bottom');
        await expectAligned([a, same, lower], 'top');
        await expectAligned([a, lower], 'bottom', { tolerance: 3 });
        await expect(expectAligned([a, lower], 'bottom', { ...FAILING, name: 'the cards' })).rejects.toThrow(
            /the cards at 800x600: bottom is not the same: locator\('#a'\) 100px, locator\('#lower'\) 103px \(tolerance 0.5px\)/,
        );
        await expect(expectAligned([a, lower], 'height', FAILING)).rejects.toThrow(/height is not the same/);
    });

    test('expectFillsParent fails for a child that stops short of or sticks out of its parent', async ({ page }) => {
        await render(
            page,
            `<div id="parent" style="position: relative; height: 200px; width: 400px">
                <div id="fills" style="position: absolute; inset: 0"></div>
             </div>
             <div id="short-parent" style="position: relative; height: 200px; width: 400px">
                <div id="short" style="position: absolute; top: 0; left: 0; right: 0; height: 180px"></div>
             </div>
             <div id="beyond-parent" style="position: relative; height: 200px; width: 400px">
                <div id="beyond" style="position: absolute; top: 0; left: 0; right: 0; height: 230px"></div>
             </div>`,
        );

        await expectFillsParent(page.locator('#fills'), page.locator('#parent'));
        await expectFillsParent(page.locator('#short'), page.locator('#short-parent'), ['top', 'left', 'right']);
        await expect(expectFillsParent(page.locator('#short'), page.locator('#short-parent'), ['bottom'], FAILING)).rejects.toThrow(
            /locator\('#short'\) at 800x600: does not fill locator\('#short-parent'\): bottom at 380px but its parent's at 400px \(20px short\)/,
        );
        await expect(expectFillsParent(page.locator('#beyond'), page.locator('#beyond-parent'), ['bottom'], FAILING)).rejects.toThrow(/\(30px beyond\)/);
    });

    test('expectWithinViewport fails for an element that reaches beyond the edges of the window it is asked about', async ({ page }) => {
        await render(
            page,
            `<div id="inside" style="position: absolute; top: 0; left: 0; width: 700px; height: 500px"></div>
             <div id="tall" style="position: absolute; top: 0; left: 0; width: 700px; height: 1500px"></div>
             <div id="wide" style="position: absolute; top: 0; left: 0; width: 1200px; height: 100px"></div>`,
        );

        await expectWithinViewport(page.locator('#inside'), ['top', 'right', 'bottom', 'left']);
        await expectWithinViewport(page.locator('#tall'), ['top', 'right', 'left']);
        await expect(expectWithinViewport(page.locator('#tall'), ['bottom'], { ...FAILING, name: 'the scroller' })).rejects.toThrow(
            /the scroller at 800x600: reaches beyond the window: bottom at 1500px but the window's at 600px \(900px beyond\)/,
        );
        await expect(expectWithinViewport(page.locator('#wide'), ['right', 'bottom'], FAILING)).rejects.toThrow(/right at 1200px but the window's at 800px \(400px beyond\)/);
    });

    test('expectInside fails for a child that sticks out of its container or keeps less air than it needs', async ({ page }) => {
        await render(
            page,
            `<div id="row" style="position: absolute; top: 100px; left: 0; width: 400px; height: 40px"></div>
             <div id="centered" style="position: absolute; top: 105px; left: 10px; width: 100px; height: 30px"></div>
             <div id="close" style="position: absolute; top: 102px; left: 10px; width: 100px; height: 36px"></div>
             <div id="tall" style="position: absolute; top: 100px; left: 10px; width: 100px; height: 44px"></div>
             <div id="wide" style="position: absolute; top: 105px; left: 350px; width: 100px; height: 30px"></div>`,
        );
        const row = page.locator('#row');

        await expectInside(page.locator('#centered'), row);
        await expectInside(page.locator('#centered'), row, ['top', 'bottom'], { margin: 5 });
        await expect(expectInside(page.locator('#close'), row, ['top', 'bottom'], { ...FAILING, margin: 4, name: 'save button' })).rejects.toThrow(
            /save button at 800x600: does not lie inside locator\('#row'\): top edge has 2px of space, at least 4px are needed, bottom edge has 2px of space, at least 4px are needed/,
        );
        await expect(expectInside(page.locator('#tall'), row, ['top', 'bottom'], FAILING)).rejects.toThrow(/bottom edge sticks out by 4px/);
        await expect(expectInside(page.locator('#wide'), row, undefined, FAILING)).rejects.toThrow(/right edge sticks out by 50px/);
        await expect(expectInside(page.locator('#wide'), row, [], FAILING)).rejects.toThrow(/no edges to compare with locator\('#row'\)/);
    });

    test('expectInset fails when the child starts at another distance from the edge of its container', async ({ page }) => {
        await render(
            page,
            `<div id="card" style="position: absolute; top: 50px; left: 200px; width: 500px; height: 300px"></div>
             <div id="title" style="position: absolute; top: 62px; left: 216px; width: 100px; height: 20px"></div>
             <div id="late" style="position: absolute; top: 62px; left: 224px; width: 100px; height: 20px"></div>`,
        );
        const card = page.locator('#card');

        await expectInset(page.locator('#title'), card, 'left', 16);
        await expectInset(page.locator('#title'), card, 'top', 12);
        await expect(expectInset(page.locator('#late'), card, 'left', 16, { ...FAILING, name: 'title of the quiz page' })).rejects.toThrow(
            /title of the quiz page at 800x600: its left edge is 24px inside locator\('#card'\), expected 16px \(tolerance 0.5px\)/,
        );
    });

    test('expectBelow and expectNoOverlap tell what lies below an element and what covers another', async ({ page }) => {
        await render(
            page,
            `<div id="row" style="position: absolute; top: 0; left: 0; width: 700px; height: 40px"></div>
             <div id="title" style="position: absolute; top: 5px; left: 0; width: 300px; height: 30px"></div>
             <div id="beside" style="position: absolute; top: 5px; left: 300px; width: 300px; height: 30px"></div>
             <div id="over" style="position: absolute; top: 5px; left: 250px; width: 300px; height: 30px"></div>
             <div id="below" style="position: absolute; top: 40px; left: 0; width: 700px; height: 30px"></div>
             <div id="half" style="position: absolute; top: 30px; left: 0; width: 700px; height: 30px"></div>`,
        );

        await expectBelow([page.locator('#below')], page.locator('#row'));
        await expect(expectBelow([page.locator('#below'), page.locator('#half')], page.locator('#row'), { ...FAILING, name: 'the toolbar' })).rejects.toThrow(
            /the toolbar at 800x600: not below locator\('#row'\), which ends at 40px: locator\('#half'\) starts at 30px/,
        );
        await expect(expectBelow([], page.locator('#row'))).rejects.toThrow(/expectBelow: got no element/);

        await expectNoOverlap([page.locator('#title'), page.locator('#beside')]);
        await expect(expectNoOverlap([page.locator('#title'), page.locator('#over')], { ...FAILING, name: 'title and actions' })).rejects.toThrow(
            /title and actions at 800x600: overlap: locator\('#title'\) and locator\('#over'\) share 50px x 30px/,
        );
        await expect(expectNoOverlap([page.locator('#title')])).rejects.toThrow(/expectNoOverlap: got 1 element\(s\), at least 2 are needed/);
    });

    test('expectInsideOrBelow reports where the elements are and fails for a toolbar that is half in', async ({ page }) => {
        await render(
            page,
            `<div id="row" style="position: absolute; top: 0; left: 0; width: 700px; height: 40px"></div>
             <div id="a" style="position: absolute; top: 5px; left: 400px; width: 100px; height: 30px"></div>
             <div id="b" style="position: absolute; top: 5px; left: 520px; width: 100px; height: 30px"></div>
             <div id="c" style="position: absolute; top: 50px; left: 400px; width: 100px; height: 30px"></div>
             <div id="d" style="position: absolute; top: 50px; left: 520px; width: 100px; height: 30px"></div>
             <div id="half" style="position: absolute; top: 5px; left: 650px; width: 100px; height: 30px"></div>`,
        );
        const row = page.locator('#row');

        expect(await expectInsideOrBelow([page.locator('#a'), page.locator('#b')], row)).toBe('inside');
        expect(await expectInsideOrBelow([page.locator('#c'), page.locator('#d')], row)).toBe('below');
        await expect(expectInsideOrBelow([page.locator('#a'), page.locator('#c')], row, { ...FAILING, name: 'the toolbar' })).rejects.toThrow(
            /the toolbar at 800x600: not all inside locator\('#row'\) and not all below it: locator\('#a'\) is inside, locator\('#c'\) is below/,
        );
        await expect(expectInsideOrBelow([page.locator('#half')], row, FAILING)).rejects.toThrow(/locator\('#half'\) is neither inside nor below/);
        await expect(expectInsideOrBelow([], row)).rejects.toThrow(/expectInsideOrBelow: got no element/);
    });

    test('expectScrollPositionKept fails when the action moves the scroller, also when it scrolls smoothly, and refuses a scroller that cannot move', async ({ page }) => {
        await render(
            page,
            `<div id="scroller" data-testid="scroller" style="height: 200px; overflow-y: auto">
                <div style="height: 1000px">
                    <button id="stay" type="button" style="margin-top: 400px">Stay</button>
                    <button id="jump" type="button" onclick="document.getElementById('scroller').scrollTop = 0">Jump</button>
                    <button id="glide" type="button" onclick="setTimeout(() => document.getElementById('scroller').scrollTo({ top: 600, behavior: 'smooth' }), 100)">Glide</button>
                </div>
             </div>
             <div id="short" data-testid="short" style="height: 200px; overflow-y: auto"><div style="height: 100px"></div></div>`,
        );
        const scroller = page.getByTestId('scroller');
        await scroller.evaluate((element) => (element.scrollTop = 350));

        await expectScrollPositionKept(scroller, () => page.locator('#stay').click());
        await expect(expectScrollPositionKept(scroller, () => page.locator('#jump').click(), { name: 'the summary' })).rejects.toThrow(
            /the summary at 800x600: scrolled from 350px to 0px \(350px up\), expected it to stay where it was \(tolerance 1px\)/,
        );
        // A scroll that starts a moment after the click and takes a while is waited for, not raced
        await scroller.evaluate((element) => (element.scrollTop = 350));
        await expect(expectScrollPositionKept(scroller, () => page.locator('#glide').click())).rejects.toThrow(/scrolled from 350px to 600px \(250px down\)/);
        await expect(expectScrollPositionKept(page.getByTestId('short'), async () => undefined)).rejects.toThrow(/does not scroll \(200px of content in a 200px box\)/);
    });

    test('expectScrollPositionKept places the element only once the content above it has loaded', async ({ page }) => {
        await render(
            page,
            `<div id="scroller" data-testid="scroller" style="height: 200px; overflow-y: auto">
                <div id="above" style="height: 100px"></div>
                <button id="stay" type="button">Stay</button>
                <div style="height: 1000px"></div>
             </div>
             <script>setTimeout(() => (document.getElementById('above').style.height = '600px'), 300)</script>`,
        );
        const [scroller, button] = [page.getByTestId('scroller'), page.locator('#stay')];

        await expectScrollPositionKept(scroller, () => button.click(), { placed: { element: button, at: 0.5 } });

        // The content above the button grew after the check had started, and the button is halfway down the scroller all the same
        const [scrollerBox, buttonBox] = [await measure(scroller), await measure(button)];
        expect(Math.abs(buttonBox.top - scrollerBox.top - scrollerBox.height / 2)).toBeLessThan(2);
    });

    test('the helpers that retry name an element that never comes instead of reporting a bare timeout', async ({ page }) => {
        await render(page, `<div id="there" style="height: 40px"></div>`);
        const there = page.locator('#there');
        const missing = page.locator('#missing');
        const named = /locator\('#missing'\): no element found/;

        await expect(expectHeight(missing, 40, FAILING)).rejects.toThrow(named);
        await expect(expectAligned([there, missing], 'bottom', FAILING)).rejects.toThrow(named);
        await expect(expectFillsParent(missing, there, ['top'], FAILING)).rejects.toThrow(named);
        await expect(expectFillsParent(there, missing, ['top'], FAILING)).rejects.toThrow(named);
        await expect(expectWithinViewport(missing, ['bottom'], FAILING)).rejects.toThrow(named);
        await expect(expectNoHorizontalOverflow(page, [missing], FAILING)).rejects.toThrow(named);
    });

    test('the helpers refuse a comparison that would pass without comparing anything', async ({ page }) => {
        await render(page, `<div id="only" style="height: 40px"></div>`);
        const only = page.locator('#only');

        await expect(expectAligned([], 'top')).rejects.toThrow(/expectAligned: got 0 element\(s\), at least 2 are needed to compare them/);
        await expect(expectAligned([only], 'top', { name: 'the cards' })).rejects.toThrow(/the cards: got 1 element\(s\), at least 2 are needed/);
        await expect(expectFillsParent(only, only, [])).rejects.toThrow(/no edges to compare with locator\('#only'\)/);
        await expect(expectWithinViewport(only, [])).rejects.toThrow(/no edges to compare with the window/);
        expect(() => expectSameValue(page, 'top of the title row', {})).toThrow(/got 0 value\(s\), at least 2 are needed/);
        expect(() => expectSameValue(page, 'top of the title row', { overview: 52 })).toThrow(/got 1 value\(s\), at least 2 are needed/);
    });

    test('expectNoHorizontalOverflow names the element that makes the page scroll sideways', async ({ page }) => {
        await render(page, `<div style="width: 700px; height: 20px"></div>`);
        await expectNoHorizontalOverflow(page);

        await render(page, `<div class="wide-table" data-testid="wide" style="width: 2000px; height: 20px"></div>`);
        await expect(expectNoHorizontalOverflow(page, [], FAILING)).rejects.toThrow(
            /horizontal overflow at 800x600: the document is 2000px wide in a 800px viewport, sticking out: div\[data-testid=wide\] \(1200px beyond the right edge\)/,
        );
    });

    test('expectNoHorizontalOverflow does not count the pixel that rounding adds, and counts two', async ({ page }) => {
        await render(page, `<div style="width: 801px; height: 20px"></div>`);
        await expectNoHorizontalOverflow(page);

        await render(page, `<div style="width: 802px; height: 20px"></div>`);
        await expect(expectNoHorizontalOverflow(page, [], FAILING)).rejects.toThrow(/the document is 802px wide in a 800px viewport/);
    });

    test('expectNoHorizontalOverflow accepts what a scroll container clips and checks the given scroll containers', async ({ page }) => {
        await render(
            page,
            `<div data-testid="wrapper" style="overflow-x: auto; width: 300px">
                <div style="width: 900px; height: 20px"></div>
             </div>`,
        );
        const wrapper = page.getByTestId('wrapper');

        await expectNoHorizontalOverflow(page);
        await expect(expectNoHorizontalOverflow(page, [wrapper], FAILING)).rejects.toThrow(/getByTestId\('wrapper'\) is 900px wide in a 300px box, sticking out: div/);
    });

    test('expectNoHorizontalScrollAround finds the scroll container a page sits in and ignores the ones inside the page', async ({ page }) => {
        await render(
            page,
            `<div data-testid="card" style="overflow-y: auto; width: 300px; height: 200px">
                <div data-testid="row" style="width: 100px; height: 20px"></div>
                <div style="overflow-x: auto; width: 250px"><div style="width: 900px; height: 20px"></div></div>
             </div>`,
        );
        await expectNoHorizontalScrollAround(page.getByTestId('row'));

        await render(
            page,
            `<div data-testid="card" style="overflow-y: auto; width: 300px; height: 200px">
                <div data-testid="row" style="width: 900px; height: 20px"></div>
             </div>`,
        );
        await expect(expectNoHorizontalScrollAround(page.getByTestId('row'), { ...FAILING, name: 'the title row' })).rejects.toThrow(
            /the title row at 800x600: sits in a container that scrolls sideways: div\[data-testid=card\] is 900px wide in a 300px box, sticking out: div\[data-testid=row\] \(600px beyond the right edge\)/,
        );
        // Without the element there are no containers to look at, which must not pass for a page that fits.
        await expect(expectNoHorizontalScrollAround(page.getByTestId('missing'), FAILING)).rejects.toThrow(/no element found within 600 ms/);
    });

    test('expectComputedStyle and expectSameComputedStyle compare what the browser computes', async ({ page }) => {
        await render(
            page,
            `<h3 class="alike" style="font-size: 16px; font-weight: 600">General Information</h3>
             <h3 class="alike" style="font-size: 16px; font-weight: 600">Exercises</h3>
             <h3 class="odd" style="font-size: 24px; font-weight: 600">Result Overview</h3>
             <h3 class="odd" style="font-size: 16px; font-weight: 600">Points</h3>
             <h3 class="single" style="font-size: 16px">Alone</h3>`,
        );

        await expectComputedStyle(page.locator('.single'), 'font-size', '16px');
        await expect(expectComputedStyle(page.locator('.single'), 'font-size', '18px', { ...FAILING, name: 'heading' })).rejects.toThrow(
            // The matcher highlights the characters that differ, which splits the values by control characters; the words around them are matched.
            /heading at 800x600: computed font-size[\s\S]*Expected[\s\S]*Received/,
        );
        await expectSameComputedStyle(page.locator('.alike'), ['font-size', 'font-weight']);
        await expect(expectSameComputedStyle(page.locator('.odd'), ['font-weight', 'font-size'], FAILING)).rejects.toThrow(
            /font-size is not the same: 'Result Overview' 24px, 'Points' 16px/,
        );
        await expect(expectSameComputedStyle(page.locator('.single'), ['font-size'], FAILING)).rejects.toThrow(/found 1 element\(s\), at least 2 are needed/);
    });

    test('expectSameValue compares recorded values', async ({ page }) => {
        expectSameValue(page, 'top of the title row', { overview: 52, text: 52.3, quiz: 52 });
        expect(() => expectSameValue(page, 'top of the title row', { overview: 52, text: 58 })).toThrow(
            /top of the title row at 800x600 is not the same: overview 52px, text 58px \(tolerance 0.5px\)/,
        );
    });

    test('forEachViewport applies every viewport before the check and restores the original one', async ({ page }) => {
        await render(page, `<style>#title { height: 40px } @media (max-width: 800px) { #title { height: 60px } }</style><div id="title"></div>`);
        const seen: string[] = [];

        await forEachViewport(page, async (viewport) => {
            seen.push(`${viewport.width}x${await page.evaluate(() => window.innerHeight)}`);
            await expectHeight(page.locator('#title'), viewport.width <= 800 ? 60 : 40);
        });

        expect(seen).toEqual(LAYOUT_VIEWPORTS.map((viewport) => `${viewport.width}x${viewport.height}`));
        expect(page.viewportSize()).toEqual({ width: 800, height: 600 });
    });

    test('forEachViewport restores the original viewport when a check fails', async ({ page }) => {
        await render(page, `<div id="title" style="height: 41px"></div>`);

        await expect(forEachViewport(page, () => expectHeight(page.locator('#title'), 40, FAILING))).rejects.toThrow(/at 1728x1000: height is 41px/);

        expect(page.viewportSize()).toEqual({ width: 800, height: 600 });
    });
});
