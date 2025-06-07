import { Project } from "ts-morph";
import { join } from "path";
import { readdirSync, statSync } from "fs";

function getAllTSFiles(dir: string): string[] {
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
}

async function main() {
    const directory = "src/main/webapp/app/openapi";
    const files = getAllTSFiles(directory);

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

        // 1) Your original unused‐import cleanup:
        for (const importDecl of sourceFile.getImportDeclarations()) {
            for (const namedImport of importDecl.getNamedImports()) {
                const id = namedImport.getNameNode();
                const sym = typeChecker.getSymbolAtLocation(id);
                if (!sym) continue;

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
                importDecl.getNamedImports().length === 0 &&
                !importDecl.getDefaultImport() &&
                !importDecl.getNamespaceImport()
            ) {
                importDecl.remove();
            }
        }

        // 2) Strip leading underscores and trailing digits from every class‐method name:
        for (const cls of sourceFile.getClasses()) {
            for (const method of cls.getMethods()) {
                const oldName = method.getName();
                // remove all leading '_' then remove any digits at the end
                const newName = oldName
                    .replace(/^_+/, "")
                    .replace(/\d+$/, "");
                if (newName !== oldName) {
                    // full rename (updates all call sites)
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
}

main().catch(err => {
    console.error("❌ Error:", err);
    process.exit(1);
});
