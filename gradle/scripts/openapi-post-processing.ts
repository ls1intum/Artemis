/// <reference types="node" />

import { Node, Project, SourceFile, SyntaxKind } from "ts-morph";
import { join } from "path";
import { readFileSync, readdirSync, statSync, writeFileSync } from "fs";
import { parse } from "yaml";

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
    $ref?: string;
    items?: OpenApiSchema;
    oneOf?: OpenApiSchema[];
    anyOf?: OpenApiSchema[];
    allOf?: OpenApiSchema[];
    properties?: Record<string, OpenApiSchema>;
}

interface OpenApiOperation {
    requestBody?: {
        content?: Record<string, {
            schema?: OpenApiSchema;
        }>;
    };
}

interface OpenApiSpecification {
    paths?: Record<string, Record<string, OpenApiOperation>>;
    components?: {
        schemas?: Record<string, OpenApiSchema>;
    };
}

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
    const leadingBanners = new Map<SourceFile, string>();
    for (const sourceFile of project.getSourceFiles()) {
        const banner = /^\/\*\*[\s\S]*?\*\/\r?\n/.exec(sourceFile.getFullText())?.[0];
        if (banner) {
            leadingBanners.set(sourceFile, banner);
        }
    }

    const openApiSpecification = parse(readFileSync("openapi/openapi.yaml", "utf8")) as OpenApiSpecification;
    const multipartObjectPartNames = getMultipartObjectPartNames(openApiSpecification);
    const multipartObjectPartNameSet = new Set(multipartObjectPartNames);
    const totalReplacedUnionModels = replaceOneOfModelsWithUnionTypes(project, openApiSpecification);

    const typeChecker = project.getTypeChecker();
    let totalRemovedImports = 0;
    let totalRenamedMethods = 0;
    let totalSerializedFormDataParts = 0;

    for (const sourceFile of project.getSourceFiles()) {
        let removedImportsInFile = 0;
        let renamedMethodsInFile = 0;
        let serializedFormDataPartsInFile = 0;

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
        const path = sourceFile.getFilePath();
        const leadingBanner = leadingBanners.get(sourceFile);
        const text = sourceFile.getFullText();
        const content = (leadingBanner && !text.startsWith(leadingBanner) ? leadingBanner + text : text)
            .replace(/[ \t]+(?=\r?$)/gm, "")
            .replace(/(?:\r?\n)+$/, "\n");
        const fixedContent = isWindows ? normalizeLineEndings(content, "CRLF") : content;

        writeFileSync(path, fixedContent, "utf8");
        if (removedImportsInFile + renamedMethodsInFile + serializedFormDataPartsInFile > 0) {
            totalRemovedImports += removedImportsInFile;
            totalRenamedMethods += renamedMethodsInFile;
            totalSerializedFormDataParts += serializedFormDataPartsInFile;
            console.log(
                `🧹 Removed ${removedImportsInFile} imports, ` +
                `renamed ${renamedMethodsInFile} methods, ` +
                `serialized ${serializedFormDataPartsInFile} multipart model parts in ${sourceFile.getBaseName()}`
            );
        }
    }

    if (totalSerializedFormDataParts !== multipartObjectPartNames.length) {
        throw new Error(`Expected ${multipartObjectPartNames.length} multipart object parts to be serialized, but serialized ${totalSerializedFormDataParts}`);
    }

    console.log(
        `✅ Done. Total imports removed: ${totalRemovedImports}, ` +
        `methods renamed: ${totalRenamedMethods}, ` +
        `multipart model parts serialized: ${totalSerializedFormDataParts}, ` +
        `oneOf models converted to union types: ${totalReplacedUnionModels}`
    );
};

main().catch((error: unknown) => {
    console.error("OpenAPI post-processing failed:", error);
    process.exit(1);
});
