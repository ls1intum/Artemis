import { expect, test } from '@playwright/test';
import type { Frame, Page } from '@playwright/test';
import { themes } from 'storybook/theming';

type ColorScheme = 'light' | 'dark';

async function findPreviewFrame(page: Page): Promise<Frame> {
    await expect.poll(() => page.frames().some((frame) => frame.url().includes('/iframe.html'))).toBe(true);
    const frame = page.frames().find((candidate) => candidate.url().includes('/iframe.html'));
    if (!frame) {
        throw new Error('Storybook preview frame was not found');
    }
    return frame;
}

async function readAppearance(frame: Frame) {
    const heading = frame.getByRole('heading', { level: 1 }).first();
    const button = frame.getByRole('button', { name: 'Continue' }).first();
    const docs = frame.locator('.sbdocs-wrapper');
    await expect(heading).toBeVisible();
    await expect(button).toBeVisible();
    await expect(docs).toBeVisible();

    return {
        docs: await docs.evaluate((element) => ({
            background: getComputedStyle(element).backgroundColor,
            componentTheme: document.documentElement.dataset.theme,
        })),
        component: await button.evaluate((element) => ({
            background: getComputedStyle(element).backgroundColor,
            color: getComputedStyle(element).color,
        })),
    };
}

async function resolvedColor(page: Page, color: string) {
    return page.evaluate((value) => {
        const probe = document.createElement('span');
        probe.style.color = value;
        document.body.append(probe);
        const resolved = getComputedStyle(probe).color;
        probe.remove();
        return resolved;
    }, color);
}

async function expectTheme(page: Page, frame: Frame, theme: ColorScheme) {
    const expectedDocsBackground = await resolvedColor(page, themes[theme].appContentBg);
    const expectedManagerBackground = await resolvedColor(page, themes[theme].appBg);
    await expect
        .poll(async () => {
            const appearance = await readAppearance(frame);
            return {
                componentTheme: appearance.docs.componentTheme,
                docsBackground: appearance.docs.background,
                managerBackground: await page.evaluate(() => getComputedStyle(document.body).backgroundColor),
            };
        })
        .toEqual({
            componentTheme: theme,
            docsBackground: expectedDocsBackground,
            managerBackground: expectedManagerBackground,
        });
}

for (const operatingSystemTheme of ['light', 'dark'] satisfies ColorScheme[]) {
    test.describe(`${operatingSystemTheme} operating-system theme`, () => {
        test.use({ colorScheme: operatingSystemTheme });

        test('uses the operating-system theme on initial AutoDocs load', async ({ page }) => {
            await page.goto('./?path=/docs/actions-button--docs');
            const frame = await findPreviewFrame(page);
            await expectTheme(page, frame, operatingSystemTheme);
            await expect(page.getByRole('button', { name: /Theme/ })).toContainText(`${operatingSystemTheme} theme`);
        });

        if (operatingSystemTheme === 'light') {
            test('updates AutoDocs when the toolbar theme changes', async ({ page }) => {
                await page.goto('./?path=/docs/actions-button--docs&globals=theme:light');
                const frame = await findPreviewFrame(page);
                await expectTheme(page, frame, 'light');
                const lightComponent = (await readAppearance(frame)).component;

                const themeButton = page.getByRole('button', { name: /Theme/ });
                await expect(themeButton).toContainText('light theme');
                await themeButton.click();
                await expect(themeButton).toContainText('dark theme');
                await expectTheme(page, frame, 'dark');
                await expect.poll(async () => (await readAppearance(frame)).component).not.toEqual(lightComponent);

                await themeButton.click();
                await expect(themeButton).toContainText('light theme');
                await expectTheme(page, frame, 'light');
                await expect.poll(async () => (await readAppearance(frame)).component).toEqual(lightComponent);
            });
        }
    });
}
