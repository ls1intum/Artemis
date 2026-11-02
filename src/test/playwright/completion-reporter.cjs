// Keep execution evidence independent of JUnit and optional coverage reporting.
const process = require('node:process');
const console = require('node:console');
const { readFileSync, writeFileSync } = require('node:fs');

class CompletionReporter {
    suite;
    completed = new Set();
    errors = 0;

    printsToStdio() {
        return false;
    }

    onBegin(_config, suite) {
        this.suite = suite;
    }

    onTestEnd(test, result) {
        // Playwright reports a terminated worker as a per-test failure, without calling onError.
        // Keep that infrastructure error distinct from an assertion failure that Helios may exonerate.
        if (result.errors?.some((error) => /^Error: worker process exited unexpectedly \(/.test(error.message ?? '')) || (result.status === 'failed' && result.workerIndex < 0)) {
            this.errors++;
        }
        if (result.status !== 'interrupted') this.completed.add(test.id);
    }

    onError() {
        this.errors++;
    }

    onEnd(result) {
        if (!process.env.PLAYWRIGHT_COMPLETION_FILE) return;
        const tests = this.suite?.allTests();
        writeFileSync(
            process.env.PLAYWRIGHT_COMPLETION_FILE,
            JSON.stringify({
                selected: tests?.length ?? null,
                completed: this.completed.size,
                status: result.status,
                errors: this.errors,
                unexpected: tests?.filter((test) => test.outcome() === 'unexpected').length ?? 0,
            }),
        );
    }
}

module.exports = CompletionReporter;

// Invoked by run-tests.sh. Missing, malformed or incomplete evidence must fail closed.
if (require.main === module) {
    try {
        const [mode, collectionFile, executionFile, processExitCode] = process.argv.slice(2);
        const collection = JSON.parse(readFileSync(collectionFile, 'utf8'));
        if (!Number.isInteger(collection.selected) || collection.selected < 0 || collection.status !== 'passed' || collection.errors !== 0) {
            throw new Error('Test collection did not complete successfully');
        }
        if (mode === 'collection') {
            console.log(collection.selected);
        } else if (mode === 'execution') {
            const execution = JSON.parse(readFileSync(executionFile, 'utf8'));
            if (
                collection.selected === 0 ||
                execution.selected !== collection.selected ||
                execution.completed !== collection.selected ||
                execution.errors !== 0 ||
                !Number.isInteger(execution.unexpected) ||
                execution.unexpected < 0
            ) {
                throw new Error('Test execution did not complete every selected test without infrastructure errors');
            }
            if (execution.status === 'passed' && execution.unexpected === 0 && processExitCode === '0') {
                process.exitCode = 0;
            } else if (execution.status === 'failed' && execution.unexpected > 0 && processExitCode === '1') {
                // A completed assertion failure may be enriched with flakiness history, independently of infrastructure.
                process.exitCode = 1;
            } else {
                throw new Error('Playwright status or process exit does not match a completed test verdict');
            }
        } else {
            throw new Error('Unknown completion verification mode');
        }
    } catch (error) {
        console.error(`ERROR: Cannot verify Playwright completion: ${error.message}`);
        process.exitCode = process.argv[2] === 'execution' ? 2 : 1;
    }
}
