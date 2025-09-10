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

            if (jsCoverage && jsCoverage.length > 0) {
                // On CI, modify URLs of coverage entries to access sources
                // directly from the "artemis-app" container.
                // Because files served via "nginx" require HTTPS certificates
                // for accessing them, and it's not clear how we can make
                // "monocart-reporter" handle that while generating a coverage report.
                if (process.env.CI) {
                    for (const entry of jsCoverage) {
                        entry.url = entry.url.replace(process.env.BASE_URL!, 'http://artemis-app:8080');
                    }
                }
                await addCoverageReport(jsCoverage, test.info());
            }
        },
        {
            scope: 'test',
            auto: true,
        },
    ],
});

export { test, expect };
