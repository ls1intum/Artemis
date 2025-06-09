import { ESLintUtils } from "@typescript-eslint/utils";
import ts from "typescript";

const createRule = ESLintUtils.RuleCreator(() => "");

export default createRule({
    name: "require-signal-ref",
    meta: {
        type: "problem",
        docs: {
            description:
                "When a dialog’s property is backed by an Angular Signal (via `input()` / `input.required()`), ensure assignments into `modalRef.componentInstance.x` pass the Signal itself — never `.value` or by calling it.",
            recommended: "error",
        },
        messages: {
            noValue:
                "Input '{{name}}' is signal-backed; don’t pass '{{expr}}.value'. Pass the Signal itself (e.g. `this.{{name}}`).",
            noCall:
                "Input '{{name}}' is signal-backed; don’t invoke it ('{{expr}}()'). Pass the Signal itself (no `()`).",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        const parserServices = ESLintUtils.getParserServices(context);
        const checker       = parserServices.program.getTypeChecker();
        const src           = context.getSourceCode();

        // modalRef var name → dialog component Symbol
        const modalMap = new Map();

        // is this prop initialized with input() or input.required()?
        function isSignalBacked(decl) {
            const init = decl.initializer;
            if (!init || !ts.isCallExpression(init)) return false;
            const e = init.expression;
            return (
                (ts.isIdentifier(e) && e.text === "input") ||
                (ts.isPropertyAccessExpression(e) &&
                    ts.isIdentifier(e.expression) &&
                    e.expression.text === "input")
            );
        }

        return {
            // 1) capture `const mr = this.modalService.open(TheDialog, …)`
            CallExpression(node) {
                if (
                    node.callee.type !== "MemberExpression" ||
                    node.callee.property.name !== "open"
                ) return;

                const tsObj = parserServices.esTreeNodeToTSNodeMap.get(
                    node.callee.object
                );
                if (
                    checker
                        .getTypeAtLocation(tsObj)
                        .getSymbol()
                        ?.getName() !== "NgbModal"
                ) return;

                const compArg = node.arguments[0];
                if (!compArg || compArg.type !== "Identifier") return;

                let compSym = checker.getSymbolAtLocation(
                    parserServices.esTreeNodeToTSNodeMap.get(compArg)
                );
                // resolve import-alias to real class
                if (compSym && (compSym.flags & ts.SymbolFlags.Alias)) {
                    compSym = checker.getAliasedSymbol(compSym);
                }
                if (!compSym) return;

                // figure out the modalRef var
                let modalVar;
                const p = node.parent;
                if (
                    p.type === "VariableDeclarator" &&
                    p.id.type === "Identifier"
                ) {
                    modalVar = p.id.name;
                } else if (
                    p.type === "AssignmentExpression" &&
                    p.left.type === "Identifier"
                ) {
                    modalVar = p.left.name;
                }
                if (modalVar) modalMap.set(modalVar, compSym);
            },

            // 2) on `mr.componentInstance.foo = RHS`, only enforce if foo is signal-backed
            AssignmentExpression(node) {
                const L = node.left;
                if (
                    L.type !== "MemberExpression" ||
                    L.object.type !== "MemberExpression" ||
                    L.object.property.name !== "componentInstance" ||
                    L.property.type !== "Identifier"
                ) return;

                const mr = L.object.object;
                if (mr.type !== "Identifier") return;
                const compSym = modalMap.get(mr.name);
                if (!compSym) return;

                const inputName = L.property.name;

                // find the class-prop decl
                let found = null;
                for (const d of compSym.getDeclarations()) {
                    if (!ts.isClassDeclaration(d)) continue;
                    const member = d.members.find(
                        (m) =>
                            ts.isPropertyDeclaration(m) &&
                            ts.isIdentifier(m.name) &&
                            m.name.text === inputName
                    );
                    if (member && isSignalBacked(member)) {
                        found = member;
                        break;
                    }
                }
                if (!found) return;  // not signal-backed

                const rhs = node.right;

                // A) .value
                if (
                    rhs.type === "MemberExpression" &&
                    rhs.property.type === "Identifier" &&
                    rhs.property.name === "value"
                ) {
                    return context.report({
                        node: rhs.property,
                        messageId: "noValue",
                        data: {
                            name: inputName,
                            expr: src.getText(rhs.object),
                        },
                    });
                }

                // B) invocation foo()
                if (rhs.type === "CallExpression") {
                    const calleeText = src.getText(rhs.callee);
                    if (
                        (rhs.callee.type === "Identifier" &&
                            rhs.callee.name === inputName) ||
                        (rhs.callee.type === "MemberExpression" &&
                            rhs.callee.property.name === inputName)
                    ) {
                        return context.report({
                            node: rhs,
                            messageId: "noCall",
                            data: {
                                name: inputName,
                                expr: calleeText,
                            },
                        });
                    }
                }
            },
        };
    },
});
