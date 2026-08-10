import { chromium } from '@playwright/test';
import fs from 'node:fs';

const OUT = '/private/tmp/claude-501/-Users-felixdietrich-Documents-Artemis2/8f7402fe-be82-4a0e-b2ca-8e9963e7dcad/scratchpad';
fs.mkdirSync(OUT, { recursive: true });
const tag = process.env.TAG || 'verify';
const viewport = { width: Number(process.env.VW || 1600), height: Number(process.env.VH || 1000) };
const dark = process.env.DARK === '1';
const empty = process.env.EMPTY === '1';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport });
page.on('pageerror', (e) => console.log('[pageerror]', String(e).slice(0, 300)));
page.on('console', (m) => {
    const t = m.text();
    if (m.type() === 'error' && !t.includes('ServiceWorker') && !t.includes('ngsw-worker') && !t.includes('bad HTTP')) console.log('[console.error]', t.slice(0, 300));
});

if (empty) {
    await page.route('**/api/quiz/course/*/apollon-diagrams', (route) => route.fulfill({ status: 200, contentType: 'application/json', body: '[]' }));
}

await page.goto('http://localhost:9000/sign-in', { waitUntil: 'domcontentloaded' });
await page.locator('#username').waitFor({ state: 'visible', timeout: 30000 });
await page.locator('#username').fill('artemis_admin');
await page.locator('#username').blur();
await page.click('#continue-button');
await page.locator('#password').waitFor({ state: 'visible' });
await page.locator('#password').fill('artemis_admin');
await page.locator('#password').blur();
await page.click('#login-button');
await page.waitForTimeout(4000);

if (dark) {
    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
}

await page.goto('http://localhost:9000/course-management/9020/quiz-exercises/new', { waitUntil: 'domcontentloaded' });
if (dark) {
    await page.waitForTimeout(2000);
    await page.evaluate(() => document.documentElement.setAttribute('data-theme', 'dark'));
}
const btn = page.getByRole('button', { name: /Add Apollon Drag-And-Drop Question/i }).first();
await btn.waitFor({ state: 'visible', timeout: 90000 });
await page.waitForTimeout(1200);
await btn.click();

const dialog = page.locator('.p-dialog');
await dialog.waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(2000);
await dialog.screenshot({ path: `${OUT}/${tag}-list.png` });

if (empty) {
    console.log('empty-state visible:', await page.locator('[data-testid="apollon-diagram-empty"]').isVisible());
    await browser.close();
    process.exit(0);
}

// create dialog
await page.locator('#create-apollon-diagram').click();
await page.locator('#field_diagram_title').waitFor({ state: 'visible', timeout: 30000 });
await page.waitForTimeout(1200);
await page.locator('.p-dialog').last().screenshot({ path: `${OUT}/${tag}-create.png` });
await page.keyboard.press('Escape');
await page.waitForTimeout(800);

// detail
await page.locator('#open-diagram').first().click();
await page.locator('.apollon-editor').waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(3000);
await dialog.screenshot({ path: `${OUT}/${tag}-detail.png` });
await page.locator('.apollon-diagram-detail__header').screenshot({ path: `${OUT}/${tag}-detail-header.png` });

const region = page.locator('[data-apollon-region="top-right"]');
if (await region.count()) {
    await region.screenshot({ path: `${OUT}/${tag}-detail-island.png` });
}

// interactions
const before = {
    downloadDisabled: await page.locator('[data-testid="apollon-diagram-download"]').isDisabled(),
    saveDisabled: await page.locator('[data-testid="apollon-diagram-save"]').isDisabled(),
    saveState: (await page.locator('[data-testid="apollon-diagram-save-state"]').textContent())?.trim(),
    cropPressed: await page.locator('[data-testid="apollon-diagram-crop"]').getAttribute('aria-pressed'),
};

// drag a Class from the palette onto the canvas to create a selection
const paletteItem = page.locator('[data-apollon-control="apollon:palette"] >> text=Class').first();
const canvas = page.locator('.apollon-editor');
try {
    await paletteItem.dragTo(canvas, { targetPosition: { x: 600, y: 300 }, force: true });
    await page.waitForTimeout(2500);
} catch (e) {
    console.log('drag failed:', String(e).slice(0, 120));
}

// edit the title
await page.locator('[data-testid="apollon-diagram-title"]').fill('Renamed diagram');
await page.waitForTimeout(1200);

const after = {
    downloadDisabled: await page.locator('[data-testid="apollon-diagram-download"]').isDisabled(),
    saveDisabled: await page.locator('[data-testid="apollon-diagram-save"]').isDisabled(),
    saveState: (await page.locator('[data-testid="apollon-diagram-save-state"]').textContent())?.trim(),
};
console.log('BEFORE', JSON.stringify(before));
console.log('AFTER ', JSON.stringify(after));

await dialog.screenshot({ path: `${OUT}/${tag}-detail-interacted.png` });

// invalid title
await page.locator('[data-testid="apollon-diagram-title"]').fill('');
await page.waitForTimeout(800);
console.log('empty title -> save disabled:', await page.locator('[data-testid="apollon-diagram-save"]').isDisabled());
console.log('empty title -> generate disabled:', await page.locator('#generate-quiz-exercise').isDisabled());
await page.locator('.apollon-diagram-detail__header').screenshot({ path: `${OUT}/${tag}-detail-invalid.png` });

// geometry / overflow assertions
const geometry = await page.evaluate(() => {
    const r = (s) => {
        const el = document.querySelector(s);
        if (!el) return null;
        const b = el.getBoundingClientRect();
        return { x: Math.round(b.x), y: Math.round(b.y), w: Math.round(b.width), h: Math.round(b.height) };
    };
    const content = document.querySelector('.p-dialog-content');
    const canvasEl = document.querySelector('.apollon-diagram-detail__canvas');
    const cs = canvasEl ? getComputedStyle(canvasEl) : undefined;
    return {
        dialog: r('.p-dialog'),
        header: r('.apollon-diagram-detail__header'),
        canvas: r('.apollon-diagram-detail__canvas'),
        footer: r('.apollon-diagram-detail__footer'),
        island: r('[data-apollon-region="top-right"]'),
        contentScrolls: content ? content.scrollHeight > content.clientHeight + 1 : null,
        canvasBorder: cs ? `${cs.borderTopWidth} ${cs.borderTopColor}` : null,
        canvasRadius: cs?.borderRadius,
        bodyScrolls: document.documentElement.scrollWidth > document.documentElement.clientWidth,
    };
});
console.log('GEOMETRY', JSON.stringify(geometry, null, 2));

await browser.close();
