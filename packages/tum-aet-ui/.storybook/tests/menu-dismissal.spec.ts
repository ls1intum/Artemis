import { expect, test } from '@playwright/test';

test.beforeEach(async ({ page }) => {
    await page.goto('./iframe.html?id=navigation-menu--dismissal&viewMode=story');
    await expect(page.getByRole('button', { name: 'Course actions' })).toBeVisible();
});

test('closes the menu on a click outside it', async ({ page }) => {
    const trigger = page.getByRole('button', { name: 'Course actions' });
    await trigger.click();
    await expect(page.getByRole('menu')).toBeVisible();

    await page.mouse.click(5, 5);

    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(trigger).toHaveAttribute('aria-expanded', 'false');
});

test('closes the menu on Tab and moves on to the control after the trigger', async ({ page }) => {
    const trigger = page.getByRole('button', { name: 'Course actions' });
    await trigger.focus();
    await page.keyboard.press('Enter');
    await expect(page.getByRole('menuitem', { name: 'Add students' })).toBeFocused();

    await page.keyboard.press('Tab');

    await expect(page.getByRole('menu')).toHaveCount(0);
    await expect(page.getByRole('button', { name: 'Next control' })).toBeFocused();
});
