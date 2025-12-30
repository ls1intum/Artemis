import { Project, QuoteKind } from "ts-morph";
import { join } from "path";
import { existsSync, readdirSync, statSync, unlinkSync, writeFileSync } from "fs";

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
    results.push('openapi/openapi.yaml')
    return results;
};

const stripLeadingUnderscoresAndTrailingDigitsFromAllMethods = (sourceFile: any, renamedMethodsInFile: number) => {
    for (const clazz of sourceFile.getClasses()) {
        for (const method of clazz.getMethods()) {
            const oldName = method.getName();
            const newName = oldName.replace(/^_+/, "").replace(/\d+$/, "");
            if (newName !== oldName) {
                method.getNameNode().rename(newName);
                renamedMethodsInFile++;
                console.log(`🔄 [${sourceFile.getBaseName()}] ${oldName} → ${newName}`);
            }
        }
    }
    return renamedMethodsInFile;
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
        manipulationSettings: {
            quoteKind: QuoteKind.Single,
        },
    });
    project.addSourceFilesAtPaths(files);

    const typeChecker = project.getTypeChecker();
    const modelFiles = project
        .getSourceFiles()
        .filter((file) => file.getFilePath().includes(`${directory}/models/`) && file.getBaseName() !== "index.ts");
    const modelNames = new Set<string>();
    const modelNameToPath = new Map<string, string>();

    for (const modelFile of modelFiles) {
        const modelPath = `./${modelFile.getBaseNameWithoutExtension()}`;
        for (const iface of modelFile.getInterfaces()) {
            modelNames.add(iface.getName());
            modelNameToPath.set(iface.getName(), modelPath);
        }
        for (const alias of modelFile.getTypeAliases()) {
            modelNames.add(alias.getName());
            modelNameToPath.set(alias.getName(), modelPath);
        }
        for (const enumDecl of modelFile.getEnums()) {
            modelNames.add(enumDecl.getName());
            modelNameToPath.set(enumDecl.getName(), modelPath);
        }
    }

    for (const modelFile of modelFiles) {
        const localNames = new Set<string>([
            ...modelFile.getInterfaces().map((decl) => decl.getName()),
            ...modelFile.getTypeAliases().map((decl) => decl.getName()),
            ...modelFile.getEnums().map((decl) => decl.getName()),
        ]);
        const referencedNames = new Set<string>();
        const tokens = modelFile.getFullText().match(/\b[A-Z][A-Za-z0-9_]*\b/g) ?? [];
        for (const token of tokens) {
            if (modelNames.has(token) && !localNames.has(token)) {
                referencedNames.add(token);
            }
        }

        if (referencedNames.size > 0) {
            const importsByPath = new Map<string, string[]>();
            for (const name of referencedNames) {
                const modulePath = modelNameToPath.get(name);
                if (!modulePath) {
                    continue;
                }
                const names = importsByPath.get(modulePath) ?? [];
                names.push(name);
                importsByPath.set(modulePath, names);
            }

            for (const [modulePath, names] of importsByPath.entries()) {
                const namedImports = Array.from(new Set(names)).sort();
                const existing = modelFile.getImportDeclarations().find((imp) => imp.getModuleSpecifierValue() === modulePath);
                if (existing) {
                    existing.setIsTypeOnly(true);
                    const existingNames = new Set(existing.getNamedImports().map((imp) => imp.getName()));
                    for (const name of namedImports) {
                        if (!existingNames.has(name)) {
                            existing.addNamedImport(name);
                        }
                    }
                } else {
                    modelFile.addImportDeclaration({
                        isTypeOnly: true,
                        moduleSpecifier: modulePath,
                        namedImports,
                    });
                }
            }
        }

        for (const importDeclaration of modelFile.getImportDeclarations()) {
            if (importDeclaration.getModuleSpecifierValue() === "./index") {
                importDeclaration.remove();
            }
        }
    }

    const normalizeModelImportPath = (modulePath: string) => {
        if (!modulePath.startsWith("./")) {
            return modulePath;
        }
        return `../models/${modulePath.slice(2)}`;
    };

    for (const sourceFile of project.getSourceFiles()) {
        const importDeclarations = sourceFile.getImportDeclarations().filter((imp) => imp.getModuleSpecifierValue().startsWith("../models/"));
        if (importDeclarations.length === 0) {
            continue;
        }

        for (const importDeclaration of importDeclarations) {
            importDeclaration.setModuleSpecifier(importDeclaration.getModuleSpecifierValue());
            const namedImports = importDeclaration.getNamedImports();
            if (namedImports.length === 0) {
                continue;
            }

            const importMap = new Map<string, Set<string>>();
            const unmappedNames: string[] = [];

            for (const namedImport of namedImports) {
                const name = namedImport.getName();
                const modelPath = modelNameToPath.get(name);
                if (!modelPath) {
                    unmappedNames.push(name);
                    continue;
                }
                const normalizedPath = normalizeModelImportPath(modelPath);
                const names = importMap.get(normalizedPath) ?? new Set<string>();
                names.add(name);
                importMap.set(normalizedPath, names);
            }

            namedImports.forEach((namedImport) => namedImport.remove());
            for (const name of unmappedNames) {
                importDeclaration.addNamedImport(name);
            }

            for (const [modulePath, names] of importMap.entries()) {
                const existing = sourceFile.getImportDeclarations().find((imp) => imp.getModuleSpecifierValue() === modulePath);
                if (existing) {
                    const existingNames = new Set(existing.getNamedImports().map((imp) => imp.getName()));
                    for (const name of Array.from(names)) {
                        if (!existingNames.has(name)) {
                            existing.addNamedImport(name);
                        }
                    }
                } else {
                    sourceFile.addImportDeclaration({
                        moduleSpecifier: modulePath,
                        namedImports: Array.from(names).sort(),
                    });
                }
            }
        }
    }
    let totalRemovedImports = 0;
    let totalRenamedMethods = 0;

    for (const sourceFile of project.getSourceFiles()) {
        let removedImportsInFile = 0;
        let renamedMethodsInFile = 0;

        for (const importDeclaration of sourceFile.getImportDeclarations()) {
            for (const namedImport of importDeclaration.getNamedImports()) {
                const id = namedImport.getNameNode();
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
        const path = sourceFile.getFilePath();
        const content = sourceFile.getFullText();
        const normalizedModelImports = content.replace(/from \"(\.\.\/models\/[^\"]+)\"/g, "from '$1'");
        const fixedContent = isWindows ? normalizeLineEndings(normalizedModelImports, "CRLF") : normalizedModelImports;

        writeFileSync(path, fixedContent, "utf8");
        if (removedImportsInFile + renamedMethodsInFile > 0) {
            totalRemovedImports += removedImportsInFile;
            totalRenamedMethods += renamedMethodsInFile;
            console.log(
                `🧹 Removed ${removedImportsInFile} imports, ` +
                `renamed ${renamedMethodsInFile} methods in ${sourceFile.getBaseName()}`
            );
        }
    }

    console.log(
        `✅ Done. Total imports removed: ${totalRemovedImports}, ` +
        `methods renamed: ${totalRenamedMethods}`
    );

    const modelsIndexPath = `${directory}/models/index.ts`;
    if (existsSync(modelsIndexPath)) {
        unlinkSync(modelsIndexPath);
    }
};

main().catch(err => {
    console.error("❌ Error:", err);
    process.exit(1);
});
