/// <reference types="node" />

import { Node, Project, SourceFile } from "ts-morph";
import { join } from "path";
import { readdirSync, statSync, writeFileSync } from "fs";

// Post-processes the Angular client produced by the custom generator ls1intum/openapi-generator-angular22:
// - springdoc makes duplicate operation ids unique with a numeric suffix (startBatch_1), which the method names keep.
// - The generator can still leave unused imports in some models, which trips noUnusedLocals.

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
    // import takes the banner with it. Snapshot it up front, restore on write.
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

    const typeChecker = project.getTypeChecker();
    let totalRemovedImports = 0;
    let totalRenamedMethods = 0;

    for (const sourceFile of project.getSourceFiles()) {
        let removedImportsInFile = 0;
        let renamedMethodsInFile = 0;

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
        const path = sourceFile.getFilePath();
        const leadingBanner = leadingBanners.get(sourceFile);
        const text = sourceFile.getFullText();
        // The generator leaves trailing spaces on JSDoc lines; strip them so regenerating yields no whitespace churn.
        const content = (leadingBanner && !text.startsWith(leadingBanner) ? leadingBanner + text : text)
            .replace(/[ \t]+(?=\r?$)/gm, "")
            .replace(/(?:\r?\n)+$/, "\n");
        const fixedContent = isWindows ? normalizeLineEndings(content, "CRLF") : content;

        writeFileSync(path, fixedContent, "utf8");
        if (removedImportsInFile + renamedMethodsInFile > 0) {
            totalRemovedImports += removedImportsInFile;
            totalRenamedMethods += renamedMethodsInFile;
            console.log(`🧹 Removed ${removedImportsInFile} imports, renamed ${renamedMethodsInFile} methods in ${sourceFile.getBaseName()}`);
        }
    }

    console.log(`✅ Done. Total imports removed: ${totalRemovedImports}, methods renamed: ${totalRenamedMethods}`);
};

main().catch((error: unknown) => {
    console.error("OpenAPI post-processing failed:", error);
    process.exit(1);
});
