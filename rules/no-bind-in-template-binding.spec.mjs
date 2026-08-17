import { describe, it } from 'vitest';
import rule from './no-bind-in-template-binding.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

describe('no-bind-in-template-binding', () => {
    it('forbids .bind() in property bindings but leaves event bindings and stable references alone', () => {
        ruleTester.run('no-bind-in-template-binding', rule, {
            valid: [
                // The sanctioned shape: a stable reference bound once in the component.
                { code: '<jhi-table [loadAll]="reloadRows" />' },
                { code: '<jhi-quiz [fnOnSelection]="selectionChangedCallback" />' },
                // Event bindings run on user action, not during change detection, so a fresh function costs nothing.
                { code: '<button (click)="save.bind(this)()">Save</button>' },
                { code: '<button (click)="onClick()">Save</button>' },
                // A plain method call in a property binding is a different concern and not this rule's business.
                { code: '<jhi-table [rows]="visibleRows()" />' },
                // `bind` as part of an unrelated identifier must not match.
                { code: '<jhi-table [rows]="binding" />' },
                { code: '<jhi-table [rows]="rebindCount" />' },
                // Static attributes are never re-evaluated bindings.
                { code: '<jhi-table loadAll="loadAll.bind(this)" />' },
            ],
            invalid: [
                {
                    code: '<jhi-table [loadAll]="loadAll.bind(this)" />',
                    errors: [{ messageId: 'bindInBinding' }],
                },
                {
                    code: '<jhi-quiz [fnOnSelection]="onSelectionChanged.bind(this)" />',
                    errors: [{ messageId: 'bindInBinding' }],
                },
                // Whitespace between the member and the call must still match.
                {
                    code: '<jhi-table [loadAll]="loadAll.bind (this)" />',
                    errors: [{ messageId: 'bindInBinding' }],
                },
                // Each offending binding is reported separately.
                {
                    code: '<jhi-quiz [a]="x.bind(this)" [b]="y.bind(this)" />',
                    errors: [{ messageId: 'bindInBinding' }, { messageId: 'bindInBinding' }],
                },
            ],
        });
    });
});
