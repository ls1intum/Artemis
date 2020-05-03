'use strict';
// Copied from: https://github.com/thymikee/jest-preset-angular
/*
 * Code is inspired by
 * https://github.com/kulshekhar/ts-jest/blob/25e1c63dd3797793b0f46fa52fdee580b46f66ae/src/transformers/hoist-jest.ts
 *
 */
Object.defineProperty(exports, '__esModule', { value: true });
/** Angular component decorator TemplateUrl property name */
var TEMPLATE_URL = 'templateUrl';
/** Angular component decorator StyleUrls property name */
var STYLE_URLS = 'styleUrls';
/** Angular component decorator Styles property name */
var STYLES = 'styles';
/** Angular component decorator Template property name */
var TEMPLATE = 'template';
/** Node require function name */
var REQUIRE = 'require';
/**
 * Property names inside the decorator argument to transform
 */
var TRANSFORM_PROPS = [TEMPLATE_URL, STYLES, STYLE_URLS];
/**
 * Transformer ID
 * @internal
 */
exports.name = 'angular-component-inline-template-strip-styles';
// increment this each time the code is modified
/**
 * Transformer Version
 * @internal
 */
exports.version = 1;
/**
 * The factory of hoisting transformer factory
 * @internal
 */
function factory(cs) {
    /**
     * Our compiler (typescript, or a module with typescript-like interface)
     */
    var ts = cs.compilerModule;
    /**
     * Traverses the AST down to the relevant assignments in the decorator
     * argument and returns them in an array.
     */
    function isPropertyAssignmentToTransform(node) {
        return ts.isPropertyAssignment(node) && ts.isIdentifier(node.name) && TRANSFORM_PROPS.includes(node.name.text);
    }
    /**
     * Clones the assignment and manipulates it depending on its name.
     * @param node the property assignment to change
     */
    function transfromPropertyAssignmentForJest(node) {
        var mutableAssignment = ts.getMutableClone(node);
        var assignmentNameText = mutableAssignment.name.text;
        switch (assignmentNameText) {
            case TEMPLATE_URL:
                // reuse the right-hand-side literal from the assignment
                var templatePathLiteral = mutableAssignment.initializer;
                // fix templatePathLiteral if it was a non-relative path
                if (ts.isStringLiteral(mutableAssignment.initializer)) {
                    var templatePathStringLiteral = mutableAssignment.initializer;
                    // match if it starts with ./ or ../ or /
                    if (templatePathStringLiteral.text && !templatePathStringLiteral.text.match(/^(\.\/|\.\.\/|\/)/)) {
                        // make path relative by appending './'
                        templatePathLiteral = ts.createStringLiteral('./' + templatePathStringLiteral.text);
                    }
                }
                // replace 'templateUrl' with 'template'
                mutableAssignment.name = ts.createIdentifier(TEMPLATE);
                // replace current initializer with require(path)
                mutableAssignment.initializer = ts.createCall(
                    /* expression */ ts.createIdentifier(REQUIRE),
                    /* type arguments */ undefined,
                    /* arguments array */ [templatePathLiteral],
                );
                break;
            case STYLES:
            case STYLE_URLS:
                // replace initializer array with empty array
                mutableAssignment.initializer = ts.createArrayLiteral();
                break;
        }
        return mutableAssignment;
    }
    /**
     * Create a source file visitor which will visit all nodes in a source file
     * @param ctx The typescript transformation context
     * @param _ The owning source file
     */
    function createVisitor(ctx, _) {
        /**
         * Our main visitor, which will be called recursively for each node in the source file's AST
         * @param node The node to be visited
         */
        var visitor = function (node) {
            var resultNode;
            // before we create a deep clone to modify, we make sure that
            // this is an assignment which we want to transform
            if (isPropertyAssignmentToTransform(node)) {
                // get transformed node with changed properties
                resultNode = transfromPropertyAssignmentForJest(node);
            } else {
                // look for interesting assignments inside this node
                resultNode = ts.visitEachChild(node, visitor, ctx);
            }
            // finally return the currently visited node
            return resultNode;
        };
        return visitor;
    }
    return function (ctx) {
        return function (sf) {
            return ts.visitNode(sf, createVisitor(ctx, sf));
        };
    };
}
exports.factory = factory;
