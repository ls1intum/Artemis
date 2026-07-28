import { expect, test } from '@playwright/test';
import type { Frame, Page } from '@playwright/test';

type ColorScheme = 'light' | 'dark';

function rgbChannels(color: string) {
    const channels = color
        .match(/[\d.]+/g)
        ?.slice(0, 3)
        .map(Number);
    if (channels?.length !== 3) {
        throw new Error(`Expected an RGB color, received "${color}"`);
    }
    return channels;
}

function relativeLuminance(color: string) {
    const channels = rgbChannels(color).map((channel) => {
        const value = channel / 255;
        return value <= 0.04045 ? value / 12.92 : ((value + 0.055) / 1.055) ** 2.4;
    });
    return 0.2126 * channels[0] + 0.7152 * channels[1] + 0.0722 * channels[2];
}

function contrastRatio(foreground: string, background: string) {
    const lighter = Math.max(relativeLuminance(foreground), relativeLuminance(background));
    const darker = Math.min(relativeLuminance(foreground), relativeLuminance(background));
    return (lighter + 0.05) / (darker + 0.05);
}

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
    await expect(heading).toBeVisible();
    await expect(button).toBeVisible();

    return {
        docs: await heading.evaluate((element) => {
            let background = 'rgba(0, 0, 0, 0)';
            for (let current: Element | null = element; current; current = current.parentElement) {
                const candidate = getComputedStyle(current).backgroundColor;
                if (candidate !== 'rgba(0, 0, 0, 0)' && candidate !== 'transparent') {
                    background = candidate;
                    break;
                }
            }
            return {
                background,
                color: getComputedStyle(element).color,
                componentTheme: document.documentElement.dataset.theme,
            };
        }),
        component: await button.evaluate((element) => ({
            background: getComputedStyle(element).backgroundColor,
            color: getComputedStyle(element).color,
        })),
    };
}

async function expectTheme(frame: Frame, theme: ColorScheme) {
    await expect
        .poll(async () => {
            const appearance = await readAppearance(frame);
            const docsLuminance = relativeLuminance(appearance.docs.background);
            return {
                componentTheme: appearance.docs.componentTheme,
                docsReadable: contrastRatio(appearance.docs.color, appearance.docs.background) >= 4.5,
                componentReadable: contrastRatio(appearance.component.color, appearance.component.background) >= 4.5,
                docsSurfaceMatches: theme === 'light' ? docsLuminance > 0.8 : docsLuminance < 0.2,
            };
        })
        .toEqual({
            componentTheme: theme,
            docsReadable: true,
            componentReadable: true,
            docsSurfaceMatches: true,
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

                const themeButton = page.getByRole('button', { name: /Theme/ });
                await expect(themeButton).toContainText('light theme');
                await themeButton.click();
                await expect(themeButton).toContainText('dark theme');
                await expectTheme(frame, 'dark');

                await themeButton.click();
                await expect(themeButton).toContainText('light theme');
                await expectTheme(frame, 'light');
            });
        }
    });
}
