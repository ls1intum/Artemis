import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

async function expectReferenceTheme(page: Page, theme: 'light' | 'dark') {
    await expect(page).toHaveURL(new RegExp(`/developer/tum-ui/\\?path=/docs/introduction--docs&globals=theme:${theme}$`));
    await expect(page.getByRole('button', { name: /Theme/ })).toContainText(`${theme} theme`);
    const documentation = page.frameLocator('#storybook-preview-iframe').locator('.sbdocs-wrapper');
    await expect(documentation).toBeVisible();
    return documentation.evaluate((element) => getComputedStyle(element).backgroundColor);
}

async function selectDocumentationTheme(page: Page, theme: 'light' | 'dark') {
    const root = page.locator('html');
    const toggle = page.getByRole('button', { name: /Switch between dark and light mode/ });
    for (let attempt = 0; attempt < 3 && (await root.getAttribute('data-theme-choice')) !== theme; attempt++) {
        await toggle.click();
    }
    await expect(root).toHaveAttribute('data-theme-choice', theme);
    await expect(root).toHaveAttribute('data-theme', theme);
}

test('connects the coding guidelines and component reference with the selected theme', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('./developer/intro');
    await page.getByRole('main').getByRole('link', { name: 'Artemis client' }).click();
    await expect(page).toHaveURL(/\/developer\/artemis-client$/);
    await expect(page.getByRole('complementary').getByRole('link', { name: 'TUM UI component reference' })).toBeVisible();

    await selectDocumentationTheme(page, 'dark');
    await page
        .getByRole('main')
        .getByRole('link', { name: /TUM UI components/ })
        .click();
    const darkReferenceBackground = await expectReferenceTheme(page, 'dark');
    await expect(page.getByRole('link', { name: 'Back to TUM UI package guide' })).toBeVisible();
    await expect(page.frameLocator('#storybook-preview-iframe').getByRole('heading', { level: 1, name: 'Introduction' })).toBeVisible();

    await page.getByRole('link', { name: 'Back to TUM UI package guide' }).click();
    await expect(page).toHaveURL(/\/developer\/guidelines\/tum-ui-kit$/);
    await expect(page.getByRole('heading', { level: 1, name: 'TUM UI package' })).toBeVisible();

    await selectDocumentationTheme(page, 'light');
    await page.getByRole('complementary').getByRole('link', { name: 'TUM UI component reference' }).click();
    const lightReferenceBackground = await expectReferenceTheme(page, 'light');
    expect(lightReferenceBackground).not.toBe(darkReferenceBackground);
});

test('indexes component names in the developer documentation search', async ({ request }) => {
    const response = await request.get('./search-index-developer.json');
    await expect(response).toBeOK();
    await expect(await response.text()).toContain('Radio Button');
});
