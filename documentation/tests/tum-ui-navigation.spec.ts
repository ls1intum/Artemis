import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

async function expectReferenceTheme(page: Page, theme: 'light' | 'dark', story = 'introduction--docs') {
    await expect(page).toHaveURL(new RegExp(`/developer/tum-ui/\\?path=/docs/${story}&globals=theme:${theme}$`));
    await expect(page.getByRole('button', { name: /Theme/ })).toContainText(`${theme} theme`);
    const preview = page.frameLocator('#storybook-preview-iframe');
    const documentation = preview.locator('.sbdocs-wrapper');
    await expect(documentation).toBeVisible();
    return {
        documentation: await documentation.evaluate((element) => getComputedStyle(element).backgroundColor),
        manager: await page.locator('body').evaluate((element) => getComputedStyle(element).backgroundColor),
    };
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

test('connects the development guide and component reference with the selected theme', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('./developer/intro');
    await page.getByRole('main').getByRole('link', { name: 'Coding and Design Guidelines' }).click();
    await expect(page.getByRole('heading', { level: 1, name: 'Coding and Design Guidelines' })).toBeVisible();

    await selectDocumentationTheme(page, 'dark');
    await page.getByRole('main').getByRole('link', { name: 'TUM UI component reference', exact: true }).click();
    const darkReferenceBackground = await expectReferenceTheme(page, 'dark');
    const packageGuide = page.getByRole('link', { name: 'TUM UI package guide' });
    await expect(packageGuide).toHaveAttribute('href', '/developer/guidelines/tum-ui-kit');
    await packageGuide.click();
    await expect(page).toHaveURL(/\/developer\/guidelines\/tum-ui-kit$/);
    await expect(page.getByRole('heading', { level: 1, name: 'TUM UI package' })).toBeVisible();

    await selectDocumentationTheme(page, 'light');
    await page.getByRole('main').getByRole('link', { name: 'TUM UI component reference', exact: true }).click();
    const lightReferenceBackground = await expectReferenceTheme(page, 'light');
    expect(lightReferenceBackground.documentation).not.toBe(darkReferenceBackground.documentation);
    expect(lightReferenceBackground.manager).not.toBe(darkReferenceBackground.manager);
});

test('finds a component reference through documentation search', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('./developer/intro');
    await page.getByRole('button', { name: 'Search documentation' }).click();
    const search = page.getByRole('dialog', { name: 'Search documentation' });
    await search.getByRole('textbox', { name: 'Search' }).fill('Radio Button');
    await search.getByRole('option', { name: /Forms: Radio Button/ }).click();
    await expectReferenceTheme(page, 'light', 'forms-radio-button--docs');
});
