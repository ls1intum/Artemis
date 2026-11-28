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
    it('bans the former PrimeNG select fallback in the migrated context picker', async () => {
        expect(await importErrors(picker, 'import { SelectModule } from "primeng/select";')).toHaveLength(1);
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

    it.each([picker, ...otherMigratedFiles])('rejects the package root and an unknown entry point of TUM AET UI in %s', async (filePath) => {
        for (const source of [
            'import { TumAetUiChipComponent } from "@tumaet/ui-angular";',
            'import { TumAetUiChipComponent } from "@tumaet/ui-angular/chip/index";',
            'import * as dependency from "@tumaet/ui-angular/unknown";',
        ]) {
            const errors = await importErrors(filePath, source);
            expect(errors, `${filePath}: ${source}`).toHaveLength(1);
            expect(errors[0]).toMatchObject({ ruleId: 'no-restricted-imports', severity: 2 });
        }
    });

    // The foundation/ and shared-ui/ blocks of the configuration override the rule, so each of them has to repeat the restriction.
    const iconRestrictedFiles = [
        picker,
        ...otherMigratedFiles,
        'src/main/webapp/app/foundation/service/alert.service.ts',
        'src/main/webapp/app/shared-ui/delete-dialog/service/delete-dialog.service.ts',
        'src/main/webapp/app/core/navbar/navbar.component.ts',
    ];

    it.each(iconRestrictedFiles)('rejects the root of an icon package in %s, where one module per icon is imported', async (filePath) => {
        for (const source of [
            'import { faTrash } from "@fortawesome/free-solid-svg-icons";',
            'import { faCircle } from "@fortawesome/free-regular-svg-icons";',
            'import * as icons from "@fortawesome/free-solid-svg-icons";',
        ]) {
            const errors = await importErrors(filePath, source);
            expect(errors, `${filePath}: ${source}`).toHaveLength(1);
            expect(errors[0]).toMatchObject({ ruleId: 'no-restricted-imports', severity: 2 });
            expect(errors[0].message).toContain('from its own module');
        }
    });

    it('lets the specs of TUM AET UI import icons from the package root, as the kit does, and keeps the other restrictions there', async () => {
        const kitSpec = 'packages/tum-aet-ui/src/lib/button/tumaet-ui-button.component.spec.ts';

        expect(await importErrors(kitSpec, 'import { faTrash } from "@fortawesome/free-solid-svg-icons";')).toEqual([]);
        for (const source of [
            'import * as dependency from "lodash";',
            'import { NgZone } from "@angular/core";',
            'import { TumAetUiButtonComponent } from "@tumaet/ui-angular";',
        ]) {
            expect(await importErrors(kitSpec, source), source).toHaveLength(1);
        }
    });

    it.each(iconRestrictedFiles)('allows an icon from its own module and the other FontAwesome packages in %s', async (filePath) => {
        for (const source of [
            'import { faTrash } from "@fortawesome/free-solid-svg-icons/faTrash";',
            'import { faCircle } from "@fortawesome/free-regular-svg-icons/faCircle";',
            'import { IconDefinition } from "@fortawesome/fontawesome-svg-core";',
            'import { FaIconComponent } from "@fortawesome/angular-fontawesome";',
        ]) {
            expect(await importErrors(filePath, source), `${filePath}: ${source}`).toEqual([]);
        }
    });

    it.each([picker, ...otherMigratedFiles])('keeps supported public imports available in %s', async (filePath) => {
        const imports = [
            'import { TumAetUiChipComponent } from "@tumaet/ui-angular/chip";',
            'import { Component } from "@angular/core";',
            'import dayjs from "dayjs/esm";',
            'import { debounce } from "lodash-es";',
        ];
        for (const source of imports) {
            expect(await importErrors(filePath, source)).toEqual([]);
        }
    });
});
