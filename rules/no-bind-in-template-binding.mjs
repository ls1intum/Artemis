/**
 * Forbid `.bind(...)` inside an Angular property binding (`[input]="handler.bind(this)"`).
 *
 * WHY: a template property binding is re-evaluated on every change-detection pass, and `.bind()` returns a NEW
 * function object each time. The consumer therefore sees a changed input on every pass: an OnPush child re-renders
 * for nothing, a `computed()`/`effect()` that reads it re-runs, and anything that writes back into the producer
 * turns the churn into a loop. PR #13502 fixed exactly this shape twice in
 * programming-exercise-update.component.ts, where the loop froze the page in a production build (dev mode aborts
 * with an "infinite change detection" error, production has no such guard) and surfaced as two E2E tests timing out
 * at four minutes.
 *
 * FIX: bind once and hand over the stable reference.
 *
 *   // component
 *   readonly reloadRows = () => this.loadAll();
 *   // template
 *   [loadAll]="reloadRows"
 *
 * SCOPE: property bindings only. `(click)="save.bind(this)()"` and other event bindings are not flagged, because an
 * event binding runs on user action rather than during change detection, so a fresh function there costs nothing.
 * See documentation/docs/developer/guidelines/client-development.mdx.
 */
export default {
    meta: {
        type: 'problem',
        docs: {
            description: 'Forbid `.bind()` inside an Angular property binding, which hands the consumer a new function identity on every change-detection pass.',
        },
        messages: {
            bindInBinding:
                "`.bind()` in a property binding creates a new function on every change-detection pass, so '{{name}}' changes every pass and its consumer can never skip work. Bind once in the component (e.g. `readonly handler = () => this.onThing();`) and bind the stable reference instead.",
        },
        schema: [],
    },
    create(context) {
        return {
            BoundAttribute(node) {
                const source = node.value?.source;
                if (typeof source !== 'string' || !/\.bind\s*\(/.test(source)) {
                    return;
                }
                context.report({ node, messageId: 'bindInBinding', data: { name: node.name } });
            },
        };
    },
};
