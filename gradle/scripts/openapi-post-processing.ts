/// <reference types="node" />

import { Node, Project, SourceFile, SyntaxKind } from "ts-morph";
import { join } from "path";
import { readFileSync, readdirSync, statSync, writeFileSync } from "fs";
import { parse } from "yaml";

// Post-processes the Angular client produced by the custom generator ls1intum/openapi-generator-angular22.
//
// Removing unused imports and cleaning method names were the original jobs. Most other steps compensate for gaps in
// that generator's templates, which the quiz module was the first to hit:
// - oneOf schemas render as one merged interface that requires the fields of every branch, instead of a union.
// - HttpResponse is always imported, but only used for file downloads, which trips noUnusedLocals.
// - Multipart parts are appended as they are: a DTO is sent as "[object Object]" and a file without its name.
// - Methods can only return the body, so a response header the contract declares cannot be read.
// Each step says which gap it covers. Once the generator handles a gap itself, its step can be deleted; the count
// checks at the end of main() fail the build if a step no longer finds what the specification says it should.

const getAllOpenApiFiles = (dir: string): string[] => {
    let results: string[] = [];
    for (const file of readdirSync(dir)) {
        const fullPath = join(dir, file);
        const stat = statSync(fullPath);
        if (stat.isDirectory()) {
            results = results.concat(getAllOpenApiFiles(fullPath));
        } else {
            results.push(fullPath);
        }
    }
    return results;
};

const stripLeadingUnderscoresAndTrailingDigitsFromAllMethods = (sourceFile: SourceFile, renamedMethodsInFile: number) => {
    for (const clazz of sourceFile.getClasses()) {
        for (const method of clazz.getMethods()) {
            const oldName = method.getName();
            const newName = oldName.replace(/^_+/, "").replace(/\d+$/, "");
            const nameNode = method.getNameNode();
            if (newName !== oldName && Node.isIdentifier(nameNode)) {
                nameNode.rename(newName);
                renamedMethodsInFile++;
                console.log(`🔄 [${sourceFile.getBaseName()}] ${oldName} → ${newName}`);
            }
        }
    }
    return renamedMethodsInFile;
};

// The generator appends every non-array multipart part as it is. FormData turns an object into the text
// "[object Object]" with content type text/plain, which Spring cannot bind to a @RequestPart DTO. Wrap exactly the
// parts the specification types as objects in a JSON Blob, like the hand-written objectToJsonBlob. The upstream
// typescript-angular template does this with an isModel branch; the custom template has none.
const serializeGeneratedModelFormDataParts = (sourceFile: SourceFile, serializedPartsInFile: number, multipartObjectPartNames: Set<string>) => {
    const generatedModelTypes = new Set(
        sourceFile
            .getImportDeclarations()
            .filter(declaration => declaration.getModuleSpecifierValue().includes("/model/"))
            .flatMap(declaration => declaration.getNamedImports().map(namedImport => namedImport.getName())),
    );

    for (const callExpression of sourceFile.getDescendantsOfKind(SyntaxKind.CallExpression)) {
        if (callExpression.getExpression().getText() !== "formData.append") {
            continue;
        }

        const [partName, formDataValue] = callExpression.getArguments();
        if (!Node.isStringLiteral(partName) || !multipartObjectPartNames.has(partName.getLiteralValue())) {
            continue;
        }

        const formDataValueText = formDataValue.getText();
        if (Node.isNewExpression(formDataValue) && formDataValueText.startsWith("new Blob([JSON.stringify(") && formDataValueText.includes("type: 'application/json'")) {
            serializedPartsInFile++;
            continue;
        }
        if (!Node.isIdentifier(formDataValue)) {
            throw new Error(`Cannot serialize multipart object part in ${sourceFile.getBaseName()}: ${callExpression.getText()}`);
        }

        const parameterDeclaration = formDataValue.getSymbol()?.getDeclarations().find(Node.isParameterDeclaration);
        const parameterType = parameterDeclaration?.getTypeNode()?.getText();
        if (!parameterType || !generatedModelTypes.has(parameterType)) {
            throw new Error(`Multipart object part is not a generated model in ${sourceFile.getBaseName()}: ${callExpression.getText()}`);
        }

        formDataValue.replaceWithText(`new Blob([JSON.stringify(${formDataValue.getText()})], { type: 'application/json' })`);
        serializedPartsInFile++;
    }

    return serializedPartsInFile;
};

interface OpenApiSchema {
    type?: string;
    format?: string;
    $ref?: string;
    items?: OpenApiSchema;
    oneOf?: OpenApiSchema[];
    anyOf?: OpenApiSchema[];
    allOf?: OpenApiSchema[];
    properties?: Record<string, OpenApiSchema>;
}

interface OpenApiOperation {
    operationId?: string;
    requestBody?: {
        content?: Record<string, {
            schema?: OpenApiSchema;
        }>;
    };
    responses?: Record<string, {
        headers?: Record<string, unknown>;
    }>;
}

interface OpenApiSpecification {
    paths?: Record<string, Record<string, OpenApiOperation>>;
    components?: {
        schemas?: Record<string, OpenApiSchema>;
    };
}

// The generated code cannot say which formData.append calls or methods to rewrite: every part looks the same. The
// specification can. These readers derive each step's targets from openapi.yaml, and their lengths are the expected
// counts checked at the end of main(). A count taken from the generated code would only check a step against itself.
const isObjectSchema = (schema: OpenApiSchema): boolean => {
    return Boolean(
        schema.$ref ||
        schema.type === "object" ||
        (schema.items && isObjectSchema(schema.items)) ||
        schema.oneOf?.some(isObjectSchema) ||
        schema.anyOf?.some(isObjectSchema) ||
        schema.allOf?.some(isObjectSchema),
    );
};

const getMultipartObjectPartNames = (openApiSpecification: OpenApiSpecification): string[] => {
    return Object.values(openApiSpecification.paths ?? {}).flatMap(path =>
        Object.values(path).flatMap(operation => {
            const multipartSchema = operation.requestBody?.content?.["multipart/form-data"]?.schema;
            if (!multipartSchema?.properties) {
                return [];
            }
            return Object.entries(multipartSchema.properties)
                .filter(([, schema]) => isObjectSchema(schema))
                .map(([partName]) => partName);
        }),
    );
};

const getMultipartBinaryPartNames = (openApiSpecification: OpenApiSpecification): string[] => {
    return Object.values(openApiSpecification.paths ?? {}).flatMap(path =>
        Object.values(path).flatMap(operation => {
            const multipartSchema = operation.requestBody?.content?.["multipart/form-data"]?.schema;
            if (!multipartSchema?.properties) {
                return [];
            }
            return Object.entries(multipartSchema.properties)
                .filter(([, schema]) => {
                    const valueSchema = schema.items ?? schema;
                    return valueSchema.type === "string" && valueSchema.format === "binary";
                })
                .map(([partName]) => partName);
        }),
    );
};

const getOperationIdsWithResponseHeaders = (openApiSpecification: OpenApiSpecification): string[] => {
    return Object.values(openApiSpecification.paths ?? {}).flatMap(path =>
        Object.values(path)
            .filter(operation => Object.values(operation.responses ?? {}).some(response => response.headers))
            .flatMap(operation => (operation.operationId ? [operation.operationId] : [])),
    );
};

// An operation that declares a response header is useless as a bare Observable<T>: HttpClient only exposes headers
// when asked to observe the whole response. Opt exactly those operations into observe: 'response' and widen their
// return type, so a caller can read the header the contract promises. The custom template returns only the body;
// the upstream template lets the caller choose through observe overloads.
const observeFullResponseForHeaderOperations = (sourceFile: SourceFile, observedOperationsInFile: number, operationIdsWithResponseHeaders: Set<string>) => {
    for (const method of sourceFile.getClasses().flatMap(classDeclaration => classDeclaration.getMethods())) {
        if (!operationIdsWithResponseHeaders.has(method.getName())) {
            continue;
        }

        const returnTypeNode = method.getReturnTypeNode();
        const returnType = returnTypeNode?.getText();
        if (!returnType?.startsWith("Observable<")) {
            throw new Error(`Cannot observe the full response of ${method.getName()} in ${sourceFile.getBaseName()}: ${returnType}`);
        }
        if (returnType.startsWith("Observable<HttpResponse<")) {
            observedOperationsInFile++;
            continue;
        }

        const httpCall = method
            .getDescendantsOfKind(SyntaxKind.CallExpression)
            .find(callExpression => callExpression.getExpression().getText().startsWith("this.http."));
        if (!httpCall) {
            throw new Error(`No HttpClient call to observe in ${method.getName()} in ${sourceFile.getBaseName()}`);
        }

        returnTypeNode.replaceWithText("Observable<HttpResponse<" + returnType.slice("Observable<".length, -1) + ">>");
        httpCall.addArgument("{ observe: 'response' }");

        const httpImport = sourceFile.getImportDeclaration(declaration => declaration.getModuleSpecifierValue() === "@angular/common/http");
        if (httpImport && !httpImport.getNamedImports().some(namedImport => namedImport.getName() === "HttpResponse")) {
            httpImport.addNamedImport("HttpResponse");
        }

        observedOperationsInFile++;
    }

    return observedOperationsInFile;
};

// The generator types a binary part as Blob. FormData.append then labels it "blob", because only a File carries a
// name, and the server resolves uploads by their original filename — so two Blobs collide on one key. Retype the
// parameter to File and pass the name explicitly. The Blob type comes from the upstream TypeScriptAngularClientCodegen
// that the generator extends: it returns Blob for every file schema in Java, so typeMappings cannot change it, and
// the upstream template does not pass a name either.
const nameGeneratedBinaryFormDataParts = (sourceFile: SourceFile, namedPartsInFile: number, multipartBinaryPartNames: Set<string>) => {
    for (const callExpression of sourceFile.getDescendantsOfKind(SyntaxKind.CallExpression)) {
        if (callExpression.getExpression().getText() !== "formData.append") {
            continue;
        }

        const [partName, formDataValue, fileName] = callExpression.getArguments();
        if (!Node.isStringLiteral(partName) || !multipartBinaryPartNames.has(partName.getLiteralValue())) {
            continue;
        }
        if (fileName) {
            namedPartsInFile++;
            continue;
        }
        if (!Node.isIdentifier(formDataValue)) {
            throw new Error(`Cannot name multipart binary part in ${sourceFile.getBaseName()}: ${callExpression.getText()}`);
        }

        const method = callExpression.getFirstAncestorByKind(SyntaxKind.MethodDeclaration);
        const parameter = method?.getParameter(partName.getLiteralValue());
        const parameterType = parameter?.getTypeNode()?.getText();
        if (!parameter || !parameterType?.includes("Blob")) {
            throw new Error(`Multipart binary part is not a Blob parameter in ${sourceFile.getBaseName()}: ${callExpression.getText()}`);
        }

        parameter.setType(parameterType.replaceAll("Blob", "File"));
        callExpression.addArgument(`${formDataValue.getText()}.name`);
        namedPartsInFile++;
    }

    return namedPartsInFile;
};

const referencedUnionSchemas = (openApiSpecification: OpenApiSpecification): Array<[string, string[]]> => {
    return Object.entries(openApiSpecification.components?.schemas ?? {}).flatMap(([schemaName, schema]) => {
        if (!schema.oneOf || schema.oneOf.length < 2) {
            return [];
        }

        const referencedSchemaNames = schema.oneOf.map(branch => branch.$ref?.split("/").at(-1));
        if (referencedSchemaNames.some(referencedSchemaName => referencedSchemaName === undefined)) {
            return [];
        }

        return [[schemaName, referencedSchemaNames.filter(referencedSchemaName => referencedSchemaName !== undefined)]];
    });
};

// The generator has no oneOf support: it imports every branch and then renders one interface with the fields of all
// branches, which no real value satisfies and which TypeScript cannot narrow. Replace each such model with a union
// of its branches, so a switch on the discriminator narrows to the right branch. Runs once over the whole project,
// before the per-file steps, because a union file imports its branch files.
const replaceOneOfModelsWithUnionTypes = (project: Project, openApiSpecification: OpenApiSpecification) => {
    const modelSourceFilesByName = new Map<string, SourceFile>();
    for (const sourceFile of project.getSourceFiles()) {
        if (!sourceFile.getFilePath().replaceAll("\\", "/").includes("/openapi/model/")) {
            continue;
        }
        for (const declaration of [...sourceFile.getInterfaces(), ...sourceFile.getTypeAliases()]) {
            if (declaration.isExported()) {
                modelSourceFilesByName.set(declaration.getName(), sourceFile);
            }
        }
    }

    let replacedUnionModels = 0;
    for (const [schemaName, referencedSchemaNames] of referencedUnionSchemas(openApiSpecification)) {
        const sourceFile = modelSourceFilesByName.get(schemaName);
        const referencedSourceFiles = referencedSchemaNames.map(referencedSchemaName => modelSourceFilesByName.get(referencedSchemaName));
        if (!sourceFile || referencedSourceFiles.some(referencedSourceFile => referencedSourceFile === undefined)) {
            continue;
        }
        for (const [index, referencedSchemaName] of referencedSchemaNames.entries()) {
            const referencedSourceFile = referencedSourceFiles[index];
            if (!referencedSourceFile || referencedSourceFile === sourceFile) {
                continue;
            }

            const moduleSpecifier = `./${referencedSourceFile.getBaseNameWithoutExtension()}`;
            const existingImport = sourceFile.getImportDeclaration(declaration => declaration.getModuleSpecifierValue() === moduleSpecifier);
            if (existingImport) {
                if (!existingImport.getNamedImports().some(namedImport => namedImport.getName() === referencedSchemaName)) {
                    existingImport.addNamedImport(referencedSchemaName);
                }
            } else {
                sourceFile.addImportDeclaration({
                    isTypeOnly: true,
                    namedImports: [referencedSchemaName],
                    moduleSpecifier,
                });
            }
        }

        const unionType = referencedSchemaNames.join(" | ");
        const interfaceDeclaration = sourceFile.getInterface(schemaName);
        const typeAliasDeclaration = sourceFile.getTypeAlias(schemaName);
        if (interfaceDeclaration) {
            interfaceDeclaration.replaceWithText(`export type ${schemaName} = ${unionType};`);
        } else if (typeAliasDeclaration) {
            typeAliasDeclaration.setType(unionType);
        } else {
            continue;
        }

        for (const staleTypeAlias of sourceFile.getTypeAliases().filter(declaration => declaration.getName() !== schemaName && declaration.getName().startsWith(schemaName))) {
            staleTypeAlias.remove();
        }
        for (const staleVariableStatement of sourceFile.getVariableStatements().filter(statement =>
            statement.getDeclarations().every(declaration => declaration.getName().startsWith(schemaName))
        )) {
            staleVariableStatement.remove();
        }

        replacedUnionModels++;
    }
    return replacedUnionModels;
};

const normalizeLineEndings = (text: string, lineEnding: "CRLF" | "LF" = "CRLF") => {
    return lineEnding === "CRLF"
        ? text.replace(/\r?\n/g, "\r\n")
        : text.replace(/\r?\n/g, "\n");
};

const main = async () => {
    const isWindows = process.platform === "win32";
    const directory = "src/main/webapp/app/openapi";
    const files = getAllOpenApiFiles(directory);

    const project = new Project({
        tsConfigFilePath: "tsconfig.json",
        skipAddingFilesFromTsConfig: true,
    });
    project.addSourceFilesAtPaths(files);

    // The generator writes its banner as a leading comment on the file's first statement, so removing an unused first
    // import or replacing a first-statement interface takes the banner with it. Snapshot it up front, restore on write.
    // The banner is the "/** ... */" block at the very start of the file, plus the line break that ends it.
    // The s flag lets "." match line breaks too, and "*?" stops at the first "*/", so the match never runs into later comments.
    const generatedBanner = /^\/\*\*.*?\*\/\r?\n/s;
    const leadingBanners = new Map<SourceFile, string>();
    for (const sourceFile of project.getSourceFiles()) {
        const banner = generatedBanner.exec(sourceFile.getFullText())?.[0];
        if (banner) {
            leadingBanners.set(sourceFile, banner);
        }
    }

    const openApiSpecification = parse(readFileSync("openapi/openapi.yaml", "utf8")) as OpenApiSpecification;
    const multipartObjectPartNames = getMultipartObjectPartNames(openApiSpecification);
    const multipartObjectPartNameSet = new Set(multipartObjectPartNames);
    const multipartBinaryPartNames = getMultipartBinaryPartNames(openApiSpecification);
    const multipartBinaryPartNameSet = new Set(multipartBinaryPartNames);
    const operationIdsWithResponseHeaders = getOperationIdsWithResponseHeaders(openApiSpecification);
    const operationIdsWithResponseHeaderSet = new Set(operationIdsWithResponseHeaders);
    const totalReplacedUnionModels = replaceOneOfModelsWithUnionTypes(project, openApiSpecification);

    const typeChecker = project.getTypeChecker();
    let totalRemovedImports = 0;
    let totalRenamedMethods = 0;
    let totalSerializedFormDataParts = 0;
    let totalNamedBinaryParts = 0;
    let totalObservedOperations = 0;

    for (const sourceFile of project.getSourceFiles()) {
        let removedImportsInFile = 0;
        let renamedMethodsInFile = 0;
        let serializedFormDataPartsInFile = 0;
        let namedBinaryPartsInFile = 0;
        let observedOperationsInFile = 0;

        // Needed because tsconfig sets noUnusedLocals: the template always imports HttpResponse, and the union rewrite
        // leaves the property imports of the merged interface behind.
        for (const importDeclaration of sourceFile.getImportDeclarations()) {
            for (const namedImport of importDeclaration.getNamedImports()) {
                const id = namedImport.getNameNode();
                if (!Node.isIdentifier(id)) continue;
                const symbol = typeChecker.getSymbolAtLocation(id);
                if (!symbol) continue;

                const refs = id.findReferences();
                const isUsed = refs.some(refGroup =>
                    refGroup.getReferences().some(usage =>
                        usage.getNode().getSourceFile() === sourceFile &&
                        usage.getNode() !== id
                    )
                );

                if (!isUsed) {
                    namedImport.remove();
                    removedImportsInFile++;
                }
            }

            const isEmpty = importDeclaration.getNamedImports().length === 0 &&
                !importDeclaration.getDefaultImport() &&
                !importDeclaration.getNamespaceImport();
            if (isEmpty) {
                importDeclaration.remove();
            }
        }

        renamedMethodsInFile = stripLeadingUnderscoresAndTrailingDigitsFromAllMethods(sourceFile, renamedMethodsInFile);
        serializedFormDataPartsInFile = serializeGeneratedModelFormDataParts(sourceFile, serializedFormDataPartsInFile, multipartObjectPartNameSet);
        namedBinaryPartsInFile = nameGeneratedBinaryFormDataParts(sourceFile, namedBinaryPartsInFile, multipartBinaryPartNameSet);
        observedOperationsInFile = observeFullResponseForHeaderOperations(sourceFile, observedOperationsInFile, operationIdsWithResponseHeaderSet);
        const path = sourceFile.getFilePath();
        const leadingBanner = leadingBanners.get(sourceFile);
        const text = sourceFile.getFullText();
        // The generator leaves trailing spaces on JSDoc lines; strip them so regenerating yields no whitespace churn.
        const content = (leadingBanner && !text.startsWith(leadingBanner) ? leadingBanner + text : text)
            .replace(/[ \t]+(?=\r?$)/gm, "")
            .replace(/(?:\r?\n)+$/, "\n");
        const fixedContent = isWindows ? normalizeLineEndings(content, "CRLF") : content;

        writeFileSync(path, fixedContent, "utf8");
        if (removedImportsInFile + renamedMethodsInFile + serializedFormDataPartsInFile + namedBinaryPartsInFile + observedOperationsInFile > 0) {
            totalRemovedImports += removedImportsInFile;
            totalRenamedMethods += renamedMethodsInFile;
            totalSerializedFormDataParts += serializedFormDataPartsInFile;
            totalNamedBinaryParts += namedBinaryPartsInFile;
            totalObservedOperations += observedOperationsInFile;
            console.log(
                `🧹 Removed ${removedImportsInFile} imports, ` +
                `renamed ${renamedMethodsInFile} methods, ` +
                `serialized ${serializedFormDataPartsInFile} multipart model parts, ` +
                `named ${namedBinaryPartsInFile} multipart binary parts, ` +
                `observed ${observedOperationsInFile} full responses in ${sourceFile.getBaseName()}`
            );
        }
    }

    if (totalSerializedFormDataParts !== multipartObjectPartNames.length) {
        throw new Error(`Expected ${multipartObjectPartNames.length} multipart object parts to be serialized, but serialized ${totalSerializedFormDataParts}`);
    }

    if (totalNamedBinaryParts !== multipartBinaryPartNames.length) {
        throw new Error(`Expected ${multipartBinaryPartNames.length} multipart binary parts to be named, but named ${totalNamedBinaryParts}`);
    }

    if (totalObservedOperations !== operationIdsWithResponseHeaders.length) {
        throw new Error(`Expected ${operationIdsWithResponseHeaders.length} operations to observe the full response, but observed ${totalObservedOperations}`);
    }

    console.log(
        `✅ Done. Total imports removed: ${totalRemovedImports}, ` +
        `methods renamed: ${totalRenamedMethods}, ` +
        `multipart model parts serialized: ${totalSerializedFormDataParts}, ` +
        `multipart binary parts named: ${totalNamedBinaryParts}, ` +
        `full responses observed: ${totalObservedOperations}, ` +
        `oneOf models converted to union types: ${totalReplacedUnionModels}`
    );
};

main().catch((error: unknown) => {
    console.error("OpenAPI post-processing failed:", error);
    process.exit(1);
});
