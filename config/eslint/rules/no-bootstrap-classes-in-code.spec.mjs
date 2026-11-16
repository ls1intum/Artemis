import { describe, it } from 'vitest';
import rule from './no-bootstrap-classes-in-code.mjs';
import { createTypeScriptRuleTester } from './rule-tester.mjs';

const ruleTester = createTypeScriptRuleTester();

const inCode = (cls) => ({ messageId: 'bootstrapClassInCode', data: { cls } });
const dynamic = (prefix) => ({ messageId: 'dynamicBootstrapClass', data: { prefix } });

describe('no-bootstrap-classes-in-code', () => {
    it('flags Bootstrap class names that code builds, keeps or looks up, and leaves ordinary words alone', () => {
        ruleTester.run('no-bootstrap-classes-in-code', rule, {
            valid: [
                // Ordinary words are identifiers and discriminants in code, not classes.
                { code: "const kind = 'card';" },
                { code: "const type = 'table';" },
                { code: "dialog.title = 'close';" },
                { code: "const tag = 'h1';" },
                { code: "const label = 'badge';" },
                { code: "const mode = 'modal';" },
                // Prose and translation keys.
                { code: "const text = 'Please close the dialog and try again.';" },
                { code: "const key = 'artemisApp.exercise.table.header';" },
                // Tailwind and TUM AET UI names, also computed ones.
                { code: "const cls = 'flex gap-2 font-bold text-state-danger';" },
                { code: "const cls = 'text-' + 'state-danger';" },
                { code: "const severity = 'danger';" },
                // A prefix that is not continued dynamically names no class.
                { code: "const prefix = 'text-';" },
                { code: "const url = 'api/btn-' + id;" },
                // Imports are module specifiers, not classes.
                { code: "import { x } from 'bootstrap-icons';" },
                { code: 'const s = `hello ${name}`;' },
            ],
            invalid: [
                { code: "const cls = 'btn btn-sm';", errors: [inCode('btn'), inCode('btn-sm')] },
                { code: "const cls = 'btn';", errors: [inCode('btn')] },
                { code: "const map = { danger: 'text-danger', ok: 'text-success' };", errors: [inCode('text-danger'), inCode('text-success')] },
                { code: "const cls = 'd-flex align-items-center';", errors: [inCode('d-flex'), inCode('align-items-center')] },
                { code: "const cls = 'fw-bold';", errors: [inCode('fw-bold')] },
                { code: 'const cls = `rounded-3 ${extra}`;', errors: [inCode('rounded-3')] },
                // Lookups of Bootstrap markup in the DOM.
                { code: "el.querySelector('.modal-dialog');", errors: [inCode('modal-dialog')] },
                { code: "el.closest('.card .card-body > .btn-close');", errors: [inCode('card-body'), inCode('btn-close')] },
                { code: 'const sel = \'[data-bs-toggle="tooltip"], .form-control\';', errors: [inCode('form-control')] },
                // Class names that are computed from a prefix.
                { code: "const cls = 'text-' + color;", errors: [dynamic('text-')] },
                { code: 'const cls = `btn-${variant}`;', errors: [dynamic('btn-')] },
                { code: "const cls = 'alert alert-' + type;", errors: [dynamic('alert-')] },
                { code: "const cls = 'badge bg-' + status;", errors: [dynamic('bg-')] },
            ],
        });
    });
});
