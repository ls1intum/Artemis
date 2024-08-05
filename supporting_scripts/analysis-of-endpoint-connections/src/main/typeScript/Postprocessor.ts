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
    GET_VARIABLE_FROM_METHOD_SUCCESS,
    GET_VARIABLE_FROM_METHOD_FAILURE,
    EVALUATE_URL_SUCCESS,
    EVALUATE_URL_FAILURE,
    GET_URLS_FROM_CALLING_METHODS_SUCCESS,
    GET_URLS_FROM_CALLING_METHODS_FAILURE,
    GET_URL_FROM_GETTER_SUCCESS,
    GET_URL_FROM_GETTER_FAILURE,

    UNABLE_TO_EVALUATE, // no matching URL Parser is found
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
    static filesWithRestCalls: { filePath: string, restCalls: RestCall[] }[] = [];
    private readonly restCalls: RestCall[] = [];
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
            } else if (node.type === 'ClassDeclaration') {
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

    evaluateUrl(node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        switch (node.type) {
            case 'Literal':
                return this.evaluateLiteralHelper(node);
            case 'TemplateLiteral':
                return this.evaluateTemplateLiteralHelper(node, methodDefinition, restCall, classBody);
            case 'MemberExpression':
                return this.evaluateMemberExpressionHelper(node, classBody);
            case 'Identifier':
                return this.evaluateIdentifierHelper(node, methodDefinition, restCall, classBody);
            case 'BinaryExpression':
                return this.evaluateBinaryExpressionHelper(node, methodDefinition, restCall, classBody);
            case 'CallExpression':
                return this.evaluateCallExpressionHelper(node, classBody);
            default:
                return new ParsingResult(ParsingResultType.UNABLE_TO_EVALUATE, ['FAILED_TO_EVALUATE_URL']);
        }
    }

    evaluateLiteralHelper(node: TSESTree.Literal): ParsingResult {
        if (node.value) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, [node.value.toString()]);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, []);
    }

    evaluateTemplateLiteralHelper(node: TSESTree.TemplateLiteral, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        const tempResult = this.evaluateTemplateLiteralExpression(node, methodDefinition, restCall, classBody);
        if (tempResult.resultType === ParsingResultType.EVALUATE_TEMPLATE_LITERAL_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        } else if (tempResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
            return new ParsingResult(ParsingResultType.UNABLE_TO_EVALUATE, []);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, []);
    }

    evaluateMemberExpressionHelper(node: TSESTree.MemberExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        if (node.object.type === 'ThisExpression' && node.property.type === 'Identifier') {
            let tempResult = this.getMemberExpressionValueFromNameAndClassBody(node.property.name, classBody);
            if (tempResult.resultType === ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE) {
                tempResult = this.evaluateMemberVariableFromChildClasses(classBody, node.property.name);
                if (tempResult.resultType === ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE) {
                    return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, [`\${this.${node.property.name}}`]);
                }
            }
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        } else if (node.object.type === 'Identifier' && node.property.type === 'Identifier') {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, [`\${${node.object.name}.${node.property.name}}`]);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, []);
    }

    evaluateIdentifierHelper(node: TSESTree.Identifier, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        let tempResult = this.getMethodVariableValueFromNameAndMethod(node.name, node, methodDefinition, restCall, classBody);
        if (tempResult.resultType === ParsingResultType.GET_VARIABLE_FROM_METHOD_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        }

        if (methodDefinition.key.type === 'Identifier') {
            tempResult = this.findMethodCallsFromMethodNameAndClassBody(methodDefinition.key.name, classBody, restCall);
        }

        if (tempResult.resultType === ParsingResultType.GET_URLS_FROM_CALLING_METHODS_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, [`\${${node.name}}`]);
    }

    evaluateBinaryExpressionHelper(node: TSESTree.BinaryExpression, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        const tempResult = this.evaluateBinaryExpression(node, methodDefinition, restCall, classBody);
        if (tempResult.resultType === ParsingResultType.EVALUATE_BINARY_EXPRESSION_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        } else if (tempResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
            return new ParsingResult(ParsingResultType.UNABLE_TO_EVALUATE, []);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, ['Unknown URL']);
    }

    evaluateCallExpressionHelper(node: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        const tempResult = this.getUrlFromGetterMethodCall(node, classBody);
        if (tempResult.resultType === ParsingResultType.GET_URL_FROM_GETTER_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        }
        return new ParsingResult(ParsingResultType.UNABLE_TO_EVALUATE, []);
    }

    isCalleeHttpClient(callee: TSESTree.MemberExpression): boolean {
        if (callee.object.type === 'MemberExpression' && callee.object.property.type === 'Identifier') {
            return this.getMemberExpressionTypeFromName(callee.object.property.name) === 'HttpClient';
        }
        return false;
    }

    evaluateTemplateLiteralExpression(templateLiteral: TSESTree.TemplateLiteral, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        let resultType = ParsingResultType.EVALUATE_TEMPLATE_LITERAL_SUCCESS;

        let evaluatedURL = '';
        for (let index = 0; index < templateLiteral.quasis.length; index++) {
            const quasi = templateLiteral.quasis[index];
            let expression = 'Unknown URL';
            if (index < templateLiteral.expressions.length) {
                let subResult = this.evaluateUrl(templateLiteral.expressions[index], methodDefinition, restCall, classBody);

                if (subResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
                    if (subResult.result.length === 1) {
                        expression = subResult.result[0];
                    }
                } else if (subResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
                    resultType = ParsingResultType.UNABLE_TO_EVALUATE;
                    break;
                } else {
                    resultType = ParsingResultType.EVALUATE_TEMPLATE_LITERAL_FAILURE;
                }

                evaluatedURL += quasi.value.raw + expression;
            } else {
                evaluatedURL += quasi.value.raw;
            }
        }

        return new ParsingResult(resultType, [evaluatedURL]);
    }

    evaluateBinaryExpression(binaryExpression: TSESTree.BinaryExpression, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        const left = binaryExpression.left;
        const right = binaryExpression.right;
        let leftParsingResult = this.evaluateUrl(left, methodDefinition, restCall, classBody);
        let rightParsingResult = this.evaluateUrl(right, methodDefinition, restCall, classBody);

        let parsingResult: string[] = [];
        let parsingResultType = ParsingResultType.EVALUATE_BINARY_EXPRESSION_FAILURE;

        if (leftParsingResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS && rightParsingResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
            parsingResult.push(leftParsingResult.result[0] + rightParsingResult.result[0]);
            parsingResultType = ParsingResultType.EVALUATE_BINARY_EXPRESSION_SUCCESS;
        } else if (leftParsingResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE || rightParsingResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
            parsingResultType = ParsingResultType.UNABLE_TO_EVALUATE;
        }

        return new ParsingResult(parsingResultType, parsingResult);
    }

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
    findMethodCallsFromMethodNameAndClassBody(methodName: string, classBody: TSESTree.ClassDeclaration, restCall: TSESTree.CallExpression): ParsingResult {
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
                            } else if (parameterValue.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
                                parsingResultType = ParsingResultType.UNABLE_TO_EVALUATE;
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

    getMemberExpressionValueFromNameAndClassBody(name: string, classBody: TSESTree.ClassDeclaration): ParsingResult {
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

    getMemberExpressionTypeFromName(name: string): string {
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

    getUrlFromGetterMethodCall(callExpression: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
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
                            } else if (tempResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
                                parsingResultType = ParsingResultType.UNABLE_TO_EVALUATE;
                            }
                        }
                    }
                }
            }
        }

        return new ParsingResult(parsingResultType, parsingResult);
    }

    getMethodVariableValueFromNameAndMethod(name: string, node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
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
                            } else if (tempResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
                                resultType = ParsingResultType.UNABLE_TO_EVALUATE;
                            }
                            return;
                        }
                    }
                }
            }
        });
        return new ParsingResult(resultType, result);
    }

    getClassNameFromClassBody(classBody: TSESTree.ClassDeclaration): string {
        if (classBody.id?.type === 'Identifier') {
            return classBody.id.name;
        }
        return 'Unknown URL';
    }

    getConstructorArgumentsFromClassBody(classBody: TSESTree.ClassBody): TSESTree.Parameter[] {
        for (let node of classBody.body) {
            if (node.type === 'MethodDefinition' && node.key.type === 'Identifier' && node.key.name === 'constructor') {
                return node.value.params;
            }
        }

        return [];
    }

    evaluateMemberVariableFromChildClasses(classBody: TSESTree.ClassDeclaration, memberExprKey: string): ParsingResult {
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
