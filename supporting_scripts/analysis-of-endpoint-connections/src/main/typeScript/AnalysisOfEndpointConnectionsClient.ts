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
    isTypeReferenceNode, SyntaxKind,
} from 'typescript';
import { readFileSync } from 'node:fs';

// Get the file names from the command line arguments
const fileNames = process.argv.slice(2);
const HTTP_METHODS = ['get', 'post', 'put', 'delete'];

for (const fileName of fileNames.filter(fileName => fileName.endsWith('.ts')))  {
    // Load the TypeScript file
    const sourceFile = createSourceFile(fileName, readFileSync(fileName).toString(), ScriptTarget.ES2022, true);
    console.log(fileName);

    // Store class property definitions
    const classProperties: { [key: string]: string } = {};
    const classMethods: { [key: string]: string } = {};

    // Store parameter types
    const parameterTypes: { [key: string]: string } = {};

    // Start traversing the AST from the root
    visit(sourceFile, classProperties, parameterTypes, sourceFile, fileName);
};

// This function will be called for each node in the AST
function visit(node: Node, classProperties: { [key: string]: string }, parameterTypes: { [key: string]: string }, sourceFile: SourceFile, fileName: string) {
    if (isClassDeclaration(node)) {
        for (const member of node.members) {
            if (isConstructorDeclaration(member)) {
                for (const param of member.parameters) {
                    if (isParameter(param) && param.type && isTypeReferenceNode(param.type)) {
                        const paramName = param.name.getText();
                        const paramType = param.type.typeName.getText();
                        parameterTypes[paramName] = paramType;
                    }
                }
            }

            if (isPropertyDeclaration(member) && member.initializer && isStringLiteral(member.initializer)) {
                const key = member.name.getText();
                const value = member.initializer.getText().slice(1, -1); // Remove the quotes
                classProperties[key] = value;

            }
        }
    }


    if (isCallExpression(node)) {
        const expression = node.expression;
        // Check if the expression is a property access expression (e.g. httpClient.get)
        if (isPropertyAccessExpression(expression)) {
            const methodName = expression.name.getText();
            const objectName = expression.expression.getText().replace(/^this\./, ''); // Remove 'this.' prefix if present;
            // Check if the property name is one of the httpClient methods
            if (HTTP_METHODS.includes(methodName) && parameterTypes[objectName] === 'HttpClient') {
                console.log(`Found REST call: ${methodName}`);
                if (node.arguments.length > 0) {
                    let url = node.arguments[0].getText();
                    // Replace class properties in the URL
                    for (const prop in classProperties) {
                        url = url.replace(new RegExp(`\\$\\{this.${prop}\\}`, 'g'), classProperties[prop]);
                    }
                    console.log(`with URL: ${url}`);

                    // Log the other arguments
                    for (let i = 1; i < node.arguments.length; i++) {
                        console.log(`Argument ${i}: ${node.arguments[i].getText()}`);
                    }
                } else {
                    console.log('No arguments provided for this REST call');
                }
                console.log(`At line: ${sourceFile.getLineAndCharacterOfPosition(node.getStart()).line + 1}`);
                console.log(`At file path: ${fileName}`);
                console.log('-----------------------------------');
            }
        }
    }

    // Continue traversing the AST
    forEachChild(node, (childNode) => visit(childNode, classProperties, parameterTypes, sourceFile, fileName));
}
