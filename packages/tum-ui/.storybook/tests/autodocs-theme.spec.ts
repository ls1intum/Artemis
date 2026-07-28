import { expect, test } from '@playwright/test';
import type { Frame, Page } from '@playwright/test';
import { themes } from 'storybook/theming';

type ColorScheme = 'light' | 'dark';

async function findPreviewFrame(page: Page): Promise<Frame> {
    await expect.poll(() => page.frames().some((frame) => new URL(frame.url()).pathname.endsWith('/iframe.html'))).toBe(true);
    const frame = page.frames().find((candidate) => new URL(candidate.url()).pathname.endsWith('/iframe.html'));
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

async function resolvedColor(frame: Frame, color: string) {
    return frame.evaluate((value) => {
        const probe = document.createElement('span');
        probe.style.color = value;
        document.body.append(probe);
        const resolved = getComputedStyle(probe).color;
        probe.remove();
        return resolved;
    }, color);
}

async function expectTheme(frame: Frame, theme: ColorScheme) {
    const expectedDocsBackground = await resolvedColor(frame, themes[theme].appContentBg);
    await expect
        .poll(async () => {
            const appearance = await readAppearance(frame);
            return {
                componentTheme: appearance.docs.componentTheme,
                docsBackground: appearance.docs.background,
            };
        })
        .toEqual({
            componentTheme: theme,
            docsBackground: expectedDocsBackground,
        });
}

for (const operatingSystemTheme of ['light', 'dark'] satisfies ColorScheme[]) {
    test.describe(`${operatingSystemTheme} operating-system theme`, () => {
        test.use({ colorScheme: operatingSystemTheme });

        for (const toolbarTheme of ['light', 'dark'] satisfies ColorScheme[]) {
            test(`follows the ${toolbarTheme} toolbar theme in AutoDocs`, async ({ page }) => {
                await page.goto(`/?path=/docs/actions-button--docs&globals=theme:${toolbarTheme}`);
                const frame = await findPreviewFrame(page);
                await expectTheme(frame, toolbarTheme);
            });
        }

        if (operatingSystemTheme === 'light') {
            test('updates AutoDocs when the toolbar theme changes', async ({ page }) => {
                await page.goto('/?path=/docs/actions-button--docs&globals=theme:light');
                const frame = await findPreviewFrame(page);
                await expectTheme(frame, 'light');
                const lightComponent = (await readAppearance(frame)).component;

                const themeButton = page.getByRole('button', { name: /Theme/ });
                await expect(themeButton).toContainText('light theme');
                await themeButton.click();
                await expect(themeButton).toContainText('dark theme');
                await expectTheme(frame, 'dark');
                await expect.poll(async () => (await readAppearance(frame)).component).not.toEqual(lightComponent);

                await themeButton.click();
                await expect(themeButton).toContainText('light theme');
                await expectTheme(frame, 'light');
                await expect.poll(async () => (await readAppearance(frame)).component).toEqual(lightComponent);
            });
        }
    });
}
