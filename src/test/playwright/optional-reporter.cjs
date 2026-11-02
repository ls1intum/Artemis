// Coverage/dashboard reporting is advisory. Only errors caught inside this adapter are reporter failures;
// process exits (including OOMs) remain execution failures in run-tests.sh.
const process = require('node:process');
const console = require('node:console');
const { appendFileSync } = require('node:fs');

class OptionalReporter {
    reporter;
    failed = false;

    constructor(options) {
        try {
            const Reporter = require('monocart-reporter');
            this.reporter = new (Reporter.default || Reporter)(options);
        } catch {
            this.failure('constructor');
        }
    }

    printsToStdio() {
        return false;
    }

    failure(hook) {
        this.failed = true;
        const message = `Optional coverage reporter failed in ${hook}; test status is unchanged.\n`;
        console.warn(`WARNING: ${message.trim()}`);
        if (process.env.PLAYWRIGHT_OPTIONAL_REPORTER_MARKER) appendFileSync(process.env.PLAYWRIGHT_OPTIONAL_REPORTER_MARKER, message);
    }

    invoke(hook, args) {
        if (this.failed) return;
        try {
            const result = this.reporter?.[hook]?.(...args);
            if (result?.then) return result.catch(() => this.failure(hook));
        } catch {
            this.failure(hook);
        }
    }

    async onEnd(result) {
        await this.invoke('onEnd', [result]);
    }

    async onExit() {
        await this.invoke('onExit', []);
    }
}

for (const hook of ['onConfigure', 'onBegin', 'onTestBegin', 'onStepBegin', 'onStepEnd', 'onTestEnd', 'onError', 'onStdOut', 'onStdErr']) {
    OptionalReporter.prototype[hook] = function (...args) {
        this.invoke(hook, args);
    };
}

module.exports = OptionalReporter;
