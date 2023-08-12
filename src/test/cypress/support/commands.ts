// ***********************************************
// This example commands.js shows you how to
// create various custom commands and overwrite
// existing commands.
//
// For more comprehensive examples of custom
// commands please read more here:
// https://on.cypress.io/custom-commands
// ***********************************************
//
//
// -- This is a parent command --
// Cypress.Commands.add('login', (email, password) => { ... })
//
//
// -- This is a child command --
// Cypress.Commands.add('drag', { prevSubject: 'element'}, (subject, options) => { ... })
//
//
// -- This is a dual command --
// Cypress.Commands.add('dismiss', { prevSubject: 'optional'}, (subject, options) => { ... })
//
//
// -- This will overwrite an existing command --
// Cypress.Commands.overwrite('visit', (originalFn, url, options) => { ... })

import { BASE_API, POST } from './constants';
import { CypressCredentials } from './users';

export {};

declare global {
    // eslint-disable-next-line @typescript-eslint/no-namespace
    namespace Cypress {
        interface Chainable {
            login(credentials: CypressCredentials, url?: string): any;
            loginWithGUI(credentials: CypressCredentials): any;
            getSettled(selector: string, options?: any): Chainable<unknown>;
            reloadUntilFound(selector: string, interval?: number, timeout?: number): Chainable<undefined>;
            formRequest(url: string, method: string, formData: FormData): Chainable<any>;
        }
    }
}

/**
 * Logs in using API
 * */
Cypress.Commands.add('login', (credentials: CypressCredentials, url) => {
    const username = credentials.username;
    const password = credentials.password;

    cy.session(
        username,
        () => {
            // IMPORTANT: The "log" and "failOnStatusCode" fields need to be set to false to prevent leakage of the credentials via the Cypress Dashboard!
            // log = false does not prevent cypress to log the request if it failed, so failOnStatusCode also needs to be set to false, so that the request is never logged.
            // We still want to the test to fail if the authentication is unsuccessful, so we expect the status code in the then block. This only logs the status code, so it is safe.
            cy.request({
                url: BASE_API + 'public/authenticate',
                method: POST,
                followRedirect: true,
                body: {
                    username,
                    password,
                    rememberMe: true,
                },
                log: false,
                failOnStatusCode: false,
            }).then((response) => {
                expect(response.status).to.equal(200);
            });
        },
        {
            validate: () => {
                cy.getCookie('jwt', { log: false }).should('exist');
            },
            cacheAcrossSpecs: true,
        },
    );
    if (url) {
        cy.visit(url);
    }
});

/** recursively gets an element, returning only after it's determined to be attached to the DOM for good
 *  this prevents the "Element is detached from DOM" issue in some cases
 */
Cypress.Commands.add('getSettled', (selector: any, opts: { retries?: number; delay?: number } = {}) => {
    const retries = opts.retries || 3;
    const delay = opts.delay || 100;

    const isAttached = (resolve: any, count = 0) => {
        const el = Cypress.$(selector);

        // is element attached to the DOM?
        count = Cypress.dom.isAttached(el) ? count + 1 : 0;

        // hit our base case, return the element
        if (count >= retries) {
            return resolve(el);
        }

        // retry after a bit of a delay
        setTimeout(() => isAttached(resolve, count), delay);
    };

    // wrap, so we can chain cypress commands off the result
    return cy.wrap(null).then(() => {
        return new Cypress.Promise((resolve) => {
            return isAttached(resolve, 0);
        }).then((el) => {
            return cy.wrap(el);
        });
    });
});

/**
 * Periodically refreshes the page until an element with the specified selector is found. The command fails if the time exceeds the timeout.
 */
Cypress.Commands.add('reloadUntilFound', (selector: string, interval = 2000, timeout = 20000) => {
    return cy.waitUntil(
        () => {
            const found = Cypress.$(selector).length > 0;
            if (!found) {
                cy.reload();
            }
            return found;
        },
        {
            interval,
            timeout,
            errorMsg: `Timed out finding an element matching the "${selector}" selector`,
        },
    );
});

Cypress.Commands.add('formRequest', (url: string, method: string, formData: FormData) => {
    return cy
        .intercept(method, url)
        .as('formRequest')
        .window()
        .then((win) => {
            const xhr = new win.XMLHttpRequest();
            xhr.open(method, url);
            const token = localStorage.getItem('authTokenKey')?.replace(/"/g, '');
            if (token) {
                const authHeader = 'Bearer ' + token;
                xhr.setRequestHeader('Authorization', authHeader);
            }
            xhr.send(formData);
        })
        .wait('@formRequest')
        .then((xhr: any) => {
            return cy.wrap({ status: xhr.status, body: xhr.response.body });
        });
});
