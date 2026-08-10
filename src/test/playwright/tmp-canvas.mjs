import { chromium } from '@playwright/test';
const browser = await chromium.launch();
const page = await browser.newPage({ viewport: { width: 1600, height: 1000 } });
await page.goto('http://localhost:9000/sign-in', { waitUntil: 'domcontentloaded' });
await page.locator('#username').waitFor({ state: 'visible', timeout: 30000 });
await page.locator('#username').fill('artemis_admin'); await page.locator('#username').blur();
await page.click('#continue-button');
await page.locator('#password').waitFor({ state: 'visible' }); await page.locator('#password').fill('artemis_admin'); await page.locator('#password').blur();
await page.click('#login-button'); await page.waitForTimeout(4000);
await page.goto('http://localhost:9000/course-management/9020/quiz-exercises/new', { waitUntil: 'domcontentloaded' });
const btn = page.getByRole('button', { name: /Add Apollon Drag-And-Drop Question/i }).first();
await btn.waitFor({ state: 'visible', timeout: 90000 }); await page.waitForTimeout(1200); await btn.click();
await page.locator('#open-diagram').first().waitFor({ state: 'visible', timeout: 60000 });
await page.locator('#open-diagram').first().click();
await page.locator('.apollon-editor').waitFor({ state: 'visible', timeout: 60000 });
await page.waitForTimeout(2500);
console.log(JSON.stringify(await page.evaluate(() => {
  const c = document.querySelector('.apollon-diagram-detail__canvas');
  const s = getComputedStyle(c);
  const root = getComputedStyle(document.documentElement);
  return {
    className: c.className,
    borderRadius: s.borderRadius,
    onEl_md: s.getPropertyValue('--apollon-chrome-radius-md'),
    onEl_lg: s.getPropertyValue('--apollon-chrome-radius-lg'),
    onRoot_md: root.getPropertyValue('--apollon-chrome-radius-md'),
  };
}), null, 2));
await browser.close();
