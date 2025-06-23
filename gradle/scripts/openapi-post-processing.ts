import { Project } from "ts-morph";
import { join } from "path";
import { readdirSync, statSync, writeFileSync } from "fs";

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
    });
    project.addSourceFilesAtPaths(files);

    const typeChecker = project.getTypeChecker();
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
        const fixedContent = isWindows ? normalizeLineEndings(content, "CRLF") : content;

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
};

main().catch(err => {
    console.error("❌ Error:", err);
    process.exit(1);
});
