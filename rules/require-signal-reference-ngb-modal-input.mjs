import { ESLintUtils } from "@typescript-eslint/utils";
import ts from "typescript";

const createRule = ESLintUtils.RuleCreator(() => "");

/**
 * @fileoverview
 * Enforce that when opening an Angular dialog whose inputs are backed by Signals
 * (via `input()` / `input.required()`), you never invoke the Signal—
 * instead always pass the Signal itself.
 *
 * Examples **flagged** by this rule:
 * ```js
 * const dialogReference = this.modalService.open(MyDialogComponent);
 * dialogReference.componentInstance.count = this.countSignal();
 * ```
 *
 * Example **not flagged**:
 * ```js
 * dialogReference.componentInstance.count = this.countSignal;
 */
export default createRule({
    name: "require-signal-reference-ngb-modal-input",
    meta: {
        type: "problem",
        docs: {
            description:
                "When a passing an Angular Signal to a ngb-modal, ensure assignments into `modalRef.componentInstance.x` pass the Signal itself — never by calling it. (use '= mySignal;' instead of '= mySignal();'",
            recommended: "error",
        },
        messages: {
            unexpectedSignalInvocation:
                "Input '{{propertyName}}' is an angular signal; don’t invoke it ('{{expressionText}}()'). Pass the Signal itself.",
        },
        schema: [],
    },
    defaultOptions: [],
    create(context) {
        const parserServices = ESLintUtils.getParserServices(context);
        const typeChecker = parserServices.program.getTypeChecker();
        const sourceCode = context.getSourceCode();

        const modalReferenceVariableNameToComponentClassNameMap = new Map();

        const isModalServiceOpenCall = node => {
            const callee = node.callee;
            return (
                callee.type === "MemberExpression" &&
                callee.property.type === "Identifier" &&
                callee.property.name === "open"
            );
        };


        const isNgbModalInstance = objectNode => {
            const tsNode = parserServices.esTreeNodeToTSNodeMap.get(objectNode);
            const objectType = typeChecker.getTypeAtLocation(tsNode);
            return objectType.getSymbol()?.getName() === "NgbModal";
        };

        /**
         * Resolve the component class symbol from the first argument.
         */
        const resolveComponentSymbol = argumentNode => {
            if (!argumentNode || argumentNode.type !== "Identifier") return null;
            let symbol = typeChecker.getSymbolAtLocation(
                parserServices.esTreeNodeToTSNodeMap.get(argumentNode)
            );
            // The component symbol might be marked as an alias when it was imported using a different name.
            // flags is a bit flag enum, so the check is done using bitwise AND.
            if (symbol?.flags & ts.SymbolFlags.Alias) {
                symbol = typeChecker.getAliasedSymbol(symbol);
            }
            return symbol;
        };


        const extractModalReferenceAssignedVariableName = parentNode => {
            if (
                parentNode?.type === "VariableDeclarator" &&
                parentNode.id.type === "Identifier"
            ) {
                return parentNode.id.name;
            }
            if (
                parentNode?.type === "AssignmentExpression" &&
                parentNode.left.type === "Identifier"
            ) {
                return parentNode.left.name;
            }
            return null;
        };

        /**
         * STEP 1: Capture modal open calls
         */
        const handleOpenCallExpression = node => {
            if (!isModalServiceOpenCall(node)) return;
            const callee = node.callee;
            if (!isNgbModalInstance(callee.object)) return;

            const componentSymbol = resolveComponentSymbol(node.arguments[0]);
            if (!componentSymbol) return;

            const modalVariableName = extractModalReferenceAssignedVariableName(node.parent);
            if (modalVariableName) {
                modalReferenceVariableNameToComponentClassNameMap.set(modalVariableName, componentSymbol);
            }
        };

        /**
         * Returns true if a class property is initialized via `input()` or `input.required()`.
         */
        const isSignalBackedProperty = member => {
            const initializer = member.initializer;
            if (!initializer || !ts.isCallExpression(initializer)) return false;
            const expr = initializer.expression;
            return (
                (ts.isIdentifier(expr) && expr.text === "input") ||
                (ts.isPropertyAccessExpression(expr) &&
                    ts.isIdentifier(expr.expression) &&
                    expr.expression.text === "input")
            );
        };


        const findSignalInputDeclaration = (componentSymbol, propertyName) => {
            for (const decl of componentSymbol.getDeclarations() || []) {
                if (!ts.isClassDeclaration(decl)) continue;
                const member = decl.members.find(member =>
                    ts.isPropertyDeclaration(member) &&
                    ts.isIdentifier(member.name) &&
                    member.name.text === propertyName &&
                    isSignalBackedProperty(member)
                );
                if (member) return member;
            }
            return null;
        };

        /**
         * Validate signal invocation (e.g. `this.courseId()`).
         * @return {boolean} true if the node is a signal invocation
         */
        const checkSignalInvocation = (node, propertyName) => {
            if (node.type !== "CallExpression") return false;
            const callee = node.callee;
            const isDirect =
                callee.type === "Identifier" && callee.name === propertyName;
            const isMember =
                callee.type === "MemberExpression" &&
                callee.property.type === "Identifier" &&
                callee.property.name === propertyName;
            if (isDirect || isMember) {
                const exprText = sourceCode.getText(callee);
                context.report({
                    node,
                    messageId: "unexpectedSignalInvocation",
                    data: { propertyName, expressionText: exprText },
                });
                return true;
            }
            return false;
        };

        /**
         * STEP 2: On assignments to `dialogRef.componentInstance.prop`,
         * forbid  signal invocation `mySignal()` for signal-input props.
         */
        function handleAssignmentExpression(node) {
            const left = node.left;
            // Check shape: modalRef.componentInstance.attributeName
            if (
                left.type !== "MemberExpression" ||
                left.object.type !== "MemberExpression" ||
                left.object.property.name !== "componentInstance" ||
                left.property.type !== "Identifier"
            ) return;

            const modalVar = left.object.object;
            if (modalVar.type !== "Identifier") return;
            const componentSymbol = modalReferenceVariableNameToComponentClassNameMap.get(modalVar.name);
            if (!componentSymbol) return;

            const propertyName = left.property.name;
            const signalInput = findSignalInputDeclaration(
                componentSymbol,
                propertyName
            );
            if (!signalInput){
                return;
            }

            const right = node.right;
            checkSignalInvocation(right, propertyName);
        }

        return {
            CallExpression: handleOpenCallExpression,
            AssignmentExpression: handleAssignmentExpression,
        };
    },
});
