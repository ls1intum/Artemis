import {
    createSourceFile, forEachChild,
    isCallExpression, isPropertyAccessExpression,
    isPropertyDeclaration,
    isStringLiteral,
    ScriptTarget,
    SourceFile,
    Node, createProgram,
    isClassDeclaration,
    isConstructorDeclaration,
    isParameter,
    isTypeReferenceNode, SyntaxKind, ClassDeclaration, CallExpression, ConstructorDeclaration,
} from 'typescript';
import { readdirSync, readFileSync, statSync, writeFileSync } from 'node:fs';
import { join } from 'node:path';

// Get the file names from the command line arguments
const clientDirectory = '../../../../../src/main/webapp/app/';
const fileNames = getFilePaths(clientDirectory);

let restCallFiles: Array<{fileName: string, restCalls: {method: string, url: string, line: number, filePath: string}[]}> = [];

const HTTP_METHODS = ['get', 'post', 'put', 'delete'];
let isFirstRestCall = true;

for (const fileName of fileNames.filter(fileName => fileName.endsWith('.ts')))  {
    let restCalls: Array<{method: string, url: string, line: number, filePath: string}> = [];

    // Load the TypeScript file
    const sourceFile = createSourceFile(fileName, readFileSync(fileName).toString(), ScriptTarget.ES2022, true);

    // Store class property definitions
    const classProperties: { [key: string]: string } = {};

    // Store parameter types
    const parameterTypes: { [key: string]: string } = {};

    // Start traversing the AST from the root
    visit(sourceFile, classProperties, parameterTypes, sourceFile, fileName, restCalls);
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

// This function will be called for each node in the AST
function visit(node: Node, classProperties: { [key: string]: string }, parameterTypes: { [key: string]: string }, sourceFile: SourceFile, fileName: string, restCalls: Array<{method: string, url: string, line: number, filePath: string}>) {
    if (isClassDeclaration(node)) {
        processClassDeclaration(node, classProperties, parameterTypes);
    }

    if (isCallExpression(node)) {
        processCallExpression(node, classProperties, parameterTypes, sourceFile, fileName, restCalls);
    }

    // Continue traversing the AST
    forEachChild(node, (childNode) => visit(childNode, classProperties, parameterTypes, sourceFile, fileName, restCalls));
}

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

function processConstructorDeclaration(constructorDeclaration: ConstructorDeclaration, parameterTypes: { [key: string]: string }) {
    for (const param of constructorDeclaration.parameters) {
        if (isParameter(param) && param.type && isTypeReferenceNode(param.type)) {
            const paramName = param.name.getText();
            const paramType = param.type.typeName.getText();
            parameterTypes[paramName] = paramType;
        }
    }
}

function processCallExpression(callExpression: CallExpression, classProperties: { [key: string]: string }, parameterTypes: { [key: string]: string }, sourceFile: SourceFile, fileName: string, restCalls: Array<{method: string, url: string, line: number, filePath: string}>) {
    const expression = callExpression.expression;
    // Check if the expression is a property access expression (e.g. httpClient.get)
    if (isPropertyAccessExpression(expression)) {
        const methodName = expression.name.getText();
        const objectName = expression.expression.getText().replace(/^this\./, ''); // Remove 'this.' prefix if present;
        // Check if the property name is one of the httpClient methods
        if (HTTP_METHODS.includes(methodName) && parameterTypes[objectName] === 'HttpClient') {
            logRestCall(callExpression, methodName, classProperties, sourceFile, fileName, restCalls);
        }
    }
}

function logRestCall(restCall: CallExpression, methodName: string, classProperties: { [key: string]: string }, sourceFile: SourceFile, fileName: string, restCalls: Array<{method: string, url: string, line: number, filePath: string}>) {
    if (isFirstRestCall) {
        console.log('===================================');
        console.log('REST calls found in the following file: ' + fileName);
        console.log('===================================');
        isFirstRestCall = false;
    }
    console.log(`Found REST call: ${methodName}`);
    let url = '';
    if (restCall.arguments.length > 0) {
        url = restCall.arguments[0].getText();
        // Replace class properties in the URL
        for (const prop in classProperties) {
            url = url.replace(new RegExp(`\\$\\{this.${prop}\\}`, 'g'), classProperties[prop]);
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
