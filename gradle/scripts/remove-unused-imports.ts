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

    let totalRemoved = 0;

    for (const sourceFile of project.getSourceFiles()) {
        const importDecls = sourceFile.getImportDeclarations();
        let removed = 0;

        for (const importDecl of importDecls) {
            const namedImports = importDecl.getNamedImports();

            for (const namedImport of namedImports) {
                const identifier = namedImport.getNameNode();
                const symbol = typeChecker.getSymbolAtLocation(identifier);

                if (!symbol) continue;

                // Look at ALL references, skip self-reference (import)
                const references = identifier.findReferences();
                const isUsed = references.some(ref =>
                    ref.getReferences().some(r =>
                        r.getNode().getSourceFile() === sourceFile &&
                        r.getNode() !== identifier
                    )
                );

                if (!isUsed) {
                    namedImport.remove();
                    removed++;
                }
            }

            // If import is now empty, remove whole line
            if (
                importDecl.getNamedImports().length === 0 &&
                !importDecl.getDefaultImport() &&
                !importDecl.getNamespaceImport()
            ) {
                importDecl.remove();
            }
        }

        if (removed > 0) {
            await sourceFile.save();
            console.log(`🧹 Removed ${removed} unused imports from: ${sourceFile.getBaseName()}`);
            totalRemoved += removed;
        }
    }

    console.log(`✅ Done. Total removed: ${totalRemoved}`);
}

main().catch((err) => {
    console.error("❌ Error removing unused imports:", err);
    process.exit(1);
});
