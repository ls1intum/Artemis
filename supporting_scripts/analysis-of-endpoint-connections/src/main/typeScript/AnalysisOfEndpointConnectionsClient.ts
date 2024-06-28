import {
    createSourceFile,
    forEachChild,
    isCallExpression,
    isPropertyAccessExpression,
    isPropertyDeclaration,
    isStringLiteral,
    ScriptTarget,
    SourceFile,
    Node,
    createProgram,
    isClassDeclaration,
    isConstructorDeclaration,
    isParameter,
    isTypeReferenceNode,
    SyntaxKind,
    ClassDeclaration,
    CallExpression,
    ConstructorDeclaration,
    isBinaryExpression,
    isIdentifier, isVariableDeclaration, VariableDeclaration,
} from 'typescript';
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// Get the file names from the command line arguments
const clientDirectory = '../../../../../src/main/webapp/app/';
const fileNames = getFilePaths(clientDirectory);

let restCallFiles: Array<{fileName: string, restCalls: {method: string, url: string, line: number, filePath: string}[]}> = [];

const HTTP_METHODS = ['get', 'post', 'put', 'delete', 'patch'];
let isFirstRestCall = true;
console.log('WorkingDirectory: ' + process.cwd());

for (const fileName of fileNames.filter(fileName => fileName.endsWith('.ts')))  {
    let restCalls: Array<{method: string, url: string, line: number, filePath: string}> = [];

    // Load the TypeScript file
    const sourceFile = createSourceFile(fileName, readFileSync(fileName).toString(), ScriptTarget.ES2022, true);

    // Store class property definitions
    const classProperties: { [key: string]: string } = {};

    // Store parameter types
    const parameterTypes: { [key: string]: string } = {};

    // Store method variables
    const methodVariables: { [key: string]: string } = {};

    // Start traversing the AST from the root
    visit(sourceFile, classProperties, parameterTypes, methodVariables, sourceFile, fileName, restCalls);
    isFirstRestCall = true;
    if (restCalls.length > 0)
        restCallFiles.push({fileName: fileName, restCalls: restCalls});
};

// Write the restCalls array to a JSON file
writeFileSync('../../../../../supporting_scripts/analysis-of-endpoint-connections/restCalls.json', JSON.stringify(restCallFiles, null, 2));

function getFilePaths(directoryPath: string): string[] {
    let filePaths: string[] = [];

    const files = readdirSync(directoryPath);

    for (const file of files) {
        const fullPath = join(directoryPath, file);
        const stat = statSync(fullPath);

        if (stat.isDirectory()) {
            filePaths = filePaths.concat(getFilePaths(fullPath));
        } else {
            filePaths.push(fullPath);
        }
    }
    return filePaths;
}

/**
 * This function is used to visit each node in the Abstract Syntax Tree (AST) of a TypeScript file.
 * It checks if the node is a class declaration or a call expression and processes it accordingly.
 * It also recursively visits all child nodes of the current node.
 *
 * @param node - The current node in the AST.
 * @param classProperties - An object that maps class property names to their values.
 * @param parameterTypes - An object that maps parameter names to their types.
 * @param sourceFile - The TypeScript source file being analyzed.
 * @param fileName - The name of the TypeScript file.
 */
function visit(node: Node, classProperties: { [key: string]: string }, parameterTypes: { [key: string]: string }, methodVariables: { [key: string]: string }, sourceFile: SourceFile, fileName: string, restCalls: Array<{method: string, url: string, line: number, filePath: string}>) {
    if (isClassDeclaration(node)) {
        processClassDeclaration(node, classProperties, parameterTypes);
    } else if (isCallExpression(node)) {
        processCallExpression(node, classProperties, parameterTypes, methodVariables, sourceFile, fileName, restCalls);
    } else if(isVariableDeclaration(node)) {
        processVariableDeclaration(node, methodVariables);
    }

    // Continue traversing the AST
    forEachChild(node, (childNode) => visit(childNode, classProperties, parameterTypes, methodVariables, sourceFile, fileName, restCalls));
}

function processVariableDeclaration(variableDeclaration: VariableDeclaration, methodVariables: { [key: string]: string }) {
    console.log('Variable declaration node');
    console.log('DeclarationNode: ' + variableDeclaration.getText() + ' -- Kind: ' + variableDeclaration.kind);
    if (isVariableDeclaration(variableDeclaration) && variableDeclaration.initializer && isStringLiteral(variableDeclaration.initializer)) {
        const key = variableDeclaration.name.getText();
        const value = variableDeclaration.initializer.getText().slice(1, -1); // Remove the quotes
        methodVariables[key] = value;
    }
}

/**
 * Processes a TypeScript class declaration node in the Abstract Syntax Tree (AST).
 * It iterates over the members of the class declaration. If a member is a constructor declaration,
 * it processes the constructor declaration. If a member is a property declaration with a string literal initializer,
 * it adds the property to the classProperties object.
 *
 * @param classDeclaration - The class declaration node to process.
 * @param classProperties - An object that maps class property names to their values.
 * @param parameterTypes - An object that maps parameter names to their types.
 */
function processClassDeclaration(classDeclaration: ClassDeclaration, classProperties: { [key: string]: string }, parameterTypes: { [key: string]: string }) {
    for (const member of classDeclaration.members) {
        if (isConstructorDeclaration(member)) {
            processConstructorDeclaration(member, parameterTypes);
        }

        if (isPropertyDeclaration(member) && member.initializer && isStringLiteral(member.initializer)) {
            const key = member.name.getText();
            const value = member.initializer.getText().slice(1, -1); // Remove the quotes
            classProperties[key] = value;
        }
    }
}

/**
 * Processes a TypeScript constructor declaration node in the Abstract Syntax Tree (AST).
 * It iterates over the parameters of the constructor declaration. If a parameter is a type reference,
 * it adds the parameter to the parameterTypes object.
 *
 * @param constructorDeclaration - The constructor declaration node to process.
 * @param parameterTypes - An object that maps parameter names to their types.
 */
function processConstructorDeclaration(constructorDeclaration: ConstructorDeclaration, parameterTypes: { [key: string]: string }) {
    for (const param of constructorDeclaration.parameters) {
        if (isParameter(param) && param.type && isTypeReferenceNode(param.type)) {
            const paramName = param.name.getText();
            const paramType = param.type.typeName.getText();
            parameterTypes[paramName] = paramType;
        }
    }
}

/**
 * Processes a TypeScript call expression node in the Abstract Syntax Tree (AST).
 * It checks if the expression is a property access expression (e.g. httpClient.get).
 * If the property name is one of the httpClient methods and the object is of type HttpClient,
 * it logs the REST call.
 *
 * @param callExpression - The call expression node to process.
 * @param classProperties - An object that maps class property names to their values.
 * @param parameterTypes - An object that maps parameter names to their types.
 * @param sourceFile - The TypeScript source file being analyzed.
 * @param fileName - The name of the TypeScript file.
 */
function processCallExpression(callExpression: CallExpression, classProperties: { [key: string]: string }, parameterTypes: { [key: string]: string }, methodVariables: { [key: string]: string }, sourceFile: SourceFile, fileName: string, restCalls: Array<{method: string, url: string, line: number, filePath: string}>) {
    const expression = callExpression.expression;
    // Check if the expression is a property access expression (e.g. httpClient.get)
    if (isPropertyAccessExpression(expression)) {
        const methodName = expression.name.getText();
        const objectName = expression.expression.getText().replace(/^this\./, ''); // Remove 'this.' prefix if present;
        // Check if the property name is one of the httpClient methods
        if (HTTP_METHODS.includes(methodName) && parameterTypes[objectName] === 'HttpClient') {
            logRestCall(callExpression, methodName, classProperties, methodVariables, sourceFile, fileName, restCalls);
        }
    }
}

/**
 * Logs the details of a REST call found in the TypeScript file.
 * It logs the method name, URL, and arguments of the REST call.
 * If it's the first REST call found in the file, it also logs the file name.
 * It replaces class properties in the URL with their actual values.
 *
 * @param restCall - The call expression node representing the REST call.
 * @param methodName - The name of the REST method (e.g. 'get', 'post', etc.).
 * @param classProperties - An object that maps class property names to their values.
 * @param sourceFile - The TypeScript source file being analyzed.
 * @param fileName - The name of the TypeScript file.
 */
function logRestCall(restCall: CallExpression, methodName: string, classProperties: { [key: string]: string }, methodVariables: { [key: string]: string }, sourceFile: SourceFile, fileName: string, restCalls: Array<{method: string, url: string, line: number, filePath: string}>) {
    if (isFirstRestCall) {
        console.log('===================================');
        console.log('REST calls found in the following file: ' + fileName);
        console.log('===================================');
        isFirstRestCall = false;
    }
    console.log(`Found REST call: ${methodName}`);
    let url = '';
    if (restCall.arguments.length > 0) {
        url = evaluateUrl(restCall.arguments[0], classProperties, methodVariables);
        // Replace class properties in the URL
        for (const prop in classProperties) {
            // Replace all occurrences of ${this.prop} with the actual value
            url = url.replace(new RegExp(`\\$\\{this.${prop}\\}`, 'g'), classProperties[prop]);
            // Replace all occurrences of this.prop with the actual value
            url = url.replace(new RegExp(`this.${prop}`, 'g'), classProperties[prop]);
        }
        console.log(`with URL: ${url}`);

        // Log the other arguments
        for (let i = 1; i < restCall.arguments.length; i++) {
            console.log(`Argument ${i}: ${restCall.arguments[i].getText()}`);
        }
    } else {
        console.log('No arguments provided for this REST call');
    }
    console.log(`At line: ${sourceFile.getLineAndCharacterOfPosition(restCall.getStart()).line + 1}`);
    console.log('-----------------------------------');

    let restCallInformation  = {
        method: methodName,
        url: url.length > 0 ? url : 'No arguments provided for this REST call',
        line: sourceFile.getLineAndCharacterOfPosition(restCall.getStart()).line + 1,
        filePath: fileName
    };
    restCalls.push(restCallInformation);
}


function evaluateUrl(expression: Node, classProperties: { [key: string]: string }, methodVariables: { [key: string]: string }): string {
    if (isPropertyAccessExpression(expression) && expression.expression.getText() === 'this') {
        const propName = expression.name.getText();
        return classProperties[propName] || '';
    } else if (isBinaryExpression(expression) && expression.operatorToken.kind === SyntaxKind.PlusToken) {
        return evaluateUrl(expression.left, classProperties, methodVariables) + evaluateUrl(expression.right, classProperties, methodVariables);
    } else if (isStringLiteral(expression)) {
        return expression.text;
    } else if (isIdentifier(expression)) {
        return methodVariables[expression.getText()] || expression.getText();
    } else {
        return expression.getText();
    }
}
