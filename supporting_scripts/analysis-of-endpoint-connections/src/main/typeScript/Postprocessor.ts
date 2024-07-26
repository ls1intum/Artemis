import {Preprocessor} from "./Preprocessor";
import { simpleTraverse, TSESTree } from '@typescript-eslint/typescript-estree';

interface RestCall {
    method: string;
    url: string;
    line: number;
    fileName: string;
}

enum ParsingResultType {
    EVALUATE_TEMPLATE_LITERAL_SUCCESS,
    EVALUATE_TEMPLATE_LITERAL_FAILURE,
    EVALUATE_BINARY_EXPRESSION_SUCCESS,
    EVALUATE_BINARY_EXPRESSION_FAILURE,
    EVALUATE_MEMBER_EXPRESSION_SUCCESS,
    EVALUATE_MEMBER_EXPRESSION_FAILURE,
    EVALUATE_IDENTIFIER_SUCCESS,
    EVALUATE_IDENTIFIER_FAILURE,
    EVALUATE_LITERAL_SUCCESS,
    EVALUATE_LITERAL_FAILURE,
    GET_VARIABLE_FROM_METHOD_SUCCESS,
    GET_VARIABLE_FROM_METHOD_FAILURE,
    EVALUATE_URL_SUCCESS,
    EVALUATE_URL_FAILURE,
    GET_URLS_FROM_CALLING_METHODS_SUCCESS,
    GET_URLS_FROM_CALLING_METHODS_FAILURE,
    GET_URL_FROM_GETTER_SUCCESS,
    GET_URL_FROM_GETTER_FAILURE,

    UNABLE_TO_EVALUATE, // TODO, Incorporate this Type when no matching URL Parser is found
}

class ParsingResult {
    resultType: ParsingResultType;
    result: string[];

    constructor(resultType: ParsingResultType, result: string[]) {
        this.resultType = resultType;
        this.result = result;
    }
}

export class Postprocessor {
    static filesWithRestCalls: { filePath: string, restCalls: RestCall[]}[] = [];
    private restCalls: RestCall[] = [];
    private readonly filePath: string;
    private ast: TSESTree.Program;

    constructor(filePath: string) {
        this.filePath = filePath;
        this.ast = Preprocessor.parseTypeScriptFile(Preprocessor.pathPrefix + filePath)
    }

    extractRestCalls() {
        this.extractRestCallsFromProgram();
    }

    extractRestCallsFromProgram() {
        this.ast.body.forEach(node => {
            if (node.type === 'ExportNamedDeclaration' && node.declaration?.type === 'ClassDeclaration') {
                this.extractRestCallsFromClassDeclaration(node.declaration);
            } else if (node.type === 'ClassDeclaration') { // Todo: Is this needed? This could cause REST Calls do be identified twice
                this.extractRestCallsFromClassDeclaration(node);
            }
        });
        if (this.restCalls.length > 0) {
            Postprocessor.filesWithRestCalls.push( {filePath: this.filePath, restCalls: this.restCalls} );
        }
    }

    extractRestCallsFromClassDeclaration(classBody: TSESTree.ClassDeclaration) {
        for (const propertyDefinition of classBody.body.body) {
            if (propertyDefinition.type === 'MethodDefinition' && propertyDefinition.key.type === 'Identifier') {
                this.extractRestCallsFromMethodDefinition(propertyDefinition, classBody);
            }
        }
    }

    extractRestCallsFromMethodDefinition(methodDefinition: TSESTree.MethodDefinition, classBody: TSESTree.ClassDeclaration) {
        simpleTraverse(methodDefinition, {
            enter: (node) => {
                if (node.type === 'CallExpression') {
                    if (node.callee.type === 'MemberExpression' ) {
                        const calleeIsHttpClient = this.isCalleeHttpClient(node.callee);

                        if (calleeIsHttpClient && (node.callee.object.type === 'Identifier' || node.callee.object.type === 'MemberExpression')) {
                            if (node.callee.property.type === 'Identifier') {
                                if (['get', 'post', 'put', 'delete', 'patch'].includes(node.callee.property.name)) {
                                    const method = node.callee.property.name.toUpperCase();
                                    let urlEvaluationResult: ParsingResult = new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, []);
                                    const line = node.loc.start.line;

                                    if (node.arguments.length > 0) {
                                        urlEvaluationResult = this.evaluateUrl(node.arguments[0], methodDefinition, node, classBody);
                                    }

                                    const fileName = this.filePath;
                                    if (urlEvaluationResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
                                        for (let url of urlEvaluationResult.result) {
                                            this.restCalls.push({ method, url, line, fileName });
                                        }
                                    }
                                }
                            }
                        } else {
                            return;
                        }
                    }
                }
            },
        });
    }

    evaluateUrl(node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration) {
        let resultType = ParsingResultType.EVALUATE_URL_FAILURE;
        let result: string[] = [];

        if (node.type === 'Literal' && node.value) {
            result.push(node.value.toString());
            resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
        }

        if (node.type === 'TemplateLiteral') {
            const tempResult = this.evaluateTemplateLiteralExpression(node, methodDefinition, restCall, classBody);
            if (tempResult.resultType === ParsingResultType.EVALUATE_TEMPLATE_LITERAL_SUCCESS) {
                resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                result = tempResult.result;
            } else {

            }
        }

        if (node.type === 'MemberExpression') {
            if (node.object.type === 'ThisExpression' && node.property.type === 'Identifier') {
                let tempResult = this.getMemberExpressionValueFromNameAndClassBody(node.property.name, classBody);

                if (tempResult.resultType === ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE) {
                    // If the member expression value is not found in the class body, try to evaluate it from the Child Classes
                    tempResult = this.evaluateMemberVariableFromChildClasses(classBody, node.property.name);
                    if (tempResult.resultType === ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE) {
                        // if the member expression value is not found in the child classes, the value is defined dynamically and a placeholder is returned.
                        result.push('${' + `this.${node.property.name}` + '}');
                        resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                    } else  {
                        // else, the values found in the child classes are returned.
                        result = tempResult.result;
                        resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                    }
                } else {
                    // if the member expression value is found in the class body, the value is returned.
                    resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                    result = tempResult.result;
                }
            }
        }

        if (node.type === 'Identifier') {
            let tempResult = this.getMethodVariableValueFromNameAndMethod(node.name, node, methodDefinition, restCall, classBody);
            if (tempResult.resultType === ParsingResultType.GET_VARIABLE_FROM_METHOD_SUCCESS) {
                resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                result = tempResult.result;
            } else {
                // If the value is not found in the method, try to find method calls from the method name and return the parameter value. If there are method calls that pass on the URL, it is the complete URL that is passed on.
                tempResult = new ParsingResult(ParsingResultType.GET_URLS_FROM_CALLING_METHODS_FAILURE, []);

                if (methodDefinition.key.type === 'Identifier') {
                    tempResult = this.findMethodCallsFromMethodNameAndClassBody(methodDefinition.key.name, classBody, restCall);
                }

                if (tempResult.resultType === ParsingResultType.GET_URLS_FROM_CALLING_METHODS_SUCCESS) {
                    result = tempResult.result;
                    resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                } else {
                    // If the value is not found in the method calls, the value is defined dynamically and a placeholder is returned.
                    result.push('${' + node.name + '}');
                    resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                }
            }
        }

        if (node.type === 'BinaryExpression') {
            let tempResult = this.evaluateBinaryExpression(node, methodDefinition, restCall, classBody);
            if (tempResult.resultType === ParsingResultType.EVALUATE_BINARY_EXPRESSION_SUCCESS) {
                resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
                result = tempResult.result;
            } else {
                result.push('Unknown URL');
                resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
            }
        }

        if (node.type === 'CallExpression') {
            // Sometimes, The ResourceURL is built and fetched using a getterMethod
            let tempResult= this.getUrlFromGetterMethodCall(node, classBody);
            if (tempResult.resultType === ParsingResultType.GET_URL_FROM_GETTER_SUCCESS) {
                result = tempResult.result;
                resultType = ParsingResultType.EVALUATE_URL_SUCCESS;
            } else {
                resultType = ParsingResultType.UNABLE_TO_EVALUATE;
            }
        }

        return new ParsingResult(resultType, result);
    }

    isCalleeHttpClient(callee: TSESTree.MemberExpression) {
        if (callee.object.type === 'MemberExpression' && callee.object.property.type === 'Identifier') {
            return this.getMemberExpressionTypeFromName(callee.object.property.name) === 'HttpClient';
        }
        return false;
    }

    evaluateTemplateLiteralExpression(templateLiteral: TSESTree.TemplateLiteral, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration) {
        let resultType = ParsingResultType.EVALUATE_TEMPLATE_LITERAL_SUCCESS;
        let evaluatedURL = templateLiteral.quasis.reduce((acc, quasi, index) => {
            let expression = 'Unknown URL';
            if (index < templateLiteral.expressions.length) {
                let subResult = this.evaluateUrl(templateLiteral.expressions[index], methodDefinition, restCall, classBody);

                if (subResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
                    if (subResult.result.length === 1) {
                        expression = subResult.result[0];
                    }
                } else {
                    resultType = ParsingResultType.EVALUATE_TEMPLATE_LITERAL_FAILURE;
                }

                return acc + quasi.value.raw + expression;
            } else {
                return acc;
            }
        }, '');

        return new ParsingResult(resultType, [evaluatedURL]);
    }

    evaluateBinaryExpression(binaryExpression: TSESTree.BinaryExpression, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration) {
        const left = binaryExpression.left;
        const right = binaryExpression.right;
        let leftParsingResult = this.evaluateUrl(left, methodDefinition, restCall, classBody);
        let rightParsingResult = this.evaluateUrl(right, methodDefinition, restCall, classBody);

        let parsingResult: string[] = [];
        let parsingResultType = ParsingResultType.EVALUATE_BINARY_EXPRESSION_FAILURE;

        if (leftParsingResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS && rightParsingResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
            parsingResult.push(leftParsingResult.result[0] + rightParsingResult.result[0]);
            parsingResultType = ParsingResultType.EVALUATE_BINARY_EXPRESSION_SUCCESS;
        }

        return new ParsingResult(parsingResultType, parsingResult);
    }

    // /**
    //  * Evaluates the URL from a given AST node within the context of a method definition and a REST call expression.
    //  * This method processes different types of nodes to extract or compute the URL string or parts of it.
    //  * It supports handling `TemplateLiteral`, `BinaryExpression`, `MemberExpression`, `Identifier`, and `Literal` nodes.
    //  * The method aims to return a single URL string or an array of strings if multiple URLs are derived from the node.
    //  * If the node type does not match any of the expected types or if the URL cannot be determined, it returns 'Unknown URL'.
    //  *
    //  * @param node - The AST node to evaluate for URL extraction.
    //  * @param methodDefinition - The method definition context in which the node is evaluated.
    //  * @param restCall - The REST call expression context for the evaluation.
    //  * @returns The evaluated URL as a string or an array of strings if multiple URLs are found, or 'Unknown URL' if the URL cannot be determined.
    //  */
    // evaluateUrl(node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration) {
    //     let evaluationResult: ParsingResult = new ParsingResult();
    //     if (node.type === 'TemplateLiteral') {
    //         evaluationResult = this.evaluateTemplateLiteralExpression(node, classBody);
    //     }
    //     else if (node.type === 'BinaryExpression') {
    //         return this.evaluateBinaryExpression(node, methodDefinition, restCall, classBody);
    //     } else if (node.type === 'MemberExpression' && node.property.type === 'Identifier' && node.object.type === 'ThisExpression') { // TODO ========================
    //         result =  this.getMemberExpressionValueFromName(node.property.name);
    //         if (result === 'Unknown URL' && Preprocessor.PREPROCESSING_RESULTS.has(this.getClassNameFromClassBody(classBody))) {
    //             result = this.evaluateMemberVariableFromConstructorCall(classBody, node.property.name);
    //         }
    //         return result;
    //     } else if (node.type === 'Identifier') {
    //         result = this.getMethodVariableValueFromNameAndMethod(node.name, methodDefinition, classBody);
    //         if (result === 'Unknown URL' && methodDefinition.key.type === 'Identifier') {
    //             let tempResult: string[] = [];
    //             for (let methodCall of this.findMethodCallsFromMethodName(methodDefinition.key.name)) {
    //                 tempResult.push(methodCall.parameterValue);
    //             }
    //             result = tempResult;
    //         }
    //     } else if (node.type === 'Literal' && node.value) {
    //         result = node.value.toString();
    //     }
    //     return evaluationResult;
    // }

    // /**
    //  * Searches for method calls of a given method name within the AST of the current file.
    //  * This method iterates over all nodes in the AST's body. For each node that is a class declaration
    //  * (either directly or through an export declaration), it delegates the search to `findMethodCallsFromMethodNameAndClassBody`,
    //  * which looks for method calls within the class body that match the given method name.
    //  *
    //  * @param methodName - The name of the method to search for calls to.
    //  * @returns An array of objects, each containing a `callExpression` (the AST node representing the call expression)
    //  *          and a `parameterValue` (the value of the parameter passed to the method call, as a string).
    //  */
    // findMethodCallsFromMethodName(methodName: string) {
    //     let methodCalls: { callExpression: TSESTree.CallExpression, parameterValue: string }[] = [];
    //     for (const node of this.ast.body) {
    //         let classBody;
    //         if (node.type === 'ExportNamedDeclaration' && node.declaration?.type === 'ClassDeclaration') {
    //             methodCalls.push(... this.findMethodCallsFromMethodNameAndClassBody(methodName, node.declaration));
    //         } else if (node.type === 'ClassDeclaration') {
    //             methodCalls.push(... this.findMethodCallsFromMethodNameAndClassBody(methodName, node));
    //         }
    //     }
    //     return methodCalls;
    // }

    /**
     * Finds and collects method calls by a specified method name within a given class body.
     * This function iterates through all method definitions in the class body. For each method,
     * it uses a simple traversal to search for call expressions that match the specified method name.
     * If a matching call expression is found, the function extracts the value of the first argument
     * (assuming it's an identifier) and stores both the call expression and the extracted parameter value
     * in an array. This array is then returned, providing insights into where and how the specified method
     * is being called within the class.
     *
     * @param methodName - The name of the method to search for within the class body.
     * @param classBody - The AST representation of the class to search within.
     * @returns An array of objects, each containing a `callExpression` and a `parameterValue`.
     *          `callExpression` is the AST node representing the found call expression,
     *          and `parameterValue` is the value of the first argument passed to the call expression.
     */
    findMethodCallsFromMethodNameAndClassBody(methodName: string, classBody: TSESTree.ClassDeclaration, restCall: TSESTree.CallExpression) {
        let methodCalls: { callExpression: TSESTree.CallExpression, parameterValue: string }[] = [];
        let parsingResultType = ParsingResultType.GET_URLS_FROM_CALLING_METHODS_FAILURE;

        for (const methodDefinition of classBody.body.body) {
            if (methodDefinition.type === 'MethodDefinition' && methodDefinition.key.type === 'Identifier') {
                simpleTraverse(methodDefinition, {
                    enter: (node) => {
                        if (node.type === 'CallExpression' && node.callee.type === 'MemberExpression' && node.callee.object.type === 'ThisExpression' && node.callee.property.type === 'Identifier' &&
                            node.callee.property.name === methodName && node.arguments[0].type === 'Identifier') {
                            let parameterValue = this.getMethodVariableValueFromNameAndMethod(node.arguments[0].name, node, methodDefinition, restCall, classBody);
                            if (parameterValue.resultType === ParsingResultType.GET_VARIABLE_FROM_METHOD_SUCCESS) {
                                methodCalls.push({ callExpression: node, parameterValue: parameterValue.result[0] });
                            }
                        }
                    }
                });
            }
        }

        let result: string[] = [];
        for (const methodCall of methodCalls) {
            result.push(methodCall.parameterValue);
        }

        if (result.length > 0) {
            parsingResultType = ParsingResultType.GET_URLS_FROM_CALLING_METHODS_SUCCESS;
        }
        return new ParsingResult(parsingResultType, result);
    }

    getMemberExpressionValueFromNameAndClassBody(name: string, classBody: TSESTree.ClassDeclaration) {
        let memberExpressionResult: string[] = [];
        let resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE;
        simpleTraverse(classBody, {
            enter(node) {
                if (node.type === 'PropertyDefinition' && node.value?.type === 'Literal' && node.key.type === 'Identifier' && node.value.value) {
                    if (node.key.name === name) {
                        memberExpressionResult.push(node.value.value.toString());
                        resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_SUCCESS;
                        return;
                    }
                }
            }
        });
        return new ParsingResult(resultType, memberExpressionResult);
    }

    getMemberExpressionTypeFromName(name: string) {
        let memberExpressionType = 'Unknown Type';
        simpleTraverse(this.ast, {
            enter: (node) => {
                if (node.type === 'PropertyDefinition' && node.value?.type === 'Literal' && node.key.type === 'Identifier' && node.value.value && node.key.name === name) {
                    memberExpressionType = node.value.type;
                } else if (node.type === 'TSParameterProperty' && node.parameter.type === 'Identifier' && node.parameter.name === name &&
                    node.parameter.typeAnnotation?.type === 'TSTypeAnnotation' && node.parameter.typeAnnotation.typeAnnotation.type === 'TSTypeReference' &&
                    node.parameter.typeAnnotation.typeAnnotation.typeName.type === 'Identifier') {
                    memberExpressionType = node.parameter.typeAnnotation.typeAnnotation.typeName.name;
                }
            }
        });
        return memberExpressionType;
    }

    getUrlFromGetterMethodCall(callExpression: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration) {
        let parsingResultType = ParsingResultType.GET_URL_FROM_GETTER_FAILURE;
        let parsingResult: string[] = [];

        if (callExpression.callee.type === 'MemberExpression' && callExpression.callee.property.type === 'Identifier') {
            const methodName = callExpression.callee.property.name;
            for (const methodDefinition of classBody.body.body) {
                // find the getter method
                if (methodDefinition.type === 'MethodDefinition' && methodDefinition.key.type === 'Identifier' && methodDefinition.key.name === methodName && methodDefinition.value.type === 'FunctionExpression') {
                    for (const node of methodDefinition.value.body.body) {
                        if (node.type === 'ReturnStatement' && node.argument) {
                            const tempResult = this.evaluateUrl(node.argument, methodDefinition, callExpression, classBody);
                            if (tempResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
                                parsingResult = tempResult.result;
                                parsingResultType = ParsingResultType.GET_URL_FROM_GETTER_SUCCESS;
                            }
                        }
                    }
                }
            }
        }

        return new ParsingResult(parsingResultType, parsingResult);
    }

    getMethodVariableValueFromNameAndMethod(name: string, node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration) {
        let result: string[] = [];
        let resultType = ParsingResultType.GET_VARIABLE_FROM_METHOD_FAILURE;
        simpleTraverse(methodDefinition, {
            enter: (node) => {
                if (node.type === 'VariableDeclaration') {
                    for (let decl of node.declarations) {
                        if (decl.id.type === 'Identifier' && decl.id.name === name && decl.init) {
                            const tempResult = this.evaluateUrl(decl.init, methodDefinition, restCall, classBody);
                            if (tempResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
                                result = tempResult.result;
                                resultType = ParsingResultType.GET_VARIABLE_FROM_METHOD_SUCCESS;
                            }
                            return;
                        }
                    }
                }
            }
        });
        return new ParsingResult(resultType, result);
    }

    getClassNameFromClassBody(classBody: TSESTree.ClassDeclaration) {
        if (classBody.id?.type === 'Identifier') {
            return classBody.id.name;
        }
        return 'Unknown URL';
    }

    getConstructorArgumentsFromClassBody(classBody: TSESTree.ClassBody) {
        for (let node of classBody.body) {
            if (node.type === 'MethodDefinition' && node.key.type === 'Identifier' && node.key.name === 'constructor') {
                return node.value.params;
            }
        }

        return [];
    }

    evaluateMemberVariableFromChildClasses(classBody: TSESTree.ClassDeclaration, memberExprKey: string) {
        let memberExpressionResult: string[] = [];
        let resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE;

        const superClass = Preprocessor.PREPROCESSING_RESULTS.get(this.getClassNameFromClassBody(classBody));
        if (superClass) {
            const constructorArguments = this.getConstructorArgumentsFromClassBody(classBody.body);
            for (let superConstructorCallArguments of superClass.superConstructorCalls) {
                for (let i = 0; i < superConstructorCallArguments.arguments.length; i++) {
                    let constructorArgument = constructorArguments[i];
                    if (superConstructorCallArguments.arguments[i] !== '' && constructorArgument.type === 'TSParameterProperty'
                    && constructorArgument.parameter.type === 'Identifier' && constructorArgument.parameter.name === memberExprKey) {
                        memberExpressionResult.push(superConstructorCallArguments.arguments[i]);
                        resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_SUCCESS;
                    }
                }
            }
        }

        return new ParsingResult(resultType, memberExpressionResult);
    }
}
