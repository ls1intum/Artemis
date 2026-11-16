import { ESLint, Linter } from 'eslint';
import { describe, expect, it } from 'vitest';

const eslint = new ESLint();
const linter = new Linter();
const picker = 'src/main/webapp/app/iris/overview/context-selection/context-selection.component.ts';
const otherMigratedFiles = [
    'src/main/webapp/app/iris/overview/context-selection/iris-context.util.ts',
    'src/main/webapp/app/iris/overview/context-selection/iris-context-switch-divider.component.ts',
    'src/main/webapp/app/exam/overview/exam-navigation-sidebar/exam-navigation-sidebar.component.ts',
];
const bannedImports = [
    'import * as dependency from "primeng";',
    'import * as dependency from "primeng/button";',
    'import * as dependency from "primeng/select/internal";',
    'import * as dependency from "@ng-bootstrap/ng-bootstrap";',
    'import * as dependency from "bootstrap";',
    'import * as dependency from "bootstrap/js/dist/modal";',
    'import * as dependency from "dayjs";',
    'import * as dependency from "lodash";',
    'import { NgZone } from "@angular/core";',
    'import { cloneDeep } from "lodash-es";',
    'import { cloneDeepWith } from "lodash-es";',
    'import * as dependency from "lodash-es/cloneDeep";',
    'import * as dependency from "@tumaet/ui-angular/internal";',
];

async function importErrors(filePath, source) {
    const config = await eslint.calculateConfigForFile(filePath);
    // Exercise the effective rule after all config overrides, without the unrelated typed Angular rules.
    return linter.verify(source, { rules: { 'no-restricted-imports': config.rules['no-restricted-imports'] } });
}

describe('migrated client import restrictions', () => {
    it('allows the contained PrimeNG select fallback in the context picker', async () => {
        expect(await importErrors(picker, 'import { SelectModule } from "primeng/select";')).toEqual([]);
    });

    it.each([picker, ...otherMigratedFiles])('retains every other import ban for %s', async (filePath) => {
        for (const source of bannedImports) {
            const errors = await importErrors(filePath, source);
            expect(errors, `${filePath}: ${source}`).toHaveLength(1);
            expect(errors[0]).toMatchObject({ ruleId: 'no-restricted-imports', severity: 2 });
        }
    });

    it.each(otherMigratedFiles)('does not allow the PrimeNG select fallback in %s', async (filePath) => {
        expect(await importErrors(filePath, 'import { SelectModule } from "primeng/select";')).toHaveLength(1);
    });

    it.each([picker, ...otherMigratedFiles])('keeps supported public imports available in %s', async (filePath) => {
        const imports = [
            'import { TumAetUiChipComponent } from "@tumaet/ui-angular";',
            'import { Component } from "@angular/core";',
            'import dayjs from "dayjs/esm";',
            'import { debounce } from "lodash-es";',
        ];
        for (const source of imports) {
            expect(await importErrors(filePath, source)).toEqual([]);
        }
    });
});
