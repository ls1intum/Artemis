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
        // We've imported your old cypress plugins here.
        // You may want to clean this up later by importing these.
        setupNodeEvents(on, config) {
            return require('./plugins/index.ts')(on, config);
        },
        specPattern: ['init/ImportUsers.cy.ts', 'e2e/**/*.cy.{js,jsx,ts,tsx}'],
        supportFile: 'support/index.ts',
        baseUrl: 'http://localhost:8080',
    },
});
