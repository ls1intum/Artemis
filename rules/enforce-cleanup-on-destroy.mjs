import { ESLintUtils } from "@typescript-eslint/utils";

const createRule = ESLintUtils.RuleCreator(() => "");

/**
 * ESLint rule that enforces proper cleanup of resources in Angular component ngOnDestroy.
 *
 * Detects:
 * - MutationObserver / ResizeObserver / IntersectionObserver created without .disconnect() in ngOnDestroy
 * - addEventListener calls without removeEventListener in ngOnDestroy
 * - interact() calls without .unset() in ngOnDestroy
 *
 * Only checks Angular component files (.component.ts).
 */
const rule = createRule({
    name: "enforce-cleanup-on-destroy",
    meta: {
        type: "problem",
        docs: {
            description: "Enforce cleanup of observers, event listeners, and interact.js handlers in ngOnDestroy",
        },
        messages: {
            missingObserverDisconnect:
                "{{observerType}} is created but never disconnected. Store the observer and call .disconnect() in ngOnDestroy() to prevent memory leaks.",
            missingRemoveEventListener:
                "addEventListener('{{eventName}}', ...) is called but the listener is not removed in ngOnDestroy(). Store the listener reference and call removeEventListener() in ngOnDestroy().",
            missingInteractUnset:
                "interact() handler is created but never unset. Store the return value and call .unset() in ngOnDestroy() to prevent accumulated event handlers.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        // Only apply to Angular component files
        const filename = context.filename || context.getFilename();
        if (!filename.endsWith(".component.ts")) {
            return {};
        }

        let hasNgOnDestroy = false;
        let ngOnDestroyBody = "";

        const observers = []; // { type, node }
        const addEventListenerCalls = []; // { eventName, node }
        const interactCalls = []; // { node }

        return {
            // Collect ngOnDestroy method body text for checking cleanup calls
            "MethodDefinition[key.name='ngOnDestroy']"(node) {
                hasNgOnDestroy = true;
                if (node.value && node.value.body) {
                    ngOnDestroyBody = context.sourceCode.getText(node.value.body);
                }
            },

            // Detect new MutationObserver / ResizeObserver / IntersectionObserver
            "NewExpression[callee.name=/^(MutationObserver|ResizeObserver|IntersectionObserver)$/]"(node) {
                observers.push({ type: node.callee.name, node });
            },

            // Detect addEventListener calls
            "CallExpression[callee.property.name='addEventListener']"(node) {
                const args = node.arguments;
                if (args.length >= 2) {
                    const eventName = args[0].type === "Literal" ? args[0].value : "<dynamic>";
                    addEventListenerCalls.push({ eventName, node });
                }
            },

            // Detect interact() calls
            "CallExpression[callee.name='interact']"(node) {
                // Check if result is stored in a variable/field
                const parent = node.parent;
                let isStored = false;
                // Walk up the chain (interact().resizable().on() etc.)
                let current = parent;
                while (current && current.type === "CallExpression") {
                    current = current.parent;
                }
                if (current && (current.type === "AssignmentExpression" || current.type === "VariableDeclarator")) {
                    isStored = true;
                }
                // Also check ExpressionStatement chains like this.interactable = interact(...)...
                if (current && current.type === "MemberExpression" && current.parent && current.parent.type === "AssignmentExpression") {
                    isStored = true;
                }
                interactCalls.push({ node, isStored });
            },

            // At the end of the program, check if cleanup is present
            "Program:exit"() {
                // Check observers
                for (const obs of observers) {
                    if (!ngOnDestroyBody.includes(".disconnect()") && !ngOnDestroyBody.includes("disconnect()")) {
                        context.report({
                            node: obs.node,
                            messageId: "missingObserverDisconnect",
                            data: { observerType: obs.type },
                        });
                    }
                }

                // Check addEventListener — only report if removeEventListener is not in ngOnDestroy
                for (const listener of addEventListenerCalls) {
                    // Skip @HostListener-style calls (those on 'document' or 'window' objects managed by Angular)
                    const callee = listener.node.callee;
                    if (callee && callee.object) {
                        const objText = context.sourceCode.getText(callee.object);
                        if (objText === "document" || objText === "window") {
                            continue;
                        }
                    }
                    if (!ngOnDestroyBody.includes("removeEventListener")) {
                        context.report({
                            node: listener.node,
                            messageId: "missingRemoveEventListener",
                            data: { eventName: listener.eventName },
                        });
                    }
                }

                // Check interact()
                for (const call of interactCalls) {
                    if (!ngOnDestroyBody.includes(".unset()") && !ngOnDestroyBody.includes("unset()")) {
                        context.report({
                            node: call.node,
                            messageId: "missingInteractUnset",
                        });
                    }
                }
            },
        };
    },
});

export default rule;
