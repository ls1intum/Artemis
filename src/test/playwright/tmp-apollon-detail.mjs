import { chromium } from '@playwright/test';
import fs from 'node:fs';

const OUT = process.env.OUT_DIR || '/private/tmp/claude-501/-Users-felixdietrich-Documents-Artemis2/8f7402fe-be82-4a0e-b2ca-8e9963e7dcad/scratchpad';
fs.mkdirSync(OUT, { recursive: true });
const tag = process.env.TAG || 'shot';
const viewport = { width: Number(process.env.VW || 1600), height: Number(process.env.VH || 1000) };

const browser = await chromium.launch();
const page = await browser.newPage({ viewport });
page.on('console', (m) => {
    if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 300));
});
page.on('pageerror', (e) => console.log('[pageerror]', String(e).slice(0, 300)));

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

await page.goto('http://localhost:9000/course-management/9020/quiz-exercises/new', { waitUntil: 'domcontentloaded' });
const btn = page.getByRole('button', { name: /Add Apollon Drag-And-Drop Question/i }).first();
await btn.waitFor({ state: 'visible', timeout: 90000 });
await page.waitForTimeout(1500);
await btn.click();
await page.locator('#create-apollon-diagram').waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(1500);
await page.screenshot({ path: `${OUT}/${tag}-01-list.png` });

// open first existing diagram
await page.locator('#open-diagram').first().click();
await page.locator('.apollon-editor').waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(3000);
await page.screenshot({ path: `${OUT}/${tag}-02-detail.png` });

const measure = await page.evaluate(() => {
    const rect = (el) => (el ? { x: Math.round(el.getBoundingClientRect().x), y: Math.round(el.getBoundingClientRect().y), w: Math.round(el.getBoundingClientRect().width), h: Math.round(el.getBoundingClientRect().height) } : null);
    const q = (s) => document.querySelector(s);
    const cs = (el, props) => (el ? Object.fromEntries(props.map((p) => [p, getComputedStyle(el).getPropertyValue(p)])) : null);
    const box = ['padding', 'gap', 'display', 'height', 'min-height', 'overflow'];
    const dlg = q('.p-dialog');
    const out = {
        viewport: { w: innerWidth, h: innerHeight },
        dialog: rect(dlg),
        dialogContent: { r: rect(q('.p-dialog-content')), cs: cs(q('.p-dialog-content'), box) },
        detailHost: { r: rect(q('jhi-apollon-diagram-detail')), cs: cs(q('jhi-apollon-diagram-detail'), box) },
        header: rect(q('jhi-apollon-diagram-detail .modal-header')),
        body: rect(q('jhi-apollon-diagram-detail .modal-body')),
        footer: rect(q('jhi-apollon-diagram-detail .modal-footer')),
        editor: rect(q('.apollon-diagram-detail__editor')),
        apollonRoot: rect(q('.apollon-editor')),
        overflowY: dlg ? getComputedStyle(q('.p-dialog-content')).overflowY : null,
        scrollH: q('.p-dialog-content')?.scrollHeight,
        clientH: q('.p-dialog-content')?.clientHeight,
        regions: [...document.querySelectorAll('[data-apollon-region]')].map((e) => ({ region: e.dataset.apollonRegion, r: rect(e) })),
        controls: [...document.querySelectorAll('[data-apollon-control]')].map((e) => ({ id: e.dataset.apollonControl, r: rect(e) })),
    };
    return out;
});
console.log('DETAIL MEASURE', JSON.stringify(measure, null, 2));

await browser.close();
