import { parse, TSESTree } from '@typescript-eslint/typescript-estree';
import { readFileSync } from 'fs';

interface SuperClass {
    name: string;
    path: string;
    childClasses: ChildClass[];
}

interface ChildClass {
    superClass: string;
    name: string;
    memberVariables: Map<string, MemberVariable>;
    parentMethodCalls: ParentMethodCalls[];
}

interface ParentMethodCalls {
    name: string;
    parameters: MemberVariable[];
}

interface MemberVariable {
    name: string;
    type: string;
    value?: string;
}


export class Preprocessor {
    public static PREPROCESSING_RESULTS = new Map();
    public static readonly pathPrefix = '../../../../../'
    private readonly directoryPrefix = 'src/main/webapp/';
    private readonly fileToPreprocess: string;
    private ast: TSESTree.Program;
    private superClassMap: Map<string, SuperClass> = new Map<string, SuperClass>();
    private memberVariables: Map<string, MemberVariable> = new Map<string, MemberVariable>();

    constructor(fileToPreprocess: string) {
        this.fileToPreprocess = fileToPreprocess;
        this.ast = Preprocessor.parseTypeScriptFile(Preprocessor.pathPrefix + this.fileToPreprocess);
    }

    preprocessFile() {
        if (this.ast.type !== 'Program') {
            return;
        }

        this.ast.body.forEach((node) => {
            if (node.type === 'ClassDeclaration') {
                this.preprocessClass(node);
            } else if (node.type === 'ExportNamedDeclaration' && node.declaration?.type === 'ClassDeclaration') {
                this.preprocessClass(node.declaration);
            }
        });
    }

    preprocessClass(classDeclaration: TSESTree.ClassDeclaration) {
        this.identifyMemberVariablesFromClassBody(classDeclaration);

        if (classDeclaration.superClass) {
            let superClass = classDeclaration.superClass;
            this.identifySuperClass(classDeclaration);
        }

        classDeclaration.body.body.forEach((node) => {
            if (node.type === 'MethodDefinition') {
                this.preprocessMethod(node);
            }
        });
    }

    preprocessMethod(methodDefinition: TSESTree.MethodDefinition) {
        if (methodDefinition.kind === 'constructor') {
            this.identifyMemberVariablesFromConstructorParams(methodDefinition);

            if (methodDefinition.value.type === 'FunctionExpression' && methodDefinition.value.body.body.length > 0) {
                methodDefinition.value.body.body.forEach((node) => {
                    // super constructor is called. The parameters passed to the super constructor are parsed out and stored for later use.
                    if (node.type === 'ExpressionStatement' && node.expression.type === 'CallExpression' && node.expression.callee.type === 'Super') {
                        let superConstructorCall = node.expression;
                        superConstructorCall.arguments.forEach((argument) => {
                            let parameterName = '';
                            let parameterValue = '';
                            if (argument.type === 'Identifier' && this.memberVariables.has(argument.name) && this.memberVariables.get(argument.name)?.value && this.memberVariables.get(argument.name.toString())!.value){
                                parameterName = argument.name;
                                let tempParameterValue = this.memberVariables.get(argument.name)?.value;
                                if (tempParameterValue) {
                                    parameterValue = tempParameterValue;
                                }

                                // parameterValue = memberVariables.get(argument.name.toString());
                                // findParameterValueByParameterNameAndFilePath(parameterName, 'filePath');

                                // Todo: store parameterValue in a map for later use
                            } else if (argument.type ==='BinaryExpression' ) {
                                this.evaluateBinaryExpression(argument);
                            }
                        });
                    }
                });
            }
        }
    }


    evaluateBinaryExpression(binaryExpression: TSESTree.BinaryExpression) { // todo: finish implementation
        let left = binaryExpression.left;
        let right = binaryExpression.right;
        [left, right].forEach((node) => {
            let parameterValue = '';
            if (node.type === 'MemberExpression') {
                if(node.object.type === 'Identifier' && node.property.type === 'Identifier') {
                    let parameterObjectName = node.object.name;
                    let parameterName = node.property.name;
                    parameterValue = this.findParameterValueByParameterNameAndFilePath(parameterName, this.identifyImportedClassByName(parameterObjectName));
                } else if (node.object.type === 'ThisExpression') {
                    // if (this.memberVariables.has(node.property.name)) {
                    //     let memberVariable = this.memberVariables.get(node.property.name);
                    //     if (memberVariable) {
                } else if (node.object.type === 'Literal' && node.object.value) {
                    parameterValue = node.object.value.toString();
                }
            }
        });
    }

    // evaluateIdentifierInSuperConstructorCall(identifier: TSESTree.Identifier) {
    //
    // }

    /**
     * Identifies and stores member variables of a class.
     * This method iterates over the body of a class, looking for property definitions.
     * When a property definition is found, it checks the type of the property's value.
     * If the value is a literal, it stores the property's name, type as 'string', and its literal value.
     * If the value is a template literal, it stores the property's name and type as 'string', but does not store a value.
     * These member variables are stored in the `memberVariables` map with the property's name as the key.
     *
     * @param classDeclaration - The class declaration node of the class from which to identify member variables.
     */
    identifyMemberVariablesFromClassBody(classDeclaration: TSESTree.ClassDeclaration) {
        classDeclaration.body.body.forEach((node) => {
            if (node.type === 'PropertyDefinition' && node.key.type === 'Identifier') {
                if (node.value?.type === 'Literal') {
                    this.memberVariables.set(node.key.name, { name: node.key.name, type: 'string', value: node.value.value!.toString() });
                } else if (node.value?.type === 'TemplateLiteral') {
                    this.memberVariables.set(node.key.name, { name: node.key.name, type: 'string' });
                }
            }
        });
    }

    /**
     * Identifies and stores member variables of a class from the parameters of the constructor.
     * This method iterates over the parameters of the constructor, looking for TSParameterProperties.
     * When a TSParameterProperty is found, it checks the type of the parameter's typeAnnotation.
     * If the typeAnnotation is a TSTypeReference and the typeName is an Identifier, it stores the parameter's name and type.
     * These member variables are stored in the `memberVariables` map with the parameter's name as the key.
     *
     * @param constructor - The constructor method definition node from which to identify member variables.
     */
    identifyMemberVariablesFromConstructorParams(constructor: TSESTree.MethodDefinition) {
        if (constructor.kind !== 'constructor') {
            return;
        }

        constructor.value.params.forEach((param) => {
            if (param.type === 'TSParameterProperty' && param.parameter.type === 'Identifier' && param.parameter.typeAnnotation && param.parameter.typeAnnotation.typeAnnotation.type === 'TSTypeReference'
                && param.parameter.typeAnnotation.typeAnnotation.typeName.type === 'Identifier') {
                let memberVariableName = param.parameter.name;
                let memberVariableType = param.parameter.typeAnnotation.typeAnnotation.typeName.name;

                if (this.memberVariables.has(memberVariableName)) {
                    let memberVariable = this.memberVariables.get(memberVariableName);
                    if (memberVariable) {
                        memberVariable.type = memberVariableType;
                    }
                } else {
                    this.memberVariables.set(memberVariableName, { name: memberVariableName, value: '', type: memberVariableType });
                }
            }
        });
    }

    /**
     * Identifies the superclass of a given class declaration and processes its related information.
     * This method examines the `superClass` property of a class declaration to determine if the class extends another class.
     * If a superclass is identified, the method retrieves its name and the path to its definition.
     * It then constructs a `ChildClass` object containing information about the current class, such as its name,
     * an empty map for member variables, and an empty array for parent method calls.
     * This `ChildClass` object is added to the `superClassMap`, which tracks the relationships between superclasses
     * and their child classes. If the superclass is already present in the map, the current class is added to its
     * list of child classes. Otherwise, a new entry for the superclass is created in the map.
     * Finally, the method calls `identifySuperConstructorCalls` to process any calls to the superclass constructor
     * within the current class.
     *
     * @param classDeclaration - The class declaration node to process.
     */
    identifySuperClass(classDeclaration: TSESTree.ClassDeclaration) {
        let superClassName: string;
        let superClassPath: string;
        if (classDeclaration.superClass && classDeclaration.superClass.type === 'Identifier') {
            superClassName = classDeclaration.superClass.name;
            superClassPath = this.identifyImportedClassByName(superClassName);
            let childClassName = classDeclaration.id!.name;
            let childClass = { superClass: superClassName, name: childClassName, memberVariables: this.memberVariables, parentMethodCalls: [] };
            if (this.superClassMap.has(superClassName)) {
                this.superClassMap.get(superClassName)?.childClasses.push(childClass);
            } else {
                this.superClassMap.set(superClassName, { name: superClassName, path: superClassPath, childClasses: [childClass] });
            }

            this.identifySuperConstructorCalls(classDeclaration, superClassName)
        }
    }

    identifySuperConstructorCalls(classDeclaration: TSESTree.ClassDeclaration, superClassName: string) {
        classDeclaration.body.body.forEach((node) => {
            if (node.type === 'MethodDefinition' && node.kind === 'constructor' && node.value.type === 'FunctionExpression') {
                node.value.body.body.forEach((statement) => {
                    if (statement.type === 'ExpressionStatement' && statement.expression.type === 'CallExpression' && statement.expression.callee.type === 'Super') {
                        let superConstructorCall = statement.expression;
                        let superConstructorCallParameters = superConstructorCall.arguments;
                        superConstructorCallParameters.forEach((parameter) => {
                            // Todo: Find the parameter values and store them in the according array
                            let parameterValue = this.evaluateCallExpressionArgument(parameter);
                        });
                    }
                })
            }
        });
    }

    evaluateCallExpressionArgument(callExpressionArgument: TSESTree.CallExpressionArgument) {
        if (callExpressionArgument.type === 'Identifier') {
            this.evaluateIdentifierArgument(callExpressionArgument);
        }
    }

    evaluateIdentifierArgument(identifier: TSESTree.Identifier) {
        if (this.memberVariables.has(identifier.name)) {
            return this.memberVariables.get(identifier.name)?.value;
        } else {
            return '';
        }
    }

    /**
     * Identifies the path of an imported class by its name.
     * This method iterates over all import declarations in the AST of the file being preprocessed.
     * If an import declaration matches the specified class name, the source value (path) of the import is stored.
     *
     * @param className - The name of the class to find the import path for.
     * @returns The path of the imported class, or an empty string if the class is not found.
     */
    identifyImportedClassByName(className: string) {
        for (let node of this.ast.body) {
            if (node.type === 'ImportDeclaration' && node.specifiers[0].local.name === className) {
                return node.source.value;
            }
        };
        return '';
    }

    /**
     * Finds the value of a parameter by its name within a specified file.
     * This method loads the TypeScript file at the given file path, parses it to create an AST (Abstract Syntax Tree),
     * and then iterates over the AST to find a class declaration. Once a class declaration is found,
     * it delegates the search for the parameter value to `findParameterValueByParameterNameAndAST`,
     * which scans the class body for a property matching the parameter name and returns its value.
     *
     * @param parameterName - The name of the parameter whose value is to be found.
     * @param filePath - The path to the TypeScript file (relative to the base directory set in `pathPrefix` and `directoryPrefix`) where the parameter value is to be searched.
     * @returns The value of the parameter if found; otherwise, an empty string.
     */
    findParameterValueByParameterNameAndFilePath (parameterName: string, filePath: string) {
        const targetAST = Preprocessor.parseTypeScriptFile(Preprocessor.pathPrefix + this.directoryPrefix + filePath + '.ts');

        for (const node of targetAST.body) {
            if (node.type === 'ExportNamedDeclaration' && node.declaration?.type === 'ClassDeclaration') {
                return Preprocessor.findParameterValueByParameterNameAndAST(parameterName, node.declaration.body);
            }
            if (node.type === 'ClassDeclaration') {
                return Preprocessor.findParameterValueByParameterNameAndAST(parameterName, node.body);
            }
        }

        return '';
    }

    private static findParameterValueByParameterNameAndAST(parameterName: string, ast: TSESTree.ClassBody) {
        for (let classBodyNode of ast.body) {
            if (classBodyNode.type === 'PropertyDefinition' && classBodyNode.key.type === 'Identifier' && classBodyNode.key.name === parameterName) {
                if (classBodyNode.value?.type === 'Literal' && classBodyNode.value.value) {
                    return classBodyNode.value.value.toString();
                }
            }
        };

        return '';
    }

    static parseTypeScriptFile(filePath: string) {
        const code = readFileSync(filePath, 'utf8');
        return parse(code, {
            loc: true,
            comment: true,
            tokens: true,
            ecmaVersion: 2020,
            sourceType: 'module',
        });
    }

}
