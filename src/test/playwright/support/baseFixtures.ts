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
            if (process.env.CI) {
                console.log('Adjusting coverage entry urls...');
                console.log(`Source urls before: ${jsCoverage.map((entry) => entry.url)}`);
                for (const entry of jsCoverage) {
                    entry.url = entry.url.replace(process.env.BASE_URL!, 'http://artemis-app:8080');
                }
                console.log(`Source urls after: ${jsCoverage.map((entry) => entry.url)}`);
            }
            await addCoverageReport(jsCoverage, test.info());
        },
        {
            scope: 'test',
            auto: true,
        },
    ],
});

export { test, expect };
