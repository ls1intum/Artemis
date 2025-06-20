import { Project } from "ts-morph";
import { join } from "path";
import { readdirSync, statSync } from "fs";

const getAllTypescriptFiles = (dir: string): string[] => {
    let results: string[] = [];
    for (const file of readdirSync(dir)) {
        const fullPath = join(dir, file);
        const stat = statSync(fullPath);
        if (stat.isDirectory()) {
            results = results.concat(getAllTypescriptFiles(fullPath));
        } else if (file.endsWith(".ts")) {
            results.push(fullPath);
        }
    }
    return results;
};

const stripLeadingUnderscoresAndTrailingDigitsFromAllMethods = (sourceFile: any, renamedMethodsInFile: number) => {
    for (const clazz of sourceFile.getClasses()) {
        for (const method of clazz.getMethods()) {
            const oldName = method.getName();
            // remove all leading '_' then remove any digits at the end
            const newName = oldName
                .replace(/^_+/, "")
                .replace(/\d+$/, "");
            if (newName !== oldName) {
                method.getNameNode().rename(newName);
                renamedMethodsInFile++;
                console.log(`🔄 [${sourceFile.getBaseName()}] ${oldName} → ${newName}`);
            }
        }
    }
    return renamedMethodsInFile;
};

const main = async () => {
    const directory = "src/main/webapp/app/openapi";
    const files = getAllTypescriptFiles(directory);

    const project = new Project({
        tsConfigFilePath: "tsconfig.json",
        // we don't want to add files from tsconfig.json, as we only need the openapi files and not all ts files
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
                if (!symbol) {
                    continue;
                }

                const refs = id.findReferences();
                const isUsed = refs.some(referenceGroup =>
                    referenceGroup.getReferences().some(usage =>
                        usage.getNode().getSourceFile() === sourceFile &&
                        usage.getNode() !== id
                    )
                );

                if (!isUsed) {
                    namedImport.remove();
                    removedImportsInFile++;
                }
            }

            const emptyImportDeclaration = importDeclaration.getNamedImports().length === 0 &&
                !importDeclaration.getDefaultImport() &&
                !importDeclaration.getNamespaceImport();
            if (emptyImportDeclaration) {
                importDeclaration.remove();
            }
        }

        renamedMethodsInFile = stripLeadingUnderscoresAndTrailingDigitsFromAllMethods(sourceFile, renamedMethodsInFile);

        if (removedImportsInFile + renamedMethodsInFile > 0) {
            await sourceFile.save();
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
