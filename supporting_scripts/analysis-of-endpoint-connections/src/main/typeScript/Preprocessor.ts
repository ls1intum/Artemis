import { parse, TSESTree } from '@typescript-eslint/typescript-estree';
import { readFileSync } from 'fs';

interface SuperClass {
    name: string;
    path: string;
    superConstructorCalls: { arguments: string[] }[];
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
    public static PREPROCESSING_RESULTS = new Map<string, SuperClass>();
    public static readonly pathPrefix = '../../../../../'
    private readonly directoryPrefix = 'src/main/webapp/';
    private readonly fileToPreprocess: string;
    private ast: TSESTree.Program;

    private memberVariables: Map<string, MemberVariable> = new Map<string, MemberVariable>();

    constructor(fileToPreprocess: string) {
        this.fileToPreprocess = fileToPreprocess;
        this.ast = Preprocessor.parseTypeScriptFile(Preprocessor.pathPrefix + this.fileToPreprocess);
    }

    /**
     * Preprocesses the TypeScript file.
     * This method checks if the AST (Abstract Syntax Tree) type is 'Program'.
     * If it is, it iterates over the body of the AST to find class declarations.
     * For each class declaration found, it calls the `preprocessClass` method to process the class.
     * It also handles named exports that are class declarations.
     */
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

    /**
     * Preprocesses a class declaration.
     * This method checks if the class has a superclass. If it does, it identifies and stores the member variables
     * from the class body and then identifies the superclass and processes its related information.
     *
     * @param classDeclaration - The class declaration node to preprocess.
     */
    preprocessClass(classDeclaration: TSESTree.ClassDeclaration) {
        if (classDeclaration.superClass) {
            this.identifyMemberVariablesFromClassBody(classDeclaration);
            this.identifySuperClass(classDeclaration);
        }
    }

    /**
     * Evaluates a binary expression to find its corresponding value.
     * This method processes the left and right nodes of the binary expression.
     * If a node is a member expression, it evaluates the member expression to find its value.
     * If a node is a literal, it converts the literal value to a string.
     * The method concatenates the evaluated values of the left and right nodes and returns the result as a string.
     *
     * @param binaryExpression - The binary expression to be evaluated.
     * @returns The evaluated value of the binary expression as a string.
     */
    evaluateBinaryExpression(binaryExpression: TSESTree.BinaryExpression) { // todo: finish implementation
        let left = binaryExpression.left;
        let right = binaryExpression.right;
        let result = '';
        [left, right].forEach((node) => {
            let parameterValue = '';
            if (node.type === 'MemberExpression') {
                if (node.object.type === 'Identifier' && node.property.type === 'Identifier') {
                    let parameterObjectName = node.object.name;
                    let parameterName = node.property.name;
                    parameterValue = this.findParameterValueByParameterNameAndFilePath(parameterName, this.identifyImportedClassByName(parameterObjectName));
                } else if (node.object.type === 'Literal' && node.object.value) {
                    parameterValue = node.object.value.toString();
                }
                result += parameterValue;
            } else if (node.type === 'Literal' && node.value) {
                result += node.value.toString();
            }
        });

        return result;
    }

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
                if (node.value?.type === 'Literal' && node.value.value) {
                    this.memberVariables.set(node.key.name, { name: node.key.name, type: 'string', value: node.value.value.toString() });
                } else if (node.value?.type === 'TemplateLiteral') {
                    this.memberVariables.set(node.key.name, { name: node.key.name, type: 'string' });
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
     * This `ChildClass` object is added to the `PREPROCESSING_RESULTS`, which track the relationships between superclasses
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

            let superConstructorCall = this.identifySuperConstructorCalls(classDeclaration, superClassName);

            if (Preprocessor.PREPROCESSING_RESULTS.has(superClassName)) {
                Preprocessor.PREPROCESSING_RESULTS.get(superClassName)?.childClasses.push(childClass);
                Preprocessor.PREPROCESSING_RESULTS.get(superClassName)?.superConstructorCalls.concat(... superConstructorCall)
            } else {
                Preprocessor.PREPROCESSING_RESULTS.set(superClassName, { name: superClassName, path: superClassPath, childClasses: [childClass], superConstructorCalls: superConstructorCall});
            }
        }
    }

    /**
     * Identifies calls to the superclass constructor within a class declaration.
     * This method iterates over the body of the class to find the constructor method.
     * Within the constructor, it looks for call expressions to `super` and evaluates their arguments.
     * The evaluated arguments are collected and returned as an array of objects, each containing an `arguments` array.
     *
     * @param classDeclaration - The class declaration node to process.
     * @param superClassName - The name of the superclass to identify constructor calls for.
     * @returns An array of objects, each containing an `arguments` array with the evaluated values of the super constructor call arguments.
     */
    identifySuperConstructorCalls(classDeclaration: TSESTree.ClassDeclaration, superClassName: string) {
        let result: {arguments: string[]}[]= [];
        classDeclaration.body.body.forEach((node) => {
            if (node.type === 'MethodDefinition' && node.kind === 'constructor' && node.value.type === 'FunctionExpression') {
                node.value.body.body.forEach((statement) => {
                    if (statement.type === 'ExpressionStatement' && statement.expression.type === 'CallExpression' && statement.expression.callee.type === 'Super') {
                        let superConstructorCall = statement.expression;
                        let superConstructorCallParameters = superConstructorCall.arguments;
                        let superConstructorCallParameterValues: string[] = [];
                        superConstructorCallParameters.forEach((parameter) => {
                            superConstructorCallParameterValues.push(this.evaluateCallExpressionArgument(parameter));
                        });
                        result.push({arguments: superConstructorCallParameterValues});
                    }
                })
            }
        });

        return result;
    }

    /**
     * Evaluates a call expression argument to find its corresponding value.
     * This method checks the type of the call expression argument and processes it accordingly.
     * If the argument is an identifier, it evaluates the identifier to find its value.
     * If the argument is a binary expression, it evaluates the binary expression to find its value.
     * If the argument is a literal, it converts the literal value to a string.
     * The method returns the evaluated value as a string, or an empty string if no value is found.
     *
     * @param callExpressionArgument - The call expression argument to be evaluated.
     * @returns The evaluated value of the call expression argument as a string, or an empty string if no value is found.
     */
    evaluateCallExpressionArgument(callExpressionArgument: TSESTree.CallExpressionArgument) {
        let result = '';
        if (callExpressionArgument.type === 'Identifier') {
            result = this.evaluateIdentifierArgument(callExpressionArgument)!;
        } else if (callExpressionArgument.type === 'BinaryExpression') {
            result = this.evaluateBinaryExpression(callExpressionArgument);
        } else if (callExpressionArgument.type === 'Literal' && callExpressionArgument.value) {
            result = callExpressionArgument.value.toString();
        }
        return result? result : '';
    }

    /**
     * Evaluates an identifier argument to find its corresponding value.
     * This method checks if the identifier name exists in the `memberVariables` map.
     * If it exists, it returns the value associated with the identifier name.
     * If it does not exist, it returns an empty string.
     *
     * @param identifier - The identifier whose value is to be evaluated.
     * @returns The value of the identifier if found; otherwise, an empty string.
     */
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

    /**
     * Finds the value of a parameter by its name within a given AST (Abstract Syntax Tree).
     * This method iterates over the body of a class to find a property definition that matches the specified parameter name.
     * If a matching property definition is found and its value is a literal, the method returns the string representation of the value.
     * If no matching property definition is found, the method returns an empty string.
     *
     * @param parameterName - The name of the parameter whose value is to be found.
     * @param ast - The AST of the class body to search within.
     * @returns The value of the parameter if found; otherwise, an empty string.
     */
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

    /**
     * Parses a TypeScript file and returns its Abstract Syntax Tree (AST).
     *
     * @param filePath - The path to the TypeScript file to be parsed.
     * @returns The AST of the parsed TypeScript file.
     */
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
