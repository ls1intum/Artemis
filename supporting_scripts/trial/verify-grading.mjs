import { randomUUID } from 'node:crypto';
import { readFile } from 'node:fs/promises';
import { setTimeout as sleep } from 'node:timers/promises';
import { parseArgs } from 'node:util';
import { HttpClient } from '../course-scripts/setup-course/lib/http-client.mjs';

const archive = new URL('../../src/test/resources/test-data/import-from-file/valid-import-c.zip', import.meta.url);
const exercises = '/api/programming/programming-exercises';
const exerciseTitle = 'Trial automatic grading';
const buildHelp = 'Inspect Server Administration → Build Agents and the build queue. Check the Git clone URL, Docker socket access and access to the FACT image registry.';

export function trialOrigin(port) {
    if (!/^\d+$/.test(port) || Number(port) < 1 || Number(port) > 65535) {
        throw new Error('ARTEMIS_TRIAL_PORT must be a port between 1 and 65535.');
    }
    return `http://127.0.0.1:${port}`;
}

export function newCommit(before, after) {
    const known = new Set(before.map((entry) => entry.hash));
    const added = after.filter((entry) => !known.has(entry.hash));
    if (added.length !== 1 || !/^[a-f0-9]{40}$/.test(added[0].hash)) {
        throw new Error(`Expected one new Git commit, found ${added.length}.`);
    }
    return added[0].hash;
}

export function checkGrade(result, commit) {
    if (result?.submission?.commitHash !== commit || result.assessmentType !== 'AUTOMATIC' || !result.completionDate) {
        return;
    }
    if (result.submission.buildFailed || result.score !== 100 || !(result.testCaseCount > 0) || result.passedTestCaseCount !== result.testCaseCount) {
        throw new Error(`Result ${result.id} scored ${result.score}% with ${result.passedTestCaseCount}/${result.testCaseCount} tests passed. Open its build log and feedback.`);
    }
    return result;
}

export function verifyRestoredGrade(history, grade) {
    const hash = grade?.submission?.commitHash;
    if (!hash || !history.some((entry) => entry.hash === hash) || !checkGrade(grade, hash)) {
        throw new Error('The previous automatic grade or its commit is missing from the restored participation.');
    }
    return hash;
}

async function stage(label, hint, action) {
    console.log(label);
    try {
        return await action();
    } catch (error) {
        const detail = error.response?.data;
        const reason = typeof detail === 'string' ? detail : detail?.detail;
        throw new Error(`${label} failed. ${error.message}${reason ? ' ' + reason.slice(0, 300) : ''} ${hint}`);
    }
}

async function waitFor(read) {
    const deadline = Date.now() + 600_000;
    do {
        const value = await read();
        if (value) return value;
        await sleep(3000);
    } while (Date.now() < deadline);
    throw new Error('No matching result after 10 minutes.');
}

function multipart(name, json) {
    const form = new FormData();
    form.append(name, new Blob([JSON.stringify(json)], { type: 'application/json' }), name + '.json');
    return form;
}

async function run() {
    const { values } = parseArgs({ options: { 'exercise-id': { type: 'string' } } });
    const existingId = values['exercise-id'];
    if (existingId !== undefined && !/^[1-9]\d*$/.test(existingId)) {
        throw new Error('--exercise-id must be a positive integer from a previous trial run.');
    }
    const origin = trialOrigin(process.env.ARTEMIS_TRIAL_PORT ?? '8080');
    const client = new HttpClient(origin);
    const request = async (method, path, body, headers) => {
        const response = await client.request(method, path, {
            body,
            headers,
            contentType: body instanceof FormData ? 'multipart' : undefined,
            // On first sign-in, Spring may still be initializing beans after readiness reports UP.
            signal: AbortSignal.timeout(method === 'POST' ? 180_000 : 60_000),
        });
        return response.data;
    };

    await stage(`Sign in to ${origin}`, 'Check Compose readiness and the local trial admin credentials.', () =>
        request('POST', '/api/core/public/authenticate', {
            username: process.env.ARTEMIS_TRIAL_USERNAME ?? 'artemis_admin',
            password: process.env.ARTEMIS_TRIAL_PASSWORD ?? 'artemis_admin',
            rememberMe: false,
        }),
    );

    let exercise;
    let participation;
    if (existingId) {
        exercise = await stage(`Find restored exercise ${existingId}`, 'Use the exercise ID printed by the original trial run.', async () => {
            const restored = await request('GET', `${exercises}/${existingId}/with-participations`);
            if (restored.title !== exerciseTitle || restored.shortName !== 'grade' || restored.projectType !== 'FACT') {
                throw new Error('This exercise does not match the trial grading fixture.');
            }
            if (restored.studentParticipations?.length !== 1) {
                throw new Error('The original account must have one restored student participation.');
            }
            return restored;
        });
        [participation] = exercise.studentParticipations;
    } else {
        const shortName = 'trial' + randomUUID().slice(0, 8);
        const course = await stage(`Create test course ${shortName}`, 'Check that the account has administrator permissions.', () =>
            request(
                'POST',
                '/api/admin/courses',
                multipart('course', {
                    title: `Grading trial ${shortName}`,
                    shortName,
                    testCourse: true,
                    semester: 'Trial',
                    accuracyOfScores: 1,
                    startDate: new Date(Date.now() - 86_400_000).toISOString(),
                    endDate: new Date(Date.now() + 365 * 86_400_000).toISOString(),
                    courseInformationSharingConfiguration: 'DISABLED',
                    maxComplaints: 0,
                    maxTeamComplaints: 0,
                }),
            ),
        );
        exercise = await stage(`Import C archive into course ${course.id}`, 'Check the fixture in this checkout and the Artemis application logs.', async () => {
            const form = multipart('programmingExercise', {
                course: { id: course.id },
                type: 'programming',
                title: exerciseTitle,
                shortName: 'grade',
                channelName: 'trial-grading',
                programmingLanguage: 'C',
                projectType: 'FACT',
                mode: 'INDIVIDUAL',
                assessmentType: 'AUTOMATIC',
                problemStatement: 'Compute and print x squared plus five times y minus four.',
                maxPoints: 2,
                allowOnlineEditor: true,
                allowOfflineIde: true,
                staticCodeAnalysisEnabled: false,
                buildConfig: { branch: 'main', sequentialTestRuns: false, timeoutSeconds: 120 },
            });
            form.append('file', new Blob([await readFile(archive)], { type: 'application/zip' }), 'valid-import-c.zip');
            return request('POST', `/api/programming/courses/${course.id}/programming-exercises/import-from-file`, form);
        });
    }

    const exerciseId = exercise.id;
    console.log(`Exercise ${exerciseId} (${exerciseTitle})`);
    const resultFor = (id) => request('GET', `/api/programming/programming-exercise-participations/${id}/latest-result-with-feedbacks?withSubmission=true`);
    await stage('Wait for the imported solution to produce test cases', buildHelp, () =>
        waitFor(async () => {
            const cases = await request('GET', `${exercises}/${exerciseId}/test-cases`);
            if (cases.length) return true;
            const result = await resultFor(exercise.solutionParticipation.id);
            if (result?.completionDate && (result.submission?.buildFailed || result.testCaseCount === 0)) {
                throw new Error(`Solution build result ${result.id} produced no test cases. Open the solution build log for exercise ${exerciseId}.`);
            }
        }),
    );
    participation ??= await stage('Open the student participation', 'Check that the exercise is released and LocalVC is available.', () =>
        request('POST', `/api/exercise/exercises/${exerciseId}/participations`),
    );
    const participationPath = `/api/programming/programming-exercise-participations/${participation.id}`;
    const repository = (id) => `/api/programming/participations/${id}/repository`;
    const commit = await stage(`Commit the solution to participation ${participation.id}`, 'Inspect the solution and student repositories in the online editor.', async () => {
        const source = await request('GET', `${repository(exercise.solutionParticipation.id)}/file?file=exercise.c`, undefined, { Accept: 'application/octet-stream' });
        const before = await request('GET', `${participationPath}/commit-history`);
        if (existingId) {
            const restoredHash = verifyRestoredGrade(before, await resultFor(participation.id));
            console.log(`Verified restored grade for commit ${restoredHash}`);
        }
        const files = [{ fileName: 'exercise.c', fileContent: `${source}\n/* Grading trial ${randomUUID()} */\n` }];
        const saved = await request('PUT', `${repository(participation.id)}/files?commit=true`, files);
        if (saved['exercise.c']) throw new Error(saved['exercise.c']);
        return newCommit(before, await request('GET', `${participationPath}/commit-history`));
    });
    const grade = await stage(`Wait for the grade of commit ${commit}`, buildHelp, () => waitFor(async () => checkGrade(await resultFor(participation.id), commit)));
    console.log(
        `PASS exercise=${exerciseId} participation=${participation.id} commit=${commit} result=${grade.id} score=${grade.score}% tests=${grade.passedTestCaseCount}/${grade.testCaseCount}`,
    );
}

if (import.meta.main) {
    run().catch((error) => {
        console.error(error.message);
        process.exitCode = 1;
    });
}
