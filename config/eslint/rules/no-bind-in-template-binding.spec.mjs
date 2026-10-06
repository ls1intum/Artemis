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
                // A string literal that merely mentions .bind() is not a call. The rule walks the parsed expression
                // rather than the raw source, so these must not be flagged.
                { code: `<jhi-table [label]="'pass handler.bind(this) to the child'" />` },
                { code: `<jhi-table [label]="'.bind('" />` },
                // Other member calls in a binding are a different concern and not this rule's business.
                { code: '<jhi-table [rows]="items.map(toRow)" />' },
                { code: '<jhi-table [rows]="rebind(items)" />' },
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
                // Nested inside a larger expression, where a source-text match would be the only alternative.
                {
                    code: '<jhi-table [rows]="items.map(format.bind(this))" />',
                    errors: [{ messageId: 'bindInBinding' }],
                },
                // Nested far deeper than any fixed traversal limit would allow. A depth cap here would be a silent
                // false negative, which is worse than a noisy rule.
                {
                    code: `<jhi-table [rows]="x1(x2(x3(x4(x5(x6(x7(x8(x9(x10(x11(x12(x13(x14(x15(x16(x17(x18(x19(x20(x21(x22(x23(x24(x25(y.bind(this))))))))))))))))))))))))))" />`,
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
