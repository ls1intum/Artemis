import { Page, test as baseTest, expect } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';

const test = baseTest.extend<{
    autoTestFixture: string;
}>({
    autoTestFixture: [
        async ({ page }: { page: Page }, use: (fixture: string) => Promise<void>) => {
            await Promise.all([
                page.coverage.startJSCoverage({
                    resetOnNavigation: false,
                }),
            ]);

            await use('autoTestFixture');

            const jsCoverage = await page.coverage.stopJSCoverage();
            await addCoverageReport(jsCoverage, test.info());
        },
        {
            scope: 'test',
            auto: true,
        },
    ],
});

export { test, expect };
