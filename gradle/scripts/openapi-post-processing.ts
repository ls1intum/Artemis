import { Project } from "ts-morph";
import { join } from "path";
import { readdirSync, statSync } from "fs";

const getAllTSFiles = (dir: string): string[] => {
    let results: string[] = [];
    for (const file of readdirSync(dir)) {
        const fullPath = join(dir, file);
        const stat = statSync(fullPath);
        if (stat.isDirectory()) {
            results = results.concat(getAllTSFiles(fullPath));
        } else if (file.endsWith(".ts")) {
            results.push(fullPath);
        }
    }
    return results;
};

const main = async () => {
    const directory = "src/main/webapp/app/openapi";
    const files = getAllTSFiles(directory);

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
                const isUsed = refs.some(r =>
                    r.getReferences().some(r2 =>
                        r2.getNode().getSourceFile() === sourceFile &&
                        r2.getNode() !== id
                    )
                );

                if (!isUsed) {
                    namedImport.remove();
                    removedImportsInFile++;
                }
            }

            if (
                importDeclaration.getNamedImports().length === 0 &&
                !importDeclaration.getDefaultImport() &&
                !importDeclaration.getNamespaceImport()
            ) {
                importDeclaration.remove();
            }
        }

        // 2) Strip leading underscores and trailing digits from every class‐method name:
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
