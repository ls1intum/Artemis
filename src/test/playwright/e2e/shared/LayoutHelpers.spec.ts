import { Page, expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import {
    LAYOUT_VIEWPORTS,
    expectAligned,
    expectComputedStyle,
    expectFillsParent,
    expectHeight,
    expectNoHorizontalOverflow,
    expectNoHorizontalScrollAround,
    expectSameComputedStyle,
    expectSameValue,
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
