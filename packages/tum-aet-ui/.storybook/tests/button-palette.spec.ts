import { expect, test } from '@playwright/test';

const severities = ['primary', 'secondary', 'success', 'info', 'warn', 'danger', 'contrast'];
const variants = ['solid', 'outlined', 'text'];

function luminance(color: number[]): number {
    const channels = color.map((value) => {
        const channel = value / 255;
        return channel <= 0.04045 ? channel / 12.92 : ((channel + 0.055) / 1.055) ** 2.4;
    });
    return channels[0] * 0.2126 + channels[1] * 0.7152 + channels[2] * 0.0722;
}

for (const theme of ['light', 'dark']) {
    test(`keeps every button family readable in the ${theme} theme`, async ({ page }) => {
        await page.goto(`./iframe.html?id=actions-button--palette&viewMode=story&globals=theme:${theme}`);
        await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
        const first = page.getByRole('button', { name: 'primary solid', exact: true });
        await expect(first).toBeVisible();
        const fill = await first.evaluate((element) => {
            const probe = document.createElement('span');
            probe.style.backgroundColor = 'var(--tumaet-ui-button-primary-background)';
            element.append(probe);
            const color = getComputedStyle(probe).backgroundColor;
            probe.remove();
            return color;
        });
        await expect(first).toHaveCSS('background-color', fill);
        for (const variant of variants) {
            for (const severity of severities) {
                const button = page.getByRole('button', { name: `${severity} ${variant}`, exact: true });
                await expect(button).toBeVisible();
                for (const state of ['idle', 'hover', 'active']) {
                    if (state === 'idle') {
                        await page.mouse.move(0, 0);
                    } else {
                        await button.hover();
                    }
                    if (state === 'active') {
                        await page.mouse.down();
                    }
                    const colors = await button.evaluate((element) => {
                        const channels = (value: string) => (value.match(/[\d.]+/g) ?? []).map(Number);
                        const composite = (base: number[], overlay: number[]) => base.slice(0, 3).map((value, index) => value * (1 - overlay[3]) + overlay[index] * overlay[3]);
                        const style = getComputedStyle(element);
                        const probe = document.createElement('span');
                        probe.style.color = 'var(--tumaet-ui-content-background)';
                        element.append(probe);
                        const surface = channels(getComputedStyle(probe).color);
                        probe.remove();
                        let background = style.backgroundColor === 'rgba(0, 0, 0, 0)' ? surface : channels(style.backgroundColor);
                        let foreground = channels(style.color);
                        const shadowColor = style.boxShadow.match(/rgba?\([^)]+\)/)?.[0];
                        if (shadowColor && style.boxShadow.includes('inset')) {
                            const overlay = channels(shadowColor);
                            overlay[3] ??= 1;
                            background = composite(background, overlay);
                        }
                        // A full-button pseudo overlay covers glyphs as well as the fill; account for that when checking contrast.
                        const after = getComputedStyle(element, '::after');
                        if (after.content !== 'none') {
                            const overlay = channels(after.backgroundColor);
                            overlay[3] ??= 1;
                            background = composite(background, overlay);
                            foreground = composite(foreground, overlay);
                        }
                        return { foreground, background };
                    });
                    if (state === 'active') {
                        await page.mouse.up();
                    }
                    const values = [luminance(colors.foreground), luminance(colors.background)].sort((a, b) => b - a);
                    expect((values[0] + 0.05) / (values[1] + 0.05), `${severity} ${variant} ${state} text contrast`).toBeGreaterThanOrEqual(4.5);
                }
            }
        }
    });

    test(`allows button-only palette and focus overrides in the ${theme} theme`, async ({ page }) => {
        await page.goto(`./iframe.html?id=actions-button--palette&viewMode=story&globals=theme:${theme}`);
        const primary = page.getByRole('button', { name: 'primary solid', exact: true });
        const information = page.getByRole('button', { name: 'info solid', exact: true });
        await expect(primary).toBeVisible();
        await expect(page.locator('html')).toHaveAttribute('data-theme', theme);
        const informationFill = await information.evaluate((element) => {
            const probe = document.createElement('span');
            probe.style.backgroundColor = 'var(--tumaet-ui-button-info-background)';
            element.append(probe);
            const color = getComputedStyle(probe).backgroundColor;
            probe.remove();
            return color;
        });
        await expect(information).toHaveCSS('background-color', informationFill);
        const before = await information.evaluate((element) => ({
            background: getComputedStyle(element).backgroundColor,
            status: getComputedStyle(document.documentElement).getPropertyValue('--tumaet-ui-state-info'),
        }));
        await page.evaluate(() => {
            const root = document.documentElement;
            root.style.setProperty('--tumaet-ui-button-primary-background', '#123456');
            root.style.setProperty('--tumaet-ui-button-info-foreground', '#183047');
            root.style.setProperty('--tumaet-ui-button-focus-color', '#ae45b6');
        });
        await expect(primary).toHaveCSS('background-color', 'rgb(18, 52, 86)');
        await expect(page.getByRole('button', { name: 'info outlined', exact: true })).toHaveCSS('color', 'rgb(24, 48, 71)');
        await expect(page.getByRole('button', { name: 'info text', exact: true })).toHaveCSS('color', 'rgb(24, 48, 71)');
        expect(
            await information.evaluate((element) => ({
                background: getComputedStyle(element).backgroundColor,
                status: getComputedStyle(document.documentElement).getPropertyValue('--tumaet-ui-state-info'),
            })),
        ).toEqual(before);
        await page.keyboard.press('Tab');
        await expect(primary).toBeFocused();
        await expect(primary).toHaveCSS('outline-color', 'rgb(174, 69, 182)');
        await expect(primary).toHaveCSS('outline-style', 'solid');
        await expect(primary).toHaveCSS('outline-width', '2px');
    });
}
