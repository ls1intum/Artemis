import { parse, simpleTraverse, TSESTree } from '@typescript-eslint/typescript-estree';
import { readFileSync, readdirSync } from 'fs';
import { join, resolve } from 'path';
import { Preprocessor } from './Preprocessor';
import { Postprocessor } from './Postprocessor';

interface RestCall {
    method: string;
    url: string;
    line: number;
    fileName: string;
}

interface SuperClass {
    name: string;
    path: string;
    childClasses: ChildClass[];
}

interface ChildClass {
    superClass: string;
    name: string;
    memberVariables: Map<string, string>;
}

interface parentMethodCalls {
    name: string;
    parameters: memberVariable[];
}

interface memberVariable {
    name: string;
    type: string;
    value?: string;
}

const pathPrefix = '../../../../../'


///////////////////////////////////////////////////////////////////////////////////////////////

// Function to read and parse a TypeScript file
function parseTypeScriptFile(filePath: string) {
    const code = readFileSync(filePath, 'utf8');
    return parse(code, {
        loc: true,
        comment: true,
        tokens: true,
        ecmaVersion: 2020,
        sourceType: 'module',
    });
}

// Recursively collect all TypeScript files in a directory
function collectTypeScriptFiles(dir: string, files: string[] = []) {
    const entries = readdirSync(dir, { withFileTypes: true });
    for (const entry of entries) {
        const fullPath = join(dir, entry.name);

        const filePathFromSrcFolder = fullPath.substring(fullPath.indexOf('src/main/webapp/app') - 'src/main/webapp/app'.length + 'src/main/webapp/app'.length);
        if (entry.isDirectory()) {
            collectTypeScriptFiles(fullPath, files);
        } else if (entry.isFile() && entry.name.endsWith('.ts')) {
            files.push(filePathFromSrcFolder);
        }
    }
    return files;
}

// Function to extract REST call information
function extractRestCalls(ast: TSESTree.Node, fileName: string) {
    const memberExpressions = new Map<string, string>();
    const restCalls: RestCall[] = [];
    simpleTraverse(ast, {
        enter(node) {
            if (node.type === 'PropertyDefinition' && node.value?.type === 'Literal' && node.key.type === 'Identifier' && node.value.value) {
                // if (node.object?.type === 'Identifier' && node.property.type === 'Identifier') {
                memberExpressions.set(node.key.name, node.value.value.toString());
                // }
            }
            if (node.type === 'CallExpression') {

                if (node.callee.type === 'MemberExpression') {
                    if (node.callee.object.type === 'Identifier' || node.callee.object.type === 'MemberExpression') {
                        if (node.callee.property.type === 'Identifier') {
                            if (['get', 'post', 'put', 'delete', 'patch'].includes(node.callee.property.name)) {
                                const method = node.callee.property.name.toUpperCase();
                                let url = node.arguments.length > 0 ? node.arguments[0]['value'] : 'Unknown URL';
                                const line = node.loc.start.line;

                                // Check if the first argument is a TemplateLiteral
                                if (node.arguments[0] && node.arguments[0].type === 'TemplateLiteral') {
                                    url = evaluateTemplateLiteralExpressions(node.arguments[0] as TSESTree.TemplateLiteral, memberExpressions);
                                }

                                restCalls.push({ method, url, line, fileName });
                            }
                        }
                        // }
                    }
                }
            }
        },
    });
    return restCalls;
}

function evaluateTemplateLiteralExpressions(templateLiteral: TSESTree.TemplateLiteral, memberExpressions: Map<string, string>): string {
    return templateLiteral.quasis.reduce((acc, quasi, index) => {
        let expression = '';
        if (index < templateLiteral.expressions.length) {
            console.log('templateLiteral.expressions[index]: ' + templateLiteral.expressions[index]);
            const expr = templateLiteral.expressions[index];
            // Example: Evaluate simple numeric expressions or return the original
            if (expr.type === 'Literal' && expr.value) {
                expression = expr.value.toString();
            } else if (expr.type === 'TemplateLiteral') {
                expression = evaluateTemplateLiteralExpressions(expr, memberExpressions);
            } else if (expr.type === 'MemberExpression') {
                let memberExprKey = '';
                if (expr.property.type === 'Identifier' && expr.property.name) {
                    memberExprKey = expr.property.name;
                } else if (expr.object.type === 'ThisExpression' && expr.property.type === 'Identifier') {
                    memberExprKey = `this.${expr.property.name}`;
                } else {
                    // Handle the case where expr.object or expr.property are not Identifiers
                    console.error('expr.object or expr.property are not Identifiers');
                    // Use a placeholder or alternative logic as needed
                }

                if (memberExpressions.has(memberExprKey)) {
                    console.log(`memberExpressions[${memberExprKey}]: ` + memberExpressions.get(memberExprKey));
                    let temp = memberExpressions.get(memberExprKey);
                    return acc + memberExpressions.get(memberExprKey);
                }
            } else {
                // For complex expressions, keep as-is
                expression = '${' + expr.type + '}';
            }
        }
        return acc + quasi.value.raw + expression;
    }, '');
}

// Example usage
const clientDirPath = resolve('../../../../../src/main/webapp/app');

const tsFiles = collectTypeScriptFiles(clientDirPath);
// const tsFiles: string[] = [];

// tsFiles.push('');

// tsFiles.push('src/main/webapp/app/admin/standardized-competencies/admin-standardized-competency.service.ts');
// tsFiles.push('src/main/webapp/app/admin/logs/logs.service.ts');
// tsFiles.push('src/main/webapp/app/complaints/complaint.service.ts');
// tsFiles.push('src/main/webapp/app/admin/organization-management/organization-management.service.ts');
// tsFiles.push('src/main/webapp/app/account/activate/activate.service.ts');
//
// tsFiles.push('src/main/webapp/app/shared/user-settings/user-settings.service.ts');
// tsFiles.push('src/main/webapp/app/course/tutorial-groups/services/tutorial-group-free-period.service.ts')
// tsFiles.push('src/main/webapp/app/exercises/shared/manage/exercise-paging.service.ts');
// tsFiles.push('/src/main/webapp/app/exercises/programming/manage/services/code-analysis-paging.service.ts');
// Parse each TypeScript file

// preprocess each file
tsFiles.forEach((filePath) => {
    let preProcessor = new Preprocessor(filePath);
    preProcessor.preprocessFile();
});

tsFiles.forEach((filePath) => {
    let postProcessor = new Postprocessor(filePath);
    if (filePath.endsWith('/exercise-paging.service.ts')) {
        console.log('found exercise-paging.service.ts');
    }
    postProcessor.extractRestCalls();
});

let counter = 0;
for (let restCallFile of Postprocessor.filesWithRestCalls) {
    // for (let restCall of restCallFile.restCalls) {
    //     if (restCall.url === undefined) {
    //         console.log(`REST call in ${restCallFile.filePath} at line ${restCall.line} has an undefined URL: `);
    //         console.log(restCall);
    //     }
    // }
    if (restCallFile.restCalls.length > 0) {
        counter += restCallFile.restCalls.length;
        console.log(`REST calls in ${restCallFile.filePath}:`, restCallFile.restCalls);
    }
}

console.log('Amount of REST-calls: ' + counter);
