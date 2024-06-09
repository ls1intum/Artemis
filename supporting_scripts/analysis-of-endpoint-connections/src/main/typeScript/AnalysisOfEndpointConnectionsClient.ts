import {
    createSourceFile, forEachChild,
    isCallExpression, isPropertyAccessExpression,
    isPropertyDeclaration,
    isStringLiteral,
    ScriptTarget,
    SourceFile,
    Node, createProgram
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

    // Start traversing the AST from the root
    visit(sourceFile, classProperties, sourceFile, fileName);
};

// This function will be called for each node in the AST
function visit(node: Node, classProperties: { [key: string]: string }, sourceFile: SourceFile, fileName: string) {
    if (isPropertyDeclaration(node) && node.initializer && isStringLiteral(node.initializer)) {
        const key = node.name.getText();
        const value = node.initializer.getText().slice(1, -1); // Remove the quotes
        classProperties[key] = value;
    }

    if (isCallExpression(node)) {
        const expression = node.expression;
        // Check if the expression is a property access expression (e.g. httpClient.get)
        if (isPropertyAccessExpression(expression)) {
            const methodName = expression.name.getText();
            // Check if the property name is one of the httpClient methods
            if (HTTP_METHODS.includes(methodName)) {
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
    forEachChild(node, (childNode) => visit(childNode, classProperties, sourceFile, fileName));
}
