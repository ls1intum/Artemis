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

    onTestRunEnd(testModules) {
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
            const count = (state) => cases.filter((testCase) => testCase.state === state).length;
            suites.push(
                `<testsuite name="${escapeXml(testModule.relativeModuleId ?? testModule.moduleId)}" tests="${cases.length}" failures="${count('failed')}" skipped="${count('skipped')}" errors="0">${cases.map((testCase) => testCase.xml).join('')}</testsuite>`,
            );
        }
        writeFileSync(this.outputFile, `<?xml version="1.0" encoding="UTF-8"?>\n<testsuites>${suites.join('')}</testsuites>\n`);
    }
}
