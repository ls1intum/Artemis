import * as ts from 'typescript';
import * as fs from 'fs';

// Get the file names from the command line arguments
const fileNames = process.argv.slice(2);
const HTTP_METHODS = ['get', 'post', 'put', 'delete'];

fileNames.filter(fileName => fileName.endsWith('.ts')).forEach(fileName => {
    // Load the TypeScript file
    const sourceFile = ts.createSourceFile(fileName, fs.readFileSync(fileName).toString(), ts.ScriptTarget.ES2022, true);

    // Store class property definitions
    const classProperties: { [key: string]: string } = {};

    // Start traversing the AST from the root
    visit(sourceFile, classProperties, sourceFile, fileName);
});

// This function will be called for each node in the AST
function visit(node: ts.Node, classProperties: { [key: string]: string }, sourceFile: ts.SourceFile, fileName: string) {
    if (ts.isPropertyDeclaration(node) && node.initializer && ts.isStringLiteral(node.initializer)) {
        const name = node.name.getText();
        const value = node.initializer.getText().slice(1, -1); // Remove the quotes
        classProperties[name] = value;
    }

    if (ts.isCallExpression(node)) {
        const expression = node.expression;
        // Check if the expression is a property access expression (e.g. httpClient.get)
        if (ts.isPropertyAccessExpression(expression)) {
            const name = expression.name.getText();
            // Check if the property name is one of the httpClient methods
            if (HTTP_METHODS.includes(name)) {
                console.log(`Found REST call: ${name}`);
                if (node.arguments.length > 0) {
                    let url = node.arguments[0].getText();
                    // Replace class properties in the URL
                    for (const prop in classProperties) {
                        url = url.replace(new RegExp(`\\$\\{this.${prop}\\}`, 'g'), classProperties[prop]);
                    }
                    console.log(`with URL: ${url}`);
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
    ts.forEachChild(node, (childNode) => visit(childNode, classProperties, sourceFile, fileName));
}
