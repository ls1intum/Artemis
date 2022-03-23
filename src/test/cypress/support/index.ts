// ***********************************************************
// This example support/index.js is processed and
// loaded automatically before your test files.
//
// This is a great place to put global configuration and
// behavior that modifies Cypress.
//
// You can change the location of this file or turn off
// automatically serving support files with the
// 'supportFile' configuration option.
//
// You can read more here:
// https://on.cypress.io/configuration
// ***********************************************************

// Import commands.js using ES2015 syntax:
import './commands';
// https://github.com/4teamwork/cypress-drag-drop#options adds .drag and .move commands
import '@4tw/cypress-drag-drop';
// Imports utility functions
import './utils';
// Imports file upload capabilities https://github.com/abramenal/cypress-file-uploady
import 'cypress-file-upload';
// Imports cy.waitUntil https://github.com/NoriSte/cypress-wait-until
import 'cypress-wait-until';

// Alternatively you can use CommonJS syntax:
// require('./commands')

/**
 * We register hooks on the console.error and console.warn methods and forward their content to the process console to allow better debugging with cypress:run.
 */
/*eslint-disable */
Cypress.on('window:before:load', (win) => {
    cy.stub(win.console, 'error').callsFake((msg) => {
        cy.now('task', 'error', msg);
    });

    cy.stub(win.console, 'warn').callsFake((msg) => {
        cy.now('task', 'warn', msg);
    });
});

/**
 * We have to disable all service workers because the test will fail with a security exception and translations will also not be resolved properly otherwise.
 * For some reason this does not work when I add it to the hook above.
 */
Cypress.on('window:before:load', (win) => {
    delete win.navigator.__proto__.serviceWorker;
});

/**
 * On development environments (i.e. local environments) sentry is disabled and produces error messages in the console that cause cypress to fail.
 * This call tells cypress to ignore errors related to sentry
 */
Cypress.on('uncaught:exception', (err, runnable) => {
    return err.name === 'TypeError' && err?.stack?.includes('sentry');
});
/*eslint-enable */
