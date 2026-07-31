import { expect, test } from '@playwright/test';
import type { Page } from '@playwright/test';

async function expectReferenceTheme(page: Page, theme: 'light' | 'dark', story = 'introduction--docs') {
    await expect(page).toHaveURL(new RegExp(`/developer/tum-ui/\\?path=/docs/${story}&globals=theme:${theme}$`));
    await expect(page.getByRole('button', { name: /Theme/ })).toContainText(`${theme} theme`);
    const documentation = page.frameLocator('#storybook-preview-iframe').locator('.sbdocs-wrapper');
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

test('connects the coding guidelines and component reference with the selected theme', async ({ page }) => {
    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto('./developer/intro');
    await page.getByRole('main').getByRole('link', { name: 'Client Guidelines' }).click();
    await expect(page).toHaveURL(/\/developer\/guidelines\/client$/);
    await expect(page.getByRole('complementary').getByRole('link', { name: 'TUM UI component reference' })).toBeVisible();

    await selectDocumentationTheme(page, 'dark');
    await page
        .getByRole('main')
        .getByRole('link', { name: /TUM UI component reference/ })
        .click();
    const darkReferenceBackground = await expectReferenceTheme(page, 'dark');
    await expect(page.getByRole('link', { name: 'TUM UI package guide' })).toHaveAttribute('href', '/developer/guidelines/tum-ui-kit');
    const introduction = page.frameLocator('#storybook-preview-iframe');
    await expect(introduction.getByRole('heading', { level: 1, name: 'Introduction' })).toBeVisible();

    await introduction.getByRole('link', { name: 'TUM UI package guide' }).click();
    await expect(page).toHaveURL(/\/developer\/guidelines\/tum-ui-kit$/);
    await expect(page.getByRole('heading', { level: 1, name: 'TUM UI package' })).toBeVisible();

    await selectDocumentationTheme(page, 'light');
    await page.getByRole('complementary').getByRole('link', { name: 'TUM UI component reference' }).click();
    const lightReferenceBackground = await expectReferenceTheme(page, 'light');
    expect(lightReferenceBackground.documentation).not.toBe(darkReferenceBackground.documentation);
    expect(lightReferenceBackground.manager).not.toBe(darkReferenceBackground.manager);
});

test('routes an indexed component name to its component reference', async ({ page, request }) => {
    const response = await request.get('./search-index-developer.json');
    await expect(response).toBeOK();
    const sections = (await response.json()) as { documents: { t: string; u: string; h: string }[] }[];
    const radioButton = sections.flatMap(({ documents }) => documents).find(({ t }) => t === 'Forms: Radio Button');
    expect(radioButton).toEqual(expect.objectContaining({ u: '/developer/tum-ui-reference', h: '#forms-radio-button' }));

    await page.emulateMedia({ colorScheme: 'light' });
    await page.goto(`.${radioButton!.u}${radioButton!.h}`);
    await expectReferenceTheme(page, 'light', 'forms-radio-button--docs');
});
