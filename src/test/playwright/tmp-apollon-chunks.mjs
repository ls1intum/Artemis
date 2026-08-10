import { chromium } from '@playwright/test';

const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
const hits = [];
page.on('response', async (r) => {
    const u = r.url();
    if (!u.includes('localhost:9000') || !/\.(m?js)(\?|$)/.test(u)) return;
    try {
        const t = await r.text();
        if (t.includes('apollon-diagram-list') || t.includes('ApollonDiagramListComponent')) {
            hits.push({ url: u, hasNew: t.includes('apollon-diagram-list__header'), hasOld: t.includes('modal-header') && t.includes('table-striped') });
        }
    } catch {
        /* ignore */
    }
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
const btn = page.getByRole('button', { name: /Add Apollon Drag-And-Drop Question/i }).first();
await btn.waitFor({ state: 'visible', timeout: 90000 });
await page.waitForTimeout(1000);
await btn.click();
await page.locator('#create-apollon-diagram').waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(1500);
console.log(JSON.stringify(hits, null, 2));
await browser.close();
