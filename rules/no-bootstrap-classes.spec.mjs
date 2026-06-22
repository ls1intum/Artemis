import { describe, it } from 'vitest';
import rule from './no-bootstrap-classes.mjs';
import { createTemplateRuleTester } from './rule-tester.mjs';

const ruleTester = createTemplateRuleTester();

describe('no-bootstrap-classes', () => {
    it('flags Bootstrap-only classes and leaves shared-name Tailwind utilities alone', () => {
        ruleTester.run('no-bootstrap-classes', rule, {
            valid: [
                // Tailwind utilities that happen to share a name with (or resemble) Bootstrap classes,
                // but are legitimate after migration.
                { code: '<div class="text-right"></div>' },
                { code: '<div class="text-left"></div>' },
                { code: '<div class="text-end"></div>' },
                { code: '<div class="float-start"></div>' },
                { code: '<div class="mb-3"></div>' },
                { code: '<div class="me-1"></div>' },
                { code: '<div class="gap-2"></div>' },
                { code: '<div class="flex"></div>' },
                { code: '<div class="col-span-3"></div>' },
                // A realistic mix of valid Tailwind utilities in one attribute.
                { code: '<div class="flex gap-2 mb-3 me-1 text-right col-span-3"></div>' },
                // Bound class binding whose target is a valid Tailwind utility.
                { code: '<div [class.flex]="isFlex"></div>' },
                { code: '<div [ngClass]="{ \'text-right\': aligned }"></div>' },
                // [class] string concat whose literals are valid Tailwind utilities — no false positive
                // (the identifier `extra` is an expression, not a class list).
                { code: '<div [class]="\'flex gap-2 \' + extra"></div>' },
            ],
            invalid: [
                { code: '<div class="btn"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                { code: '<div class="btn-primary"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn-primary' } }] },
                { code: '<span class="badge"></span>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'badge' } }] },
                { code: '<div class="card"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'card' } }] },
                { code: '<div class="alert"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'alert' } }] },
                { code: '<div class="row"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'row' } }] },
                { code: '<div class="col-md-3"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'col-md-3' } }] },
                { code: '<div class="d-flex"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'd-flex' } }] },
                { code: '<input class="form-control" />', errors: [{ messageId: 'bootstrapClass', data: { cls: 'form-control' } }] },
                { code: '<table class="table-striped"></table>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'table-striped' } }] },
                // [class.<bootstrap>] — the banned token is the attribute name itself.
                { code: '<div [class.btn]="isButton"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                // [ngClass] object form — banned token lives in a quoted key inside the expression.
                { code: '<div [ngClass]="{ \'btn\': isButton }"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                // [class] string-concat form — banned token lives in a quoted string literal segment.
                { code: '<div [class]="\'d-flex \' + extra"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'd-flex' } }] },
                // One offending token among valid Tailwind utilities is still reported exactly once.
                { code: '<div class="flex gap-2 btn mb-3"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
            ],
        });
    });
});
