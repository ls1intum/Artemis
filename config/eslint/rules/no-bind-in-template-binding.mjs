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
        /**
         * True when the expression contains a call to a member named `bind`.
         *
         * Walks the parsed expression rather than matching the raw source text, so a string literal that merely
         * mentions `.bind()` is not flagged: `[label]="'pass handler.bind(this)'"` parses to a LiteralPrimitive with
         * no Call node, while `[loadAll]="loadAll.bind(this)"` parses to a Call whose receiver is a PropertyRead
         * named `bind`. Members named `bind` on the component are matched the same way, which is intended - calling
         * one in a binding still yields a fresh value per pass.
         *
         * Duck-typed on shape (a call has an `args` array and a `receiver`) rather than on AST class names, so it
         * survives the parser renaming its node classes.
         *
         * Cycles are guarded with a visited set rather than a depth limit: a limit is a silent false negative, and
         * expression depth grows about one level per nesting level, so any cap is a guess about how convoluted a
         * template is allowed to get.
         */
        const callsBind = (root) => {
            const visited = new WeakSet();
            const walk = (node) => {
                if (!node || typeof node !== 'object' || visited.has(node)) {
                    return false;
                }
                visited.add(node);
                if (Array.isArray(node.args) && node.receiver?.name === 'bind') {
                    return true;
                }
                for (const key of Object.keys(node)) {
                    // Spans carry offsets and parent-ish references; skipping them keeps the walk cheap.
                    if (key.endsWith('Span') || key === 'span') {
                        continue;
                    }
                    const value = node[key];
                    if (Array.isArray(value)) {
                        if (value.some((entry) => walk(entry))) {
                            return true;
                        }
                    } else if (walk(value)) {
                        return true;
                    }
                }
                return false;
            };
            return walk(root);
        };

        return {
            BoundAttribute(node) {
                if (!callsBind(node.value?.ast)) {
                    return;
                }
                context.report({ node, messageId: 'bindInBinding', data: { name: node.name } });
            },
        };
    },
};
