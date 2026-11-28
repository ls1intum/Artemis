import { ESLint, Linter } from 'eslint';
import { describe, expect, it } from 'vitest';

const eslint = new ESLint();
const linter = new Linter();
const ruleId = 'localRules/sharing-module-boundary';
const appRoot = 'src/main/webapp/app/';
const editor = 'programming/manage/update/programming-exercise-update.component.ts';
const connector = 'sharing/sharing.routes.ts';

// Use the repository's effective configuration so disabling or overriding the rule also fails these tests.
async function boundaryErrors(file, code) {
    const filename = appRoot + file;
    const config = await eslint.calculateConfigForFile(filename);
    const messages = linter.verify(
        code,
        [
            {
                files: ['**/*.ts'],
                languageOptions: { parser: config.languageOptions.parser },
                plugins: { localRules: config.plugins.localRules },
                rules: { [ruleId]: config.rules[ruleId] ?? 'off' },
            },
        ],
        { filename },
    );
    expect(messages.filter((message) => message.fatal)).toEqual([]);
    return messages.filter((message) => message.ruleId === ruleId).map((message) => message.messageId);
}

describe('optional sharing module boundary', () => {
    it.each([
        [editor, "import type { SharingInfo } from 'app/sharing/sharing.model';"],
        [editor, "export * from '../../../sharing/sharing.model';"],
        [editor, "export { SharingComponent } from 'src/main/webapp/app/sharing/sharing.component';"],
        [editor, "const load = () => import('app/sharing-basket.route');"],
        [editor, "type Model = import('app/sharing/sharing.model').SharingInfo;"],
        ['app.routes.ts', "import 'app/sharing/programming-exercise-sharing.service';"],
        ['core/about-us/about-us-data.ts', 'const load = () => import(`../../sharing/sharing.component`);'],
    ])('rejects an incoming dependency in %s: %s', async (file, source) => {
        expect(await boundaryErrors(file, source)).toEqual(['incoming']);
    });

    it.each(["http.get('api/core/sharing/config/is-enabled');", 'http.post(`/api/programming/sharing/export?exerciseId=${id}`, callback);'])(
        'rejects a direct request that bypasses the sharing module: %s',
        async (source) => {
            expect(await boundaryErrors(editor, source)).toEqual(['endpoint']);
            expect(await boundaryErrors(connector, source)).toEqual([]);
        },
    );

    it.each([
        "import { Course } from '../course/shared/entities/course.model';",
        "import 'app/programming/api/../manage/programming-exercise-shared.route';",
        "export * from 'app/exercise/shared/routes/exercise-update-route';",
        "const load = () => import('src/main/webapp/app/admin/features/admin-feature-toggle.component');",
        "import service = require('app/programming/manage/services/programming-exercise.service');",
        "const service = require('app/core/auth/user-route-access-service');",
    ])('rejects a private or unrelated dependency from sharing: %s', async (source) => {
        expect(await boundaryErrors(connector, source)).toEqual(['outgoing']);
    });

    it.each([
        ['app.routes.ts', "import './sharing/sharing.routes';"],
        ['app.routes.ts', "const load = () => import('./sharing-basket.route');"],
        ['sharing-basket.route.ts', "import 'app/sharing/sharing.component';"],
        ['sharing-basket.route.ts', "import 'app/sharing/sharing-course';"],
        [connector, "export type { ProgrammingExerciseImport } from '../programming/api/programming-exercise-extensions';"],
        [connector, "const load = () => import('./sharing-import.route');"],
        [connector, "import 'app/app.constants'; import 'app/foundation/service/alert.service'; import 'app/core/layouts/profiles/shared/profile.service';"],
        [connector, "import { inject } from '@angular/core';"],
    ])('allows the public API, shared infrastructure and route wiring in %s: %s', async (file, source) => {
        expect(await boundaryErrors(file, source)).toEqual([]);
    });
});
