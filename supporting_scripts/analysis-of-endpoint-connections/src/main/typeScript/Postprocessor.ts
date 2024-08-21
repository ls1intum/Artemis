import {Preprocessor} from "./Preprocessor";
import { simpleTraverse, TSESTree } from '@typescript-eslint/typescript-estree';

interface RestCall {
    method: string;
    url: string;
    line: number;
    filePath: string;
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
    private readonly ast: TSESTree.Program;

    /**
     * @param filePath - The path of the file being processed.
     * @param ast - The abstract syntax tree (AST) of the processed file.
     */
    constructor(filePath: string, ast: TSESTree.Program) {
        this.filePath = filePath;
        this.ast = ast;
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
            Postprocessor.filesWithRestCalls.push({ filePath: this.filePath, restCalls: this.restCalls });
        }
    }

    /**
     * Extracts REST calls from the program's AST.
     *
     * This method iterates over the top-level nodes in the AST and checks if they are class declarations.
     * If a class declaration is found, it calls `extractRestCallsFromClassDeclaration` to process it.
     * After processing all nodes, if any REST calls were found, it adds them to the static `filesWithRestCalls` array.
     */
    extractRestCallsFromClassDeclaration(classBody: TSESTree.ClassDeclaration) {
        for (const propertyDefinition of classBody.body.body) {
            if (propertyDefinition.type === 'MethodDefinition' && propertyDefinition.key.type === 'Identifier') {
                this.extractRestCallsFromMethodDefinition(propertyDefinition, classBody);
            }
        }
    }

    /**
     * Extracts REST calls from a method definition within a class declaration.
     *
     * This method traverses the AST of the provided method definition to identify HTTP client calls.
     * It checks if the call expression is a member of an HTTP client and if the method name is one of the HTTP methods (GET, POST, PUT, DELETE, PATCH).
     * If a valid HTTP call is found, it evaluates the URL and adds the REST call details to the `restCalls` array.
     *
     * @param methodDefinition - The AST node representing the method definition to extract REST calls from.
     * @param classBody - The AST node representing the class declaration containing the method.
     */
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

                                    const filePath = this.filePath;
                                    if (urlEvaluationResult.resultType === ParsingResultType.EVALUATE_URL_SUCCESS) {
                                        for (const url of urlEvaluationResult.result) {
                                            this.restCalls.push({ method, url, line, filePath });
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

    /**
     * Evaluates a given AST node to determine its URL value.
     *
     * This method takes an AST node and determines its URL value by delegating to helper methods
     * based on the node type. It supports various node types such as Literal, TemplateLiteral,
     * MemberExpression, Identifier, BinaryExpression, and CallExpression.
     *
     * @param node - The AST node to evaluate.
     * @param methodDefinition - The AST node representing the method definition containing the node.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
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

    /**
     * Evaluates a literal AST node to determine its URL value.
     *
     * This method checks if the provided literal node has a value. If it does, it converts the value to a string
     * and returns a ParsingResult indicating success. If the node does not have a value, it returns a ParsingResult
     * indicating failure.
     *
     * @param node - The AST node representing the literal to evaluate.
     * @returns A ParsingResult object containing the evaluation result and the URL if successful.
     */
    evaluateLiteralHelper(node: TSESTree.Literal): ParsingResult {
        if (node.value) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, [node.value.toString()]);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, []);
    }

    /**
     * Evaluates a template literal AST node to determine its URL value.
     * Evaluates a template literal AST node to determine its URL value.
     *
     * This method evaluates the provided template literal node by calling `evaluateTemplateLiteralExpression`.
     * If the evaluation is successful, it returns a ParsingResult indicating success.
     * If the evaluation is unable to determine the URL, it returns a ParsingResult indicating that.
     * Otherwise, it returns a ParsingResult indicating failure.
     *
     * @param node - The AST node representing the template literal to evaluate.
     * @param methodDefinition - The AST node representing the method definition containing the node.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
    evaluateTemplateLiteralHelper(node: TSESTree.TemplateLiteral, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        const tempResult = this.evaluateTemplateLiteralExpression(node, methodDefinition, restCall, classBody);
        if (tempResult.resultType === ParsingResultType.EVALUATE_TEMPLATE_LITERAL_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        } else if (tempResult.resultType === ParsingResultType.UNABLE_TO_EVALUATE) {
            return new ParsingResult(ParsingResultType.UNABLE_TO_EVALUATE, []);
        }
        return new ParsingResult(ParsingResultType.EVALUATE_URL_FAILURE, []);
    }

    /**
     * Evaluates a member expression AST node to determine its URL value.
     *
     * This method checks if the provided member expression node is a `this` expression or an identifier.
     * If it is a `this` expression, it attempts to get the member expression value from the class body.
     * If the evaluation fails, it tries to evaluate the member variable from child classes.
     * If both evaluations fail, it returns a URL template string.
     * If it is an identifier, it returns a URL template string directly.
     *
     * @param node - The AST node representing the member expression to evaluate.
     * @param classBody - The AST node representing the class declaration containing the member expression.
     * @returns A ParsingResult object containing the evaluation result and the URL if successful.
     */
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

    /**
     * Evaluates an identifier AST node to determine its URL value.
     *
     * This method attempts to find the value of the identifier by checking the method's variables.
     * If successful, it returns a ParsingResult indicating success. If not, it searches for method calls
     * within the class body that match the method name and evaluates their URLs.
     * If successful, it returns a ParsingResult indicating success. Otherwise, it returns a string in the format ${variableName}.
     *
     * @param node - The AST node representing the identifier to evaluate.
     * @param methodDefinition - The AST node representing the method definition containing the identifier.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
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

    /**
     * Evaluates a binary expression AST node to determine its URL value.
     *
     * This method evaluates the provided binary expression node by calling `evaluateBinaryExpression`.
     * If the evaluation is successful, it returns a ParsingResult indicating success.
     * If the evaluation is unable to determine the URL, it returns a ParsingResult indicating that.
     * Otherwise, it returns a ParsingResult indicating failure.
     *
     * @param node - The AST node representing the binary expression to evaluate.
     * @param methodDefinition - The AST node representing the method definition containing the node.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
    evaluateCallExpressionHelper(node: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        const tempResult = this.getUrlFromGetterMethodCall(node, classBody);
        if (tempResult.resultType === ParsingResultType.GET_URL_FROM_GETTER_SUCCESS) {
            return new ParsingResult(ParsingResultType.EVALUATE_URL_SUCCESS, tempResult.result);
        }
        return new ParsingResult(ParsingResultType.UNABLE_TO_EVALUATE, []);
    }

    /**
     * Checks if the callee is an HTTP client.
     *
     * This method determines if the provided member expression represents an HTTP client.
     * It checks if the object of the member expression is another member expression and if its property is an identifier.
     * If these conditions are met, it further checks if the type of the member expression is 'HttpClient'.
     *
     * @param callee - The AST node representing the member expression to check.
     * @returns A boolean indicating whether the callee is an HTTP client.
     */
    isCalleeHttpClient(callee: TSESTree.MemberExpression): boolean {
        if (callee.object.type === 'MemberExpression' && callee.object.property.type === 'Identifier') {
            return this.getMemberExpressionTypeFromName(callee.object.property.name) === 'HttpClient';
        }
        return false;
    }

    /**
     * Evaluates a template literal AST node to determine its URL value.
     *
     * This method evaluates the provided template literal node by iterating over its quasis and expressions.
     * It calls `evaluateUrl` on each expression and concatenates the results with the quasi values.
     * If the evaluation of any expression fails, it updates the result type accordingly.
     *
     * @param templateLiteral - The AST node representing the template literal to evaluate.
     * @param methodDefinition - The AST node representing the method definition containing the template literal.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
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

    /**
     * Evaluates a binary expression AST node to determine its URL value.
     *
     * This method evaluates the left and right sides of the provided binary expression node by calling `evaluateUrl`.
     * If both evaluations are successful, it concatenates the results and returns a ParsingResult indicating success.
     * If either evaluation is unable to determine the URL, it returns a ParsingResult indicating that.
     * Otherwise, it returns a ParsingResult indicating failure.
     *
     * @param binaryExpression - The AST node representing the binary expression to evaluate.
     * @param methodDefinition - The AST node representing the method definition containing the binary expression.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
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

    /**
     * Retrieves the value of a member expression from a class body by its name.
     *
     * This method traverses the class body to find a property definition that matches the provided name.
     * If a matching property definition is found and its value is a literal, the value is added to the result array.
     * The result type is updated to indicate success if a matching property is found.
     *
     * @param name - The name of the member expression to find.
     * @param classBody - The AST node representing the class declaration containing the member expression.
     * @returns A ParsingResult object containing the evaluation result and the member expression value(s) if successful.
     */
    getMemberExpressionValueFromNameAndClassBody(name: string, classBody: TSESTree.ClassDeclaration): ParsingResult {
        let memberExpressionResult: string[] = [];
        let resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE;
        simpleTraverse(classBody, {
            enter(node) {
                if (node.type === 'PropertyDefinition' && node.value?.type === 'Literal' && node.key.type === 'Identifier' && node.value.value && node.key.name === name) {
                    memberExpressionResult.push(node.value.value.toString());
                    resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_SUCCESS;
                    return;
                }
            }
        });
        return new ParsingResult(resultType, memberExpressionResult);
    }

    /**
     * Retrieves the type of a member expression by its name.
     *
     * This method traverses the AST to find a property definition or parameter property that matches the provided name.
     * If a matching property definition is found, it returns the type of the property.
     * If a matching parameter property is found, it returns the type of the parameter.
     * If no match is found, it returns 'Unknown Type'.
     *
     * @param name - The name of the member expression to find the type for.
     * @returns A string representing the type of the member expression.
     */
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

    /**
     * Retrieves the URL from a getter method call.
     *
     * This method evaluates a call expression to determine if it is a call to a getter method.
     * If it is, it traverses the class body to find the corresponding method definition.
     * It then evaluates the return statement of the getter method to extract the URL.
     *
     * @param callExpression - The AST node representing the call expression to evaluate.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the URL(s) if successful.
     */
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

    /**
     * Retrieves the value of a variable from a method by its name.
     *
     * This method traverses the provided method definition to find a variable declaration that matches the provided name.
     * If a matching variable declaration is found and its initializer can be evaluated to a URL, the value is added to the result array.
     * The result type is updated to indicate success if a matching variable is found.
     *
     * @param name - The name of the variable to find.
     * @param node - The AST node representing the current node in the traversal.
     * @param methodDefinition - The AST node representing the method definition containing the variable.
     * @param restCall - The AST node representing the REST call expression.
     * @param classBody - The AST node representing the class declaration containing the method.
     * @returns A ParsingResult object containing the evaluation result and the variable value(s) if successful.
     */
    getMethodVariableValueFromNameAndMethod(name: string, node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression, classBody: TSESTree.ClassDeclaration): ParsingResult {
        let result: string[] = [];
        let resultType = ParsingResultType.GET_VARIABLE_FROM_METHOD_FAILURE;
        simpleTraverse(methodDefinition, {
            enter: (node) => {
                if (node.type === 'VariableDeclaration') {
                    for (const decl of node.declarations) {
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

    /**
     * Retrieves the class name from a class declaration AST node.
     *
     * This method checks if the provided class declaration node has an identifier.
     * If it does, it returns the name of the class. If the class declaration does not have an identifier,
     * it returns 'Unknown URL'.
     *
     * @param classBody - The AST node representing the class declaration to extract the name from.
     * @returns A string representing the name of the class, or 'Unknown URL' if the class has no identifier.
     */
    getClassNameFromClassBody(classBody: TSESTree.ClassDeclaration): string {
        if (classBody.id?.type === 'Identifier') {
            return classBody.id.name;
        }
        return 'Unknown URL';
    }

    /**
     * Retrieves the constructor arguments from a class body.
     *
     * This method iterates over the nodes in the class body to find the constructor method definition.
     * If the constructor is found, it returns the parameters of the constructor.
     * If no constructor is found, it returns an empty array.
     *
     * @param classBody - The AST node representing the class body to extract constructor arguments from.
     * @returns An array of AST nodes representing the parameters of the constructor.
     */
    getConstructorArgumentsFromClassBody(classBody: TSESTree.ClassBody): TSESTree.Parameter[] {
        for (const node of classBody.body) {
            if (node.type === 'MethodDefinition' && node.key.type === 'Identifier' && node.key.name === 'constructor') {
                return node.value.params;
            }
        }

        return [];
    }

    /**
     * Evaluates the value of a member variable from child classes.
     *
     * This method attempts to find the value of a member variable by traversing the class hierarchy.
     * It first retrieves the superclass information from the preprocessing results.
     * Then, it iterates over the constructor arguments of the superclass to find a match for the member variable.
     * If a match is found, the value is added to the result array and the result type is updated to indicate success.
     *
     * @param classBody - The AST node representing the class declaration containing the member variable.
     * @param memberExprKey - The name of the member variable to evaluate.
     * @returns A ParsingResult object containing the evaluation result and the member variable value(s) if successful.
     */
    evaluateMemberVariableFromChildClasses(classBody: TSESTree.ClassDeclaration, memberExprKey: string): ParsingResult {
        let memberExpressionResult: string[] = [];
        let resultType = ParsingResultType.EVALUATE_MEMBER_EXPRESSION_FAILURE;

        const superClass = Preprocessor.PREPROCESSING_RESULTS.get(this.getClassNameFromClassBody(classBody));
        if (superClass) {
            const constructorArguments = this.getConstructorArgumentsFromClassBody(classBody.body);
            for (const superConstructorCallArguments of superClass.superConstructorCalls) {
                for (let i = 0; i < superConstructorCallArguments.arguments.length; i++) {
                    const constructorArgument = constructorArguments[i];
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
