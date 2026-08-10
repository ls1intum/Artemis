import { chromium } from '@playwright/test';
import fs from 'node:fs';

const OUT = process.env.OUT_DIR || '/private/tmp/claude-501/-Users-felixdietrich-Documents-Artemis2/8f7402fe-be82-4a0e-b2ca-8e9963e7dcad/scratchpad';
fs.mkdirSync(OUT, { recursive: true });
const tag = process.env.TAG || 'shot';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
page.on('console', (m) => {
    if (m.type() === 'error') console.log('[console.error]', m.text().slice(0, 300));
});

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
await page.waitForTimeout(6000);
await page.screenshot({ path: `${OUT}/${tag}-00-quiz-new.png` });

// Find the Apollon button
const btn = page.getByRole('button', { name: /Apollon/i }).first();
await btn.waitFor({ state: 'visible', timeout: 30000 });
await btn.click();
await page.waitForTimeout(3000);
await page.screenshot({ path: `${OUT}/${tag}-01-list.png` });

// Measure
const measure = await page.evaluate(() => {
    const q = (s) => document.querySelector(s);
    const rect = (el) => (el ? { ...el.getBoundingClientRect().toJSON() } : null);
    const dialog = q('.p-dialog');
    const content = q('.p-dialog-content');
    const host = q('jhi-apollon-diagram-import-dialog');
    const header = q('.modal-header');
    const body = q('.modal-body');
    const footer = q('.modal-footer');
    const cs = (el, props) => {
        if (!el) return null;
        const s = getComputedStyle(el);
        return Object.fromEntries(props.map((p) => [p, s.getPropertyValue(p)]));
    };
    const box = ['padding', 'margin', 'border-width', 'gap', 'display', 'min-height', 'height', 'overflow', 'background-color', 'border-radius'];
    return {
        dialog: { rect: rect(dialog), cs: cs(dialog, box) },
        content: { rect: rect(content), cs: cs(content, box) },
        host: { rect: rect(host), cs: cs(host, box) },
        header: { rect: rect(header), cs: cs(header, box) },
        body: { rect: rect(body), cs: cs(body, box) },
        footer: { rect: rect(footer), cs: cs(footer, box) },
    };
});
console.log('LIST MEASURE', JSON.stringify(measure, null, 2));

await browser.close();
