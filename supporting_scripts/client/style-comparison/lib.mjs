// Shared helpers of the style comparison scripts. See README.md.
import { mkdirSync } from 'node:fs';
import { dirname, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

export const repositoryRoot = resolve(dirname(fileURLToPath(import.meta.url)), '../../..');

/** The computed properties that a capture records for every element. */
export const PROPERTIES = [
    'display',
    'position',
    'margin-top',
    'margin-right',
    'margin-bottom',
    'margin-left',
    'padding-top',
    'padding-right',
    'padding-bottom',
    'padding-left',
    'font-size',
    'font-weight',
    'line-height',
    'font-family',
    'color',
    'background-color',
    'text-decoration-line',
    'text-align',
    'white-space',
    'border-top-width',
    'border-right-width',
    'border-bottom-width',
    'border-left-width',
    'border-top-color',
    'border-right-color',
    'border-bottom-color',
    'border-left-color',
    'border-top-style',
    'border-top-left-radius',
    'box-shadow',
    'row-gap',
    'column-gap',
    'flex-direction',
    'flex-wrap',
    'align-items',
    'justify-content',
    'overflow-x',
    'overflow-y',
    'opacity',
    'visibility',
    'vertical-align',
    'min-width',
    'cursor',
    'list-style-type',
    'width',
    'height',
];

/** Prints the usage and ends the script if it was called with `--help` or without a required argument. */
export function usage(text, missing) {
    if (missing) {
        console.error(text);
        process.exit(1);
    }
}

/** Reads `--name value` and `--flag` options; everything else is a positional argument. */
export function parseArguments(argv, defaults = {}) {
    const options = { ...defaults };
    const positional = [];
    for (let index = 0; index < argv.length; index++) {
        const argument = argv[index];
        if (!argument.startsWith('--')) {
            positional.push(argument);
            continue;
        }
        const name = argument.slice(2);
        const next = argv[index + 1];
        if (next === undefined || next.startsWith('--')) {
            options[name] = true;
        } else {
            options[name] = next;
            index++;
        }
    }
    return { options, positional };
}

export function outputDirectory(options, ...segments) {
    const directory = resolve(options.out ?? resolve(repositoryRoot, 'build/style-comparison'), ...segments);
    mkdirSync(directory, { recursive: true });
    return directory;
}

/** The users of the E2E setup; override them with the environment variables for another environment. */
export const users = {
    admin: { username: process.env.STYLE_COMPARISON_ADMIN_USERNAME ?? 'artemis_admin', password: process.env.STYLE_COMPARISON_ADMIN_PASSWORD ?? 'artemis_admin' },
    student: { username: process.env.STYLE_COMPARISON_STUDENT_USERNAME ?? 'artemis_test_user_1', password: process.env.STYLE_COMPARISON_STUDENT_PASSWORD ?? 'artemis_test_user_1' },
};

export async function openPage() {
    const { chromium } = await import('@playwright/test');
    const browser = await chromium.launch();
    const context = await browser.newContext({ viewport: { width: 1440, height: 1000 }, locale: 'en-US' });
    const page = await context.newPage();
    return { browser, page };
}

/** Logs in through the REST API; the session cookie ends up in the browser context of the page. */
export async function login(page, base, user) {
    await page.context().clearCookies();
    const response = await page.request.post(`${base}/api/core/public/authenticate`, { data: { ...user, rememberMe: true } });
    if (!response.ok()) {
        throw new Error(`Login of ${user.username} failed with status ${response.status()}`);
    }
}

/** Waits until the page has loaded its data and finished the rendering that follows. */
export async function settle(page, milliseconds = 1300) {
    await page.waitForLoadState('networkidle', { timeout: 15000 }).catch(() => {});
    await page.waitForTimeout(milliseconds);
}

/**
 * Removes what differs from run to run without a change of the styles: the dialogs that ask to set up a passkey or to
 * accept cookies, alerts, the ribbon of the test server, the text cursor, animations and transitions.
 */
export async function calm(page) {
    await page
        .getByRole('button', { name: /Remind Me in 30 Days|Set Up Later/i })
        .first()
        .click({ timeout: 600 })
        .catch(() => {});
    await page
        .getByRole('button', { name: /^Accept$/i })
        .first()
        .click({ timeout: 300 })
        .catch(() => {});
    await page
        .addStyleTag({
            content:
                'jhi-alert-overlay, jhi-page-ribbon { display: none !important } * { caret-color: transparent !important; animation: none !important; transition: none !important }',
        })
        .catch(() => {});
}

/**
 * Runs in the page: the computed values of the properties for every element below the root, keyed by the position of
 * the element in the tree. The contents of an SVG and of the Monaco editor are skipped; they are not styled by the client.
 */
export function collectStyles({ properties, rootSelector }) {
    const root = document.querySelector(rootSelector);
    const elements = [];
    if (!root) {
        return { properties, elements };
    }
    const pathOf = (element) => {
        const segments = [];
        for (let current = element; current && current !== root; current = current.parentElement) {
            const parent = current.parentElement;
            segments.unshift(`${current.tagName.toLowerCase()}:${parent ? [...parent.children].indexOf(current) : 0}`);
        }
        return segments.join('/');
    };
    for (const element of root.querySelectorAll('*')) {
        if (element.closest('svg') && element.tagName.toLowerCase() !== 'svg') continue;
        if (element.closest('.monaco-editor .view-lines, .monaco-editor .margin, .minimap')) continue;
        const style = getComputedStyle(element);
        if (style.display === 'none' && !element.className) continue;
        elements.push([pathOf(element), typeof element.className === 'string' ? element.className : '', properties.map((property) => style.getPropertyValue(property))]);
    }
    return { properties, elements };
}
