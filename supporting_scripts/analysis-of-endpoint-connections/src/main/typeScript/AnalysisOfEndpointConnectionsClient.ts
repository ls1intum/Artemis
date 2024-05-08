import * as ts from 'typescript';

// Get the file names from the command line arguments
const fileNames = process.argv.slice(2);

const testFileNames = ["src/main/webapp/app/course/tutorial-groups/services/tutorial-group-free-period.service.ts"];

testFileNames.forEach(fileName => {
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
                    console.log(`with URL: ${node.arguments[0].getText()}`);
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
});
