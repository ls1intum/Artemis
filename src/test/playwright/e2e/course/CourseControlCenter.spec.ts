import { Locator, Page, expect } from '@playwright/test';
import { ArtemisCommands, test } from '../../support/fixtures';
import { admin } from '../../support/users';
import { SEED_COURSES } from '../../support/seedData';

const course = SEED_COURSES.general;

/**
 * Injects 'iris' and 'athena' into activeModuleFeatures, so the course overview renders both AI panels. The layout
 * under test is purely client-side: the Athena panel reads its switches from a core endpoint, and the Iris panel shows
 * itself as disabled when the Iris settings cannot be loaded.
 */
async function enableAiPanels(page: Page) {
    await page.route('**/management/info', async (route) => {
        const response = await route.fetch();
        const json = await response.json();
        json.activeModuleFeatures = [...(json.activeModuleFeatures ?? []), 'iris', 'athena'];
        await route.fulfill({ json });
    });
}

/**
 * Asserts that every Enabled / Disabled control in the panel, and every button in it, is as wide as its content. The
 * control clips its overflow, so a control that does not fit hides part of its buttons instead of pushing the page wider.
 */
async function expectTogglesFit(panel: Locator) {
    const groups = panel.getByRole('group');
    await expect(groups.first()).toBeVisible();
    for (const group of await groups.all()) {
        for (const element of [group, ...(await group.getByRole('button').all())]) {
            const { scrollWidth, clientWidth } = await element.evaluate((node) => ({ scrollWidth: node.scrollWidth, clientWidth: node.clientWidth }));
            expect(scrollWidth, 'the Enabled / Disabled buttons must fit into their control').toBeLessThanOrEqual(clientWidth);
        }
    }
}

async function openAiPanels(page: Page, login: ArtemisCommands['login'], width: number, height: number) {
    await page.setViewportSize({ width, height });
    await enableAiPanels(page);
    await login(admin, `/course-management/${course.id}`);

    const irisPanel = page.getByTestId('iris-panel');
    const athenaPanel = page.getByTestId('athena-panel');
    await expect(irisPanel).toBeVisible();
    await expect(athenaPanel).toBeVisible();
    return { irisPanel, athenaPanel, irisBox: (await irisPanel.boundingBox())!, athenaBox: (await athenaPanel.boundingBox())! };
}

test.describe('Course control center', { tag: '@fast' }, () => {
    // The production WAR registers a service worker that answers fetches before page.route sees them.
    test.use({ serviceWorkers: 'block' });

    for (const width of [400, 360]) {
        test(`Stacks the AI panels on a ${width}px phone so their switches stay readable`, async ({ page, login }) => {
            const { irisPanel, athenaPanel, irisBox, athenaBox } = await openAiPanels(page, login, width, 900);

            expect(athenaBox.y, 'the Athena panel sits below the Iris panel').toBeGreaterThanOrEqual(irisBox.y + irisBox.height);
            await expectTogglesFit(irisPanel);
            await expectTogglesFit(athenaPanel);
        });
    }

    test('Keeps the AI panels side by side on a wide screen', async ({ page, login }) => {
        const { irisPanel, athenaPanel, irisBox, athenaBox } = await openAiPanels(page, login, 1920, 1080);

        expect(athenaBox.x, 'the Athena panel sits next to the Iris panel').toBeGreaterThanOrEqual(irisBox.x + irisBox.width);
        await expectTogglesFit(irisPanel);
        await expectTogglesFit(athenaPanel);
    });
});
