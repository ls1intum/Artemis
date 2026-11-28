import assert from 'node:assert/strict';
import { execFile } from 'node:child_process';
import { once } from 'node:events';
import { readFile } from 'node:fs/promises';
import { createServer } from 'node:http';
import test from 'node:test';
import { fileURLToPath } from 'node:url';
import { promisify } from 'node:util';
import { checkGrade, newCommit, verifyRestoredGrade, trialOrigin } from './verify-grading.mjs';

const commit = 'b'.repeat(40);
const previous = { hash: 'a'.repeat(40) };
const grade = {
    id: 42,
    submission: { commitHash: commit },
    assessmentType: 'AUTOMATIC',
    completionDate: '2026-01-01T00:00:00Z',
    score: 100,
    testCaseCount: 3,
    passedTestCaseCount: 3,
};

test('targets only a loopback port', () => {
    assert.equal(trialOrigin('18080'), 'http://127.0.0.1:18080');
    for (const port of ['0', '65536', 'http://example.org', '8080/path']) {
        assert.throws(() => trialOrigin(port), /port/i);
    }
});

test('requires one new commit in the participation history', () => {
    assert.equal(newCommit([previous], [{ hash: commit }, previous]), commit);
    assert.throws(() => newCommit([previous], [previous]), /commit/);
    assert.throws(() => newCommit([], [previous, { hash: commit }]), /commit/);
});

test('waits for the completed automatic result of the submitted commit', () => {
    assert.equal(checkGrade(null, commit), undefined);
    assert.equal(checkGrade({ ...grade, submission: { commitHash: previous.hash } }, commit), undefined);
    assert.equal(checkGrade({ ...grade, completionDate: null }, commit), undefined);
    assert.equal(checkGrade({ ...grade, assessmentType: 'MANUAL' }, commit), undefined);
    assert.equal(checkGrade(grade, commit), grade);
});

test('rejects failed builds, wrong scores, and empty test runs', () => {
    for (const change of [{ submission: { commitHash: commit, buildFailed: true } }, { score: 0 }, { testCaseCount: 0 }, { passedTestCaseCount: 2 }]) {
        assert.throws(() => checkGrade({ ...grade, ...change }, commit), /result 42/i);
    }
});

test('requires the restored grade and Git history to agree before writing', () => {
    assert.throws(() => verifyRestoredGrade([previous], grade), /restored/i);
    assert.throws(() => verifyRestoredGrade([previous], undefined), /restored/i);
    assert.equal(verifyRestoredGrade([{ hash: commit }], grade), commit);
});

test('the operator command imports the shipped ZIP and uses the solution in its own participation', async (t) => {
    const fixture = await readFile(new URL('../../src/test/resources/test-data/import-from-file/valid-import-c.zip', import.meta.url));
    const source = 'int main(void) { return 0; }';
    const exercise = { id: 2, solutionParticipation: { id: 3 } };
    const participationPath = '/api/programming/programming-exercise-participations/4';
    let committed = false;
    let solutionFailed = false;
    const server = createServer(async (request, response) => {
        // Return handler assertions to the child process so a malformed request fails immediately.
        try {
            let data;
            const route = `${request.method} ${request.url}`;
            if (route === 'POST /api/core/public/authenticate') {
                response.setHeader('Set-Cookie', 'jwt=local-test; HttpOnly');
                data = {};
            } else {
                assert.equal(request.headers.cookie, 'jwt=local-test');
                switch (route) {
                    case 'POST /api/admin/courses': {
                        const form = await new Response(request, { headers: request.headers }).formData();
                        assert.equal(JSON.parse(await form.get('course').text()).testCourse, true);
                        data = { id: 1 };
                        break;
                    }
                    case 'POST /api/programming/courses/1/programming-exercises/import-from-file': {
                        const form = await new Response(request, { headers: request.headers }).formData();
                        assert.deepEqual(Buffer.from(await form.get('file').arrayBuffer()), fixture);
                        assert.equal(JSON.parse(await form.get('programmingExercise').text()).course.id, 1);
                        data = exercise;
                        break;
                    }
                    case 'GET /api/programming/programming-exercises/2/test-cases':
                        data = solutionFailed ? [] : [{ id: 1 }];
                        break;
                    case 'GET /api/programming/programming-exercise-participations/3/latest-result-with-feedbacks?withSubmission=true':
                        data = { ...grade, id: 51, testCaseCount: 0, score: 0, submission: { buildFailed: true } };
                        break;
                    case 'POST /api/exercise/exercises/2/participations':
                        data = { id: 4 };
                        break;
                    case 'GET /api/programming/participations/3/repository/file?file=exercise.c':
                        response.writeHead(200, { 'Content-Type': 'application/octet-stream' }).end(source);
                        return;
                    case `GET ${participationPath}/commit-history`:
                        data = committed ? [{ hash: commit }, previous] : [previous];
                        break;
                    case 'PUT /api/programming/participations/4/repository/files?commit=true': {
                        const [file] = await new Response(request).json();
                        assert.equal(file.fileName, 'exercise.c');
                        assert.ok(file.fileContent.startsWith(source));
                        assert.match(file.fileContent, /Grading trial [a-f0-9-]+/);
                        committed = true;
                        data = { 'exercise.c': null };
                        break;
                    }
                    case `GET ${participationPath}/latest-result-with-feedbacks?withSubmission=true`:
                        assert.equal(committed, true);
                        data = grade;
                        break;
                    default:
                        throw new Error('Unexpected request ' + route);
                }
            }
            response.writeHead(200, { 'Content-Type': 'application/json' }).end(JSON.stringify(data));
        } catch (error) {
            response.writeHead(400).end(error.message);
        }
    });
    server.listen(0, '127.0.0.1');
    await once(server, 'listening');
    t.after(() => server.close());
    const script = fileURLToPath(new URL('./verify-grading.mjs', import.meta.url));
    const invoke = () => promisify(execFile)(process.execPath, [script], { env: { ...process.env, ARTEMIS_TRIAL_PORT: String(server.address().port) }, timeout: 15_000 });
    const { stdout } = await invoke();
    assert.match(stdout, new RegExp(`PASS exercise=2 participation=4 commit=${commit} result=42 score=100% tests=3/3`));
    solutionFailed = true;
    await assert.rejects(invoke(), (error) => error.code === 1 && /solution build result 51/i.test(error.stderr));
});
