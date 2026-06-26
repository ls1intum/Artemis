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
                { code: '<div class="h-full w-full"></div>' },
                // `h-auto` / `w-auto` are shared (width:auto) and stay valid.
                { code: '<div class="w-auto h-auto"></div>' },
                // A realistic mix of valid Tailwind utilities in one attribute.
                { code: '<div class="flex gap-2 mb-3 me-1 text-right col-span-3"></div>' },
                // Custom class names that merely contain a Bootstrap family word are not flagged (anchored, exact `nav`).
                { code: '<li class="nav-group-header"></li>' },
                { code: '<a class="nav-link-sidebar"></a>' },
                { code: '<div class="my-modal-wrapper"></div>' },
                // Bound class binding whose target is a valid Tailwind utility.
                { code: '<div [class.flex]="isFlex"></div>' },
                { code: '<div [ngClass]="{ \'text-right\': aligned }"></div>' },
                // Unquoted Tailwind-utility key in an object binding is fine — only Bootstrap names are flagged.
                { code: '<div [ngClass]="{ flex: active }"></div>' },
                // PrimeNG styleClass holding Tailwind utilities is fine.
                { code: '<p-message styleClass="flex gap-2"></p-message>' },
                // [class] string concat whose literals are valid Tailwind utilities — no false positive
                // (the identifier `extra` is an expression, not a class list).
                { code: '<div [class]="\'flex gap-2 \' + extra"></div>' },
                // Component INPUTS that share a Bootstrap class name are not class bindings — must not be flagged.
                { code: '<my-dialog [close]="onClose" [card]="data"></my-dialog>' },
                { code: '<div [attr.role]="\'row\'"></div>' },
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
                // Bootstrap component families.
                { code: '<div class="modal"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'modal' } }] },
                { code: '<div class="dropdown-menu"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'dropdown-menu' } }] },
                { code: '<div class="navbar-brand"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'navbar-brand' } }] },
                { code: '<div class="nav-link"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'nav-link' } }] },
                { code: '<li class="list-group-item"></li>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'list-group-item' } }] },
                { code: '<div class="spinner-border"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'spinner-border' } }] },
                { code: '<a class="page-link"></a>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'page-link' } }] },
                { code: '<input class="form-control" />', errors: [{ messageId: 'bootstrapClass', data: { cls: 'form-control' } }] },
                { code: '<table class="table-striped"></table>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'table-striped' } }] },
                { code: '<div class="h-100"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'h-100' } }] },
                { code: '<div class="w-50"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'w-50' } }] },
                // [class.<bootstrap>] — the banned token is the attribute name itself.
                { code: '<div [class.btn]="isButton"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                // [ngClass] object form — banned token in a quoted key, and in the UNQUOTED key Prettier
                // rewrites it to (`{ 'btn': x }` -> `{ btn: x }`), which must not bypass the rule.
                { code: '<div [ngClass]="{ \'btn\': isButton }"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                { code: '<div [ngClass]="{ btn: isButton }"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                { code: '<div [class]="{ card: isCard }"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'card' } }] },
                // PrimeNG styleClass (and component-specific *StyleClass) render classes at runtime — scan them too.
                { code: '<p-message styleClass="alert"></p-message>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'alert' } }] },
                { code: '<p-button [styleClass]="\'btn \' + extra"></p-button>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
                { code: '<p-tree contentStyleClass="d-flex"></p-tree>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'd-flex' } }] },
                // [class] string-concat form — banned token lives in a quoted string literal segment.
                { code: '<div [class]="\'d-flex \' + extra"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'd-flex' } }] },
                // One offending token among valid Tailwind utilities is still reported exactly once.
                { code: '<div class="flex gap-2 btn mb-3"></div>', errors: [{ messageId: 'bootstrapClass', data: { cls: 'btn' } }] },
            ],
        });
    });
});
