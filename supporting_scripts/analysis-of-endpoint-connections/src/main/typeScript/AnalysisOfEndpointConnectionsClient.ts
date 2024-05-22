import * as ts from 'typescript';
import * as fs from 'fs';

// Get the file names from the command line arguments
const fileNames = process.argv.slice(2);

fileNames.forEach(fileName => {
    if (fileName.endsWith('.ts')) {
        // Load the TypeScript file
        const sourceFile = ts.createSourceFile(fileName, require('fs').readFileSync(fileName).toString(), ts.ScriptTarget.ES2015, true);

        // Store class property definitions
        const classProperties: { [key: string]: string } = {};

        // This function will be called for each node in the AST
        function visit(node: ts.Node) {
            // Check if the node is a property declaration
            if (ts.isPropertyDeclaration(node) && node.initializer && ts.isStringLiteral(node.initializer)) {
                const name = node.name.getText();
                const value = node.initializer.getText().slice(1, -1); // Remove the quotes
                classProperties[name] = value;
            }

            // Check if the node is a call expression
            if (ts.isCallExpression(node)) {
                const expression = node.expression;
                // Check if the expression is a property access expression (e.g. httpClient.get)
                if (ts.isPropertyAccessExpression(expression)) {
                    const name = expression.name.getText();
                    // Check if the property name is one of the httpClient methods
                    if (['get', 'post', 'put', 'delete'].includes(name)) {
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
            ts.forEachChild(node, visit);
        }

        // Start traversing the AST from the root
        visit(sourceFile);
    }
});
