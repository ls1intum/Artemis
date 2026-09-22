import { expect } from '@playwright/test';
import { test } from '../../support/fixtures';
import { studentOne } from '../../support/users';

test.describe('Global search keyboard focus', { tag: '@fast' }, () => {
    for (const forcedColors of ['none', 'active'] as const) {
        test(`keeps the highlighted combobox visibly focused with forced colors ${forcedColors}`, async ({ page, login }) => {
            await page.emulateMedia({ forcedColors });
            await page.route('**/management/info', async (route) => {
                const response = await route.fetch();
                const info = await response.json();
                info.features = [...new Set([...(info.features ?? []), 'GlobalSearch'])];
                await route.fulfill({ json: info });
            });
            await page.route('**/api/search?*', (route) => route.fulfill({ json: [] }));
            await login(studentOne, '/courses');
            await expect(page.locator('jhi-global-search-navbar').getByRole('button')).toBeVisible();

            await page.keyboard.press('ControlOrMeta+k');
            const input = page.getByRole('dialog').getByRole('combobox');
            await expect(input).toBeFocused();
            await expect(input).toHaveCSS('outline-style', 'solid');
            await expect(input).toHaveCSS('outline-width', '2px');
            await expect(input).toHaveCSS('outline-offset', '2px');
            expect(await input.evaluate((element) => element.matches(':focus-visible'))).toBe(true);

            await input.pressSequentially('type:lec');
            await expect(input).toHaveValue('type:lec');
            await expect(input).toBeFocused();
            const highlight = page.getByTestId('global-search-highlight');
            await expect(highlight).toHaveAttribute('aria-hidden', 'true');
            await expect(highlight).toHaveCSS('pointer-events', 'none');
            if (forcedColors === 'active') {
                await expect(highlight).toBeHidden();
                await expect(input).not.toHaveCSS('color', 'rgba(0, 0, 0, 0)');
            } else {
                await expect(highlight).toBeVisible();
                await expect(highlight).toHaveText('type:lec');
            }
            await expect(input).toHaveCSS('outline-width', '2px');
        });
    }
});
