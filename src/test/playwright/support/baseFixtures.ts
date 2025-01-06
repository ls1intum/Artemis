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
                page.coverage.startCSSCoverage({
                    resetOnNavigation: false,
                }),
            ]);

            await use('autoTestFixture');

            const [jsCoverage, cssCoverage] = await Promise.all([page.coverage.stopJSCoverage(), page.coverage.stopCSSCoverage()]);

            const coverageList = [...jsCoverage, ...cssCoverage];

            await addCoverageReport(coverageList, test.info());
        },
        {
            scope: 'test',
            auto: true,
        },
    ],
});

export { test, expect };
