import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { createRequire } from 'node:module';
import { runInNewContext } from 'node:vm';
import ts from 'typescript';
import { describe, expect, it } from 'vitest';

const root = new URL('../../../src/test/playwright/', import.meta.url);

function loadTypeScript(path, imports = {}) {
    const file = new URL(path, root);
    const require = createRequire(file);
    const module = { exports: {} };
    const compiled = ts.transpileModule(readFileSync(file, 'utf8'), {
        compilerOptions: { module: ts.ModuleKind.CommonJS, target: ts.ScriptTarget.ES2022, esModuleInterop: true },
    }).outputText;
    runInNewContext(compiled, {
        module,
        exports: module.exports,
        AggregateError,
        process: { env: {} },
        require: (name) => {
            if (Object.hasOwn(imports, name)) return imports[name];
            if (name.endsWith('.json') || name === 'dayjs') return require(name);
            throw new Error(`Unexpected import in ${path}: ${name}`);
        },
    });
    return module.exports;
}

// Creation-only dependencies do not participate in deletion. Keep the real endpoint constants
// and deletion methods, replacing only the request boundary exercised below.
const { ExerciseAPIRequests } = loadTypeScript('support/requests/ExerciseAPIRequests.ts', {
    '../constants': loadTypeScript('support/constants.ts'),
    '../timeouts': loadTypeScript('support/timeouts.ts'),
    '../utils': {},
    'app/programming/shared/entities/programming-exercise-build.config': {},
    '../../fixtures/fixtures': {},
    'app/quiz/shared/util/quiz-exercise-creation-request.util': {},
});

function loadHooks() {
    const hooks = {};
    const test = () => {};
    test.describe = (...args) => args.at(-1)();
    test.beforeEach = (...args) => (hooks.setup = args.at(-1));
    test.afterEach = (...args) => (hooks.cleanup = args.at(-1));
    loadTypeScript('e2e/course/CourseExercise.spec.ts', {
        '../../support/fixtures': { test },
        '../../support/users': { admin: { username: 'fixture-admin' } },
        '../../support/seedData': { SEED_COURSES: { general: { id: 42 } } },
        '@playwright/test': { expect },
    });
    return hooks;
}

function resources({ failCreate, deletionFailures = new Map() } = {}) {
    let count = 0;
    const remaining = new Map();
    const attempted = [];
    const create = async (kind) => {
        if (++count === failCreate) throw new Error('creation failed');
        remaining.set(count, kind);
        return { id: count };
    };
    const exerciseAPIRequests = new ExerciseAPIRequests({
        request: {
            delete: async (url) => {
                const [, kind, , rawId] = url.split('/');
                const id = Number(rawId);
                assert.equal(remaining.get(id), kind, `Unexpected ${kind} deletion ${id}`);
                attempted.push(id);
                const failure = deletionFailures.get(id);
                if (failure instanceof Error) throw failure;
                const status = failure ?? 200;
                if (status >= 200 && status < 300) remaining.delete(id);
                return { ok: () => status >= 200 && status < 300, status: () => status };
            },
        },
    });
    exerciseAPIRequests.createTextExercise = () => create('text');
    exerciseAPIRequests.createQuizExercise = () => create('quiz');
    return { remaining, attempted, exerciseAPIRequests, login: async () => {} };
}

describe('course exercise fixture cleanup', () => {
    it('does no cleanup work after login fails', async () => {
        const hooks = loadHooks();
        const fixture = resources();
        await expect(hooks.setup({ ...fixture, login: async () => Promise.reject(new Error('login failed')) })).rejects.toThrow('login failed');
        await hooks.cleanup(fixture);
        expect(fixture.attempted).toEqual([]);
    });

    it.each([
        [1, []],
        [2, [1]],
        [3, [2, 1]],
        [4, [2, 3, 1]],
    ])('cleans only existing resources when creation %i fails', async (failCreate, expected) => {
        const hooks = loadHooks();
        const fixture = resources({ failCreate });
        await expect(hooks.setup(fixture)).rejects.toThrow('creation failed');
        await hooks.cleanup(fixture);
        expect(fixture.attempted).toEqual(expected);
        expect(fixture.remaining.size).toBe(0);
    });

    it('deletes all three quizzes and the text anchor after successful setup', async () => {
        const hooks = loadHooks();
        const fixture = resources();
        await hooks.setup(fixture);
        await hooks.cleanup(fixture);
        expect(fixture.attempted).toEqual([2, 3, 4, 1]);
        expect(fixture.remaining.size).toBe(0);
    });

    it('clears stale references before the next login attempt fails', async () => {
        const hooks = loadHooks();
        const first = resources();
        await hooks.setup(first);
        await hooks.cleanup(first);
        const next = resources();
        await expect(hooks.setup({ ...next, login: async () => Promise.reject(new Error('login failed')) })).rejects.toThrow('login failed');
        await hooks.cleanup(next);
        expect(next.attempted).toEqual([]);
    });

    it('attempts every deletion and reports all HTTP and transport failures together', async () => {
        const hooks = loadHooks();
        const transportError = new Error('connection closed');
        const fixture = resources({
            deletionFailures: new Map([
                [2, 403],
                [3, transportError],
                [1, 500],
            ]),
        });
        await hooks.setup(fixture);
        const error = await hooks.cleanup(fixture).catch((reason) => reason);
        expect(fixture.attempted).toEqual([2, 3, 4, 1]);
        expect([...fixture.remaining.keys()]).toEqual([1, 2, 3]);
        expect(error).toBeInstanceOf(AggregateError);
        expect(error.errors).toHaveLength(3);
        expect(error.errors[0].message).toContain('quiz exercise 2: 403');
        expect(error.errors[1]).toBe(transportError);
        expect(error.errors[2].message).toContain('text exercise 1: 500');
        expect(error.message).toContain('quiz exercise 2: 403');
        expect(error.message).toContain('connection closed');
        expect(error.message).toContain('text exercise 1: 500');
    });
});

describe.each([
    ['deleteQuizExercise', 'api/quiz/quiz-exercises/17', 'quiz'],
    ['deleteTextExercise', 'api/text/text-exercises/17', 'text'],
])('%s response validation', (method, endpoint, kind) => {
    it.each([200, 204, 302, 400, 403, 404, 500])('validates HTTP %i from the deletion endpoint', async (status) => {
        const requests = new ExerciseAPIRequests({
            request: {
                delete: async (url) => {
                    expect(url).toBe(endpoint);
                    return { ok: () => status >= 200 && status < 300, status: () => status };
                },
            },
        });
        if (status >= 200 && status < 300) await expect(requests[method](17)).resolves.toBeUndefined();
        else await expect(requests[method](17)).rejects.toThrow(`Failed to delete ${kind} exercise 17: ${status}`);
    });
});
