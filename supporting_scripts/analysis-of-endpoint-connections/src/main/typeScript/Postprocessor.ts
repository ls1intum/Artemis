import {Preprocessor} from "./Preprocessor";
import { simpleTraverse, TSESTree } from '@typescript-eslint/typescript-estree';

interface RestCall {
    method: string;
    url: string;
    line: number;
    fileName: string;
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
        // this.extractRestCallsFromTree(this.ast, this.filePath);
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
                this.extractRestCallsFromMethodDefinition(propertyDefinition);
            }
        }
    }

    extractRestCallsFromMethodDefinition(methodDefinition: TSESTree.MethodDefinition) {
        simpleTraverse(methodDefinition, {
            enter: (node) => {
                if (node.type === 'CallExpression') {
                    if (node.callee.type === 'MemberExpression' ) {
                        const calleeIsHttpClient = this.isCalleeHttpClient(node.callee);

                        if (calleeIsHttpClient && (node.callee.object.type === 'Identifier' || node.callee.object.type === 'MemberExpression')) {
                            // if (node.callee.object.type === 'httpClient') {
                            // console.log('callee.object: ' + node.callee.object);
                            if (node.callee.property.type === 'Identifier') {
                                if (['get', 'post', 'put', 'delete', 'patch'].includes(node.callee.property.name)) {
                                    const method = node.callee.property.name.toUpperCase();
                                    let url: string | string[] = 'Unknown URL';
                                    const line = node.loc.start.line;

                                    if (node.arguments.length > 0) {
                                        // Check if the first argument is a TemplateLiteral
                                        url = this.evaluateUrl(node.arguments[0], methodDefinition, node);
                                    }

                                    const fileName = this.filePath;
                                    if (typeof url === 'string') {
                                        this.restCalls.push({ method, url, line, fileName });
                                    } else {
                                        for (let urlPart of url) {
                                            this.restCalls.push({method, url: urlPart, line, fileName});
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

    isCalleeHttpClient(callee: TSESTree.MemberExpression) {
        if (callee.object.type === 'MemberExpression' && callee.object.property.type === 'Identifier') {
            return this.getMemberExpressionTypeFromName(callee.object.property.name) === 'HttpClient';
        }

        return false;
    }

    evaluateTemplateLiteralExpression(templateLiteral: TSESTree.TemplateLiteral): string {
        return templateLiteral.quasis.reduce((acc, quasi, index) => {
            let expression = '';
            if (index < templateLiteral.expressions.length) {
                // console.log('templateLiteral.expressions[index]: ' + templateLiteral.expressions[index]);
                const expr = templateLiteral.expressions[index];
                // Example: Evaluate simple numeric expressions or return the original
                if (expr.type === 'Literal' && expr.value) {
                    expression = expr.value.toString();
                } else if (expr.type === 'TemplateLiteral') {
                    expression = this.evaluateTemplateLiteralExpression(expr);
                } else if (expr.type === 'MemberExpression') {
                    let memberExprKey = '';
                    if (expr.property.type === 'Identifier' && expr.property.name) {
                        memberExprKey = expr.property.name;
                    } else if (expr.object.type === 'ThisExpression' && expr.property.type === 'Identifier') {
                        memberExprKey = `this.${expr.property.name}`;
                    } else {
                        // Handle the case where expr.object or expr.property are not Identifiers
                        console.error('expr.object or expr.property are not Identifiers');
                        // Use a placeholder or alternative logic as needed
                    }

                    const memberExpressionValue = this.getMemberExpressionValueFromName(memberExprKey);

                    if (memberExpressionValue.length > 0) {
                        return acc + memberExpressionValue;
                    }
                } else {
                    // For complex or undefined expressions, keep as-is
                    expression = '${' + expr.type + '}';
                }
            }
            return acc + quasi.value.raw + expression;
        }, '');
    }

    evaluateBinaryExpression(binaryExpression: TSESTree.BinaryExpression, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression) {
        const left = binaryExpression.left;
        const right = binaryExpression.right;
        let leftValue = this.evaluateUrl(left, methodDefinition, restCall);
        let rightValue = this.evaluateUrl(right, methodDefinition, restCall);

        if (typeof leftValue === 'string' && typeof rightValue === 'string') {
            return leftValue + rightValue;
        }

        return 'Unknown URL';
    }

    evaluateUrl(node: TSESTree.Node, methodDefinition: TSESTree.MethodDefinition, restCall: TSESTree.CallExpression): string | string[] {
        let result: (string | string[]) = 'Unknown URL';
        if (node.type === 'TemplateLiteral') {
            return this.evaluateTemplateLiteralExpression(node);
        } else if (node.type === 'BinaryExpression') {
            return this.evaluateBinaryExpression(node, methodDefinition, restCall);
        } else if (node.type === 'MemberExpression' && node.property.type === 'Identifier' && node.object.type === 'ThisExpression') {
            result =  this.getMemberExpressionValueFromName(node.property.name);
            // Todo: if the return value is '', then check the Classes Children for the value
            return result;
        } else if (node.type === 'Identifier') {
            result = this.getMethodVariableValueFromNameAndMethod(node.name, methodDefinition);
            // Todo: if the entire URL is passed on as an argument, find the calling Method and get the value of the argument
            if (result === '' && methodDefinition.key.type === 'Identifier') {
                let tempResult: string[] = [];
                for (let methodCall of this.findMethodCallsFromMethodName(methodDefinition.key.name)) {
                    tempResult.push(methodCall.parameterValue);
                }
                result = tempResult;
            }
        } else if (node.type === 'Literal' && node.value) {
            result = node.value.toString();
        }
        return result;
    }

    isMethodVariableOnlyArgument(name: string, restCall: TSESTree.CallExpression) {
// Todo: remove if not needed
        return false;
    }

    findMethodCallsFromMethodName(methodName: string) {
        let methodCalls: { callExpression: TSESTree.CallExpression, parameterValue: string }[] = [];

        for (const node of this.ast.body) {
            let classBody;
            if (node.type === 'ExportNamedDeclaration' && node.declaration?.type === 'ClassDeclaration') {
                methodCalls.push(... this.findMethodCallsFromMethodNameAndClassBody(methodName, node.declaration.body));
            } else if (node.type === 'ClassDeclaration') {
                methodCalls.push(... this.findMethodCallsFromMethodNameAndClassBody(methodName, node.body));
            }

        }

        // simpleTraverse(this.ast, {
        //     enter: (node) => {
        //         if (node.type === 'CallExpression' && node.callee.type === 'Identifier' && node.callee.name === methodName) {
        //             methodCalls.push(node);
        //         }
        //     }
        // });

        return methodCalls;
    }

    findMethodCallsFromMethodNameAndClassBody(methodName: string, classBody: TSESTree.ClassBody) {
        // Todo: Not sure if this does what I want it to do
        let methodCalls: { callExpression: TSESTree.CallExpression, parameterValue: string }[] = [];
        for (const methodNode of classBody.body) {
            if (methodNode.type === 'MethodDefinition' && methodNode.key.type === 'Identifier') {
                simpleTraverse(methodNode, {
                    enter: (node) => {
                        if (node.type === 'CallExpression' && node.callee.type === 'MemberExpression' && node.callee.property.type === 'Identifier' &&
                            node.callee.property.name === methodName && node.arguments[0].type === 'Identifier') {
                            let parameterValue = this.getMethodVariableValueFromNameAndMethod(node.arguments[0].name, methodNode);
                            methodCalls.push({ callExpression: node, parameterValue: parameterValue });
                        }
                    }
                });
            }
        }
        return methodCalls;
    }

    getMemberExpressionValueFromName(name: string) {
        let memberExpression = '';
        simpleTraverse(this.ast, {
            enter(node) {
                if (node.type === 'PropertyDefinition' && node.value?.type === 'Literal' && node.key.type === 'Identifier' && node.value.value) {
                    if (node.key.name === name) {
                        memberExpression = node.value.value.toString();
                    }
                }
            }
        });
        return memberExpression;
    }

    getMemberExpressionTypeFromName(name: string) {
        let memberExpressionType = '';
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

    getMethodVariableValueFromNameAndMethod(name: string, method: TSESTree.MethodDefinition) {
        let methodVariableValue = '';
        simpleTraverse(method, {
            enter: (node) => {
                if (node.type === 'VariableDeclaration') {
                    for (let decl of node.declarations) {
                        if (decl.id.type === 'Identifier' && decl.id.name === name) {
                            if (decl.init?.type === 'Literal' && decl.init.value) {
                                methodVariableValue = decl.init.value.toString();
                            } else if (decl.init?.type === 'TemplateLiteral') {
                                methodVariableValue = this.evaluateTemplateLiteralExpression(decl.init);
                            }
                            return;
                        }
                    }
                }
            }
        });
        return methodVariableValue;
    }

}
