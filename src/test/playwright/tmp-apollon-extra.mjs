import { chromium } from '@playwright/test';
import fs from 'node:fs';

const OUT = '/private/tmp/claude-501/-Users-felixdietrich-Documents-Artemis2/8f7402fe-be82-4a0e-b2ca-8e9963e7dcad/scratchpad';
fs.mkdirSync(OUT, { recursive: true });
const tag = process.env.TAG || 'extra';
const viewport = { width: Number(process.env.VW || 1600), height: Number(process.env.VH || 1000) };
const dark = process.env.DARK === '1';
const empty = process.env.EMPTY === '1';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport });
page.on('pageerror', (e) => console.log('[pageerror]', String(e).slice(0, 300)));

if (empty) {
    await page.route('**/api/modeling/courses/*/apollon-diagrams**', async (route) => {
        if (route.request().method() !== 'GET') return route.fallback();
        return route.fulfill({ status: 200, contentType: 'application/json', body: '[]' });
    });
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
    // Use the app's own theme switch so the ThemeService owns the change.
    const toggle = page.locator('#theme-toggle').first();
    if (await toggle.count()) {
        await toggle.click();
        await page.waitForTimeout(1500);
    }
    console.log('theme =', await page.evaluate(() => document.documentElement.getAttribute('data-theme')));
}

await page.goto('http://localhost:9000/course-management/9020/quiz-exercises/new', { waitUntil: 'domcontentloaded' });
const btn = page.getByRole('button', { name: /Add Apollon Drag-And-Drop Question/i }).first();
await btn.waitFor({ state: 'visible', timeout: 90000 });
await page.waitForTimeout(1200);
await btn.click();
const dialog = page.locator('.p-dialog');
await dialog.waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(2500);
await dialog.screenshot({ path: `${OUT}/${tag}-list.png` });

if (empty) {
    console.log('empty-state visible:', await page.locator('[data-testid="apollon-diagram-empty"]').isVisible());
    await browser.close();
    process.exit(0);
}

await page.locator('#open-diagram').first().click();
await page.locator('.apollon-editor').waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(3000);

// Drive a real selection through the Apollon instance the component exposes for E2E.
const selected = await page.evaluate(async () => {
    const host = document.querySelector('jhi-apollon-diagram-detail');
    const editor = host.__apollonEditor;
    const model = editor.model;
    const first = model.nodes?.[0]?.id;
    if (!first) return { ok: false, reason: 'no nodes' };
    editor.select?.([first]) ?? editor.setSelection?.([first]);
    return { ok: true, id: first, api: Object.getOwnPropertyNames(Object.getPrototypeOf(editor)).filter((n) => /select/i.test(n)) };
});
console.log('SELECT', JSON.stringify(selected));
await page.waitForTimeout(1500);
console.log('download disabled after programmatic select:', await page.locator('[data-testid="apollon-diagram-download"]').isDisabled());

// Fall back to clicking a node on the canvas.
const node = page.locator('.apollon-editor .react-flow__node').first();
if (await node.count()) {
    await node.click({ force: true });
    await page.waitForTimeout(1500);
}
const downloadDisabled = await page.locator('[data-testid="apollon-diagram-download"]').isDisabled();
console.log('download disabled after node click:', downloadDisabled);
await dialog.screenshot({ path: `${OUT}/${tag}-detail.png` });
if (!downloadDisabled) {
    await page.locator('[data-apollon-region="top-right"]').screenshot({ path: `${OUT}/${tag}-island-enabled.png` });
    await page.locator('[data-testid="apollon-diagram-download"]').hover();
    await page.waitForTimeout(900);
    await dialog.screenshot({ path: `${OUT}/${tag}-tooltip.png` });
}
await browser.close();
