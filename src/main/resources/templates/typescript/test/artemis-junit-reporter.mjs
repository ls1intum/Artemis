import { writeFileSync } from 'node:fs';

const escapeXml = (value) =>
    String(value ?? '')
        .replace(/&/g, '&amp;')
        .replace(/</g, '&lt;')
        .replace(/>/g, '&gt;')
        .replace(/"/g, '&quot;')
        // eslint-disable-next-line no-control-regex
        .replace(/[\u0000-\u0008\u000B\u000C\u000E-\u001F]/g, '');

/**
 * Writes junit.xml in the format Artemis expects: class name and name of a test case are both
 * "<suite>_<nested suite>_<test title>", e.g. "behavior_BubbleSort_should_sort_correctly".
 */
export default class ArtemisJUnitReporter {
    constructor(options = {}) {
        this.outputFile = options.outputFile ?? 'junit.xml';
    }

    onTestRunEnd(testModules, unhandledErrors = []) {
        const suites = [];
        for (const testModule of testModules) {
            const cases = [];
            for (const test of testModule.children.allTests()) {
                const name = test.fullName.split(' > ').join('_');
                const result = test.result();
                const seconds = ((test.diagnostic()?.duration ?? 0) / 1000).toFixed(3);
                let body = '';
                if (result.state === 'failed') {
                    const message = result.errors?.[0]?.message ?? 'Test failed';
                    body = `<failure message="${escapeXml(message)}">${escapeXml(result.errors?.map((error) => error.stack ?? error.message).join('\n'))}</failure>`;
                } else if (result.state === 'skipped') {
                    body = '<skipped/>';
                }
                cases.push({ xml: `<testcase classname="${escapeXml(name)}" name="${escapeXml(name)}" time="${seconds}">${body}</testcase>`, state: result.state });
            }
            // A file that cannot be collected (syntax error, failing import) has no tests, but must not look like a pass.
            const moduleName = testModule.relativeModuleId ?? testModule.moduleId;
            const moduleErrors = testModule.errors();
            if (moduleErrors.length > 0) {
                cases.push({ xml: errorCase(moduleName, moduleErrors), state: 'error' });
            }
            suites.push(suite(moduleName, cases));
        }
        if (unhandledErrors.length > 0) {
            suites.push(suite('unhandled_errors', [{ xml: errorCase('unhandled_errors', unhandledErrors), state: 'error' }]));
        }
        writeFileSync(this.outputFile, `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>${suites.join('')}</testsuites>\n`);
    }
}

const errorCase = (name, errors) => {
    const message = errors[0]?.message ?? 'Error';
    const details = errors.map((error) => error.stack ?? error.message).join('\n');
    return `<testcase classname="${escapeXml(name)}" name="${escapeXml(name)}" time="0.000"><error message="${escapeXml(message)}">${escapeXml(details)}</error></testcase>`;
};

const suite = (name, cases) => {
    const count = (state) => cases.filter((testCase) => testCase.state === state).length;
    return `<testsuite name="${escapeXml(name)}" tests="${cases.length}" failures="${count('failed')}" skipped="${count('skipped')}" errors="${count('error')}">${cases.map((testCase) => testCase.xml).join('')}</testsuite>`;
};
