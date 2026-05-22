import { CDPSession, Page, test as baseTest, expect } from '@playwright/test';
import { addCoverageReport } from 'monocart-reporter';

const test = baseTest.extend<{
    autoTestFixture: string;
    virtualAuthenticator: CDPSession;
}>({
    autoTestFixture: [
        async ({ page }: { page: Page }, use: (fixture: string) => Promise<void>) => {
            // Hide the notification popup overlay that can block clicks in e2e tests.
            // This runs before every page load to ensure the overlay never intercepts pointer events.
            await page.addInitScript(() => {
                const injectStyle = () => {
                    const style = document.createElement('style');
                    style.textContent = 'jhi-course-notification-popup-overlay { display: none !important; }';
                    document.head.appendChild(style);
                };
                if (document.head) {
                    injectStyle();
                } else {
                    document.addEventListener('DOMContentLoaded', injectStyle);
                }
            });

            const coverageEnabled = process.env.PLAYWRIGHT_COVERAGE !== 'off';

            if (coverageEnabled) {
                await page.coverage.startJSCoverage({
                    resetOnNavigation: false,
                });
            }

            await use('autoTestFixture');

            if (coverageEnabled) {
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
            }
        },
        {
            scope: 'test',
            auto: true,
        },
    ],
    virtualAuthenticator: [
        async ({ page }, use) => {
            const cdpSession = await page.context().newCDPSession(page);
            await cdpSession.send('WebAuthn.enable', { enableUI: false });
            await cdpSession.send('WebAuthn.addVirtualAuthenticator', {
                options: {
                    protocol: 'ctap2',
                    transport: 'internal',
                    hasResidentKey: true,
                    hasUserVerification: true,
                    isUserVerified: true,
                    automaticPresenceSimulation: true,
                },
            });
            await use(cdpSession);
            await cdpSession.send('WebAuthn.disable');
        },
        { scope: 'test', auto: true },
    ],
});

export { test, expect };
