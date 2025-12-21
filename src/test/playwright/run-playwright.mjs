/* global process, console */
import { spawn } from 'child_process';

function splitCsv(value) {
    if (!value) {
        return [];
    }
    return value
        .split(',')
        .map((s) => s.trim())
        .filter(Boolean);
}

function run(command, args, env) {
    return new Promise((resolve, reject) => {
        const child = spawn(command, args, {
            stdio: 'inherit',
            env: { ...process.env, ...env },
        });

        child.on('exit', (code) => {
            if (code === 0) {
                resolve();
            } else {
                reject(new Error(`${command} ${args.join(' ')} failed with exit code ${code}`));
            }
        });

        child.on('error', reject);
    });
}

async function runParallel(phaseEnv) {
    await run('npx', ['playwright', 'test', 'e2e', '--project=fast-tests', '--project=slow-tests', '--pass-with-no-tests'], phaseEnv);
}

async function runSequential(phaseEnv) {
    await run('npx', ['playwright', 'test', 'e2e', '--project=sequential-tests', '--workers', '1', '--pass-with-no-tests'], phaseEnv);
}

async function main() {
    const relevantGlobs = splitCsv(process.env.PLAYWRIGHT_RELEVANT_GLOBS);

    const baseEnv = {
        NODE_OPTIONS: '--max-old-space-size=8192',
    };

    if (relevantGlobs.length === 0) {
        await runParallel({ ...baseEnv, PLAYWRIGHT_TEST_TYPE: 'parallel' });
        await runSequential({ ...baseEnv, PLAYWRIGHT_TEST_TYPE: 'sequential' });
        await run('npx', ['junit-merge'], { ...baseEnv });
        await run('node', ['./merge-coverage-reports.mjs'], baseEnv);
        return;
    }

    const relevantCsv = relevantGlobs.join(',');

    // Relevant tests first
    await runParallel({
        ...baseEnv,
        PLAYWRIGHT_TEST_TYPE: 'relevant-parallel',
        PLAYWRIGHT_TEST_INCLUDE: relevantCsv,
        PLAYWRIGHT_TEST_IGNORE: '',
    });

    await runSequential({
        ...baseEnv,
        PLAYWRIGHT_TEST_TYPE: 'relevant-sequential',
        PLAYWRIGHT_TEST_INCLUDE: relevantCsv,
        PLAYWRIGHT_TEST_IGNORE: '',
    });

    // Then run remaining tests (exclude relevant globs)
    await runParallel({
        ...baseEnv,
        PLAYWRIGHT_TEST_TYPE: 'remaining-parallel',
        PLAYWRIGHT_TEST_INCLUDE: '',
        PLAYWRIGHT_TEST_IGNORE: relevantCsv,
    });

    await runSequential({
        ...baseEnv,
        PLAYWRIGHT_TEST_TYPE: 'remaining-sequential',
        PLAYWRIGHT_TEST_INCLUDE: '',
        PLAYWRIGHT_TEST_IGNORE: relevantCsv,
    });

    // Merge JUnit and coverage across all runs
    await run('node', ['./merge-junit-reports.mjs'], baseEnv);
    await run('node', ['./merge-coverage-reports.mjs'], baseEnv);
}

await main();
