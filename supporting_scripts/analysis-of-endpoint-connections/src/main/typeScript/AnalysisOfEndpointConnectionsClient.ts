import * as ts from 'typescript';
import * as fs from 'fs';

// Get the file names from the command line arguments
const fileNames = process.argv.slice(2);
fileNames.push("src/main/webapp/app/course/tutorial-groups/services/tutorial-group-free-period.service.ts")

let restCalls: Array<{method: string, url: string, line: number, filePath: string}> = [];


fileNames.forEach(fileName => {
    if (fileName.endsWith('.ts')) {
    // Load the TypeScript file
        const sourceFile = ts.createSourceFile(fileName, require('fs').readFileSync(fileName).toString(), ts.ScriptTarget.ES2015, true);

        // This function will be called for each node in the AST
        function visit(node: ts.Node) {
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
                            console.log(`with URL: ${node.arguments[0].getText()}`);
                        } else {
                            console.log('No arguments provided for this REST call');
                        }
                        console.log(`At line: ${sourceFile.getLineAndCharacterOfPosition(node.getStart()).line + 1}`);
                        console.log(`At file path: ${fileName}`);
                        console.log('-----------------------------------');

                        let restCall = {
                            method: name,
                            url: node.arguments.length > 0 ? node.arguments[0].getText() : 'No arguments provided for this REST call',
                            line: sourceFile.getLineAndCharacterOfPosition(node.getStart()).line + 1,
                            filePath: fileName
                        };
                        restCalls.push(restCall);
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

// Write the restCalls array to a JSON file
fs.writeFileSync('supporting_scripts/analysis-of-endpoint-connections/restCalls.json', JSON.stringify(restCalls, null, 2));
