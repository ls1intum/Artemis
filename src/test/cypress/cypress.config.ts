import { defineConfig } from 'cypress';

export default defineConfig({
    fixturesFolder: 'fixtures',
    screenshotsFolder: 'screenshots',
    videosFolder: 'videos',
    video: false,
    screenshotOnRunFailure: false,
    viewportWidth: 1920,
    viewportHeight: 1080,
    defaultCommandTimeout: 20000,
    responseTimeout: 120000,
    reporter: 'junit',
    reporterOptions: {
        mochaFile: 'build/cypress/test-reports/test-results.[hash].xml',
        toConsole: true,
    },
    e2e: {
        setupNodeEvents(on) {
            on('task', {
                error(message: string) {
                    console.error('\x1b[31m', 'ERROR: ', message, '\x1b[0m');
                    return null;
                },
                warn(message: string) {
                    console.error('\x1b[33m', 'WARNING: ', message, '\x1b[0m');
                    return null;
                },
                log(message: string) {
                    console.log('\x1b[37m', 'LOG: ', message, '\x1b[0m');
                    return null;
                },
            });
        },
        specPattern: ['init/ImportUsers.cy.ts', 'e2e/**/*.cy.{js,jsx,ts,tsx}'],
        supportFile: 'support/index.ts',
        baseUrl: 'http://localhost:8080',
    },
});
