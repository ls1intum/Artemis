// Setup:
// npm install -g ts-node
// or npm install --save-dev ts-node

// CMD:
// ts-node -P tsconfig-ts-node.json supporting_scripts/analyze_client_modules.ts

/**
 * # Angular Technical Debt Analyzer
 *
 * ## Purpose:
 * Uncover and manage technical debt across your Angular modules through
 * incremental, actionable metrics. Add one metric at a time and watch your
 * debt score evolve with your codebase.
 *
 * ## Component Inventory
 * - Count `@Component`, `@Directive`, `@Pipe`, `@Injectable` declarations
 * - Track `@Input` / `@Output` and `@ViewChild` / `@ViewChildren` vs. modern usage
 *
 * ## Change Detection
 * - Identify explicit `OnPush`, explicit `Default`, and implicit (none)
 * - **Goal:** 100% explicit `OnPush` for consistent, performant rendering -> Phase out ZoneJS
 *
 * ## Signal Readiness
 * - Detect components using Angular signals `signal`, ignore `OnPush` components
 *
 * ## Complexity & Dependencies
 * - Measure lines of code (LOC) and method count per component
 * - Audit imports: internal modules, npm packages, cross-module links
 *   **Goal:** eliminate cross-module dependencies outside of `shared` and `core` module
 *
 * ## RxJS Usage
 * - Count `subscribe` / `unsubscribe` calls
 *   **Goal:** Reduce in favor of `async` pipe and signals
 * - List other RxJS imports and usages
 *
 * ## Service Catalog
 * - Count services (`*.service.ts`)
 * - Measure lines of code (LOC) and method count per service
 * - Distinguish generated vs custom services to favor automation
 *
 * ## Test Coverage
 * - Report spec files and their associations
 * - Count `describe` / `it` blocks and coverage % per module
 *
 * ## Dashboard Vision
 * - CRON job to run this script weekly and generate a report
 * - Weekly trend data with clear deltas
 * - "Hotspots" ranked by composite debt score
 * - Team/feature comparisons to focus refactoring efforts
 */

import { Project } from 'ts-morph';
import * as path from 'path';
import * as fs from 'fs';

const basePath = 'src/main/webapp/app';
const modules = [
    'admin',
    'assessment',
    'atlas',
    'buildagent',
    'communication',
    'core',
    'exam',
    'exercise',
    'fileupload',
    'iris',
    'lecture',
    'lti',
    'modeling',
    'plagiarism',
    'programming',
    'quiz',
    'shared',
    'text',
    'tutorialgroup'
];

// Initialize the project
const project = new Project({
    tsConfigFilePath: path.join(process.cwd(), 'tsconfig.json'),
});

// Add source files from each module
modules.forEach(moduleName => {
    const modulePath = path.join(process.cwd(), basePath, moduleName);
    if (fs.existsSync(modulePath)) {
        project.addSourceFilesAtPaths(path.join(modulePath, '**/*.ts'));
    }
});

// Define interface for module statistics
interface ModuleStats {
    components: number;
    directives: number;
    pipes: number;
    injectables: number;
    total: number;
}

// Define interface for change detection statistics
interface ChangeDetectionStats {
    onPush: number;
    default: number;
    implicit: number; // No explicit strategy specified (also Default)
    total: number;
}

// Analyze components, directives, and pipes by module
function analyzeModules(): Record<string, ModuleStats> {
    const stats: Record<string, ModuleStats> = {};

    // Initialize stats for each module
    modules.forEach(module => {
        stats[module] = { components: 0, directives: 0, pipes: 0, injectables: 0, total: 0 };
    });

    // Process each source file
    const sourceFiles = project.getSourceFiles();
    sourceFiles.forEach(file => {
        const filePath = file.getFilePath();

        // Determine which module this file belongs to
        const moduleName = modules.find(module =>
            filePath.includes(path.join(basePath, module))
        );

        if (!moduleName) return;

        // Find all classes with decorators
        const classes = file.getClasses();
        classes.forEach(cls => {
            const decorators = cls.getDecorators();

            decorators.forEach(decorator => {
                const decoratorName = decorator.getName();
                const decoratorText = decorator.getText();

                if (decoratorName === 'Component' || decoratorText.includes('@Component')) {
                    stats[moduleName].components++;
                } else if (decoratorName === 'Directive' || decoratorText.includes('@Directive')) {
                    stats[moduleName].directives++;
                } else if (decoratorName === 'Pipe' || decoratorText.includes('@Pipe')) {
                    stats[moduleName].pipes++;
                } else if (decoratorName === 'Injectable' || decoratorText.includes('@Injectable')) {
                    stats[moduleName].injectables++;
                }
            });
        });
    });

    // Calculate totals
    for (const module in stats) {
        stats[module].total =
            stats[module].components +
            stats[module].directives +
            stats[module].pipes +
            stats[module].injectables;
    }

    return stats;
}

// Analyze change detection strategies by module
function analyzeChangeDetection(): Record<string, ChangeDetectionStats> {
    const stats: Record<string, ChangeDetectionStats> = {};

    // Initialize stats for each module
    modules.forEach(module => {
        stats[module] = { onPush: 0, default: 0, implicit: 0, total: 0 };
    });

    // Process each source file
    const sourceFiles = project.getSourceFiles();
    sourceFiles.forEach(file => {
        const filePath = file.getFilePath();

        // Determine which module this file belongs to
        const moduleName = modules.find(module =>
            filePath.includes(path.join(basePath, module))
        );

        if (!moduleName) return;

        // Find all component decorators
        const classes = file.getClasses();
        classes.forEach(cls => {
            const decorators = cls.getDecorators();

            decorators.forEach(decorator => {
                if (decorator.getName() === 'Component' || decorator.getText().includes('@Component')) {
                    // Get the decorator arguments
                    const callExpr = decorator.getCallExpression();
                    if (!callExpr) return;

                    const args = callExpr.getArguments();
                    if (args.length === 0) return;

                    // Look for changeDetection property in the component decorator
                    const arg = args[0];
                    const text = arg.getText();

                    if (text.includes('ChangeDetectionStrategy.OnPush')) {
                        stats[moduleName].onPush++;
                    } else if (text.includes('ChangeDetectionStrategy.Default')) {
                        stats[moduleName].default++;
                    } else {
                        stats[moduleName].implicit++;
                    }
                }
            });
        });
    });

    // Calculate totals
    for (const module in stats) {
        stats[module].total =
            stats[module].onPush +
            stats[module].default +
            stats[module].implicit;
    }

    return stats;
}

// Generate component inventory table as markdown
function generateComponentInventoryTable(): string {
    const stats = analyzeModules();
    let tableContent = '## Angular Component Inventory\n\n';

    tableContent += '| Module | Components | Directives | Pipes | Injectables | Total |\n';
    tableContent += '|--------|------------|------------|-------|------------|-------|\n';

    let totalComponents = 0;
    let totalDirectives = 0;
    let totalPipes = 0;
    let totalInjectables = 0;
    let grandTotal = 0;

    // Sort modules by total count in descending order
    const sortedModules = [...modules].sort((a, b) => stats[b].total - stats[a].total);

    sortedModules.forEach(module => {
        const moduleStats = stats[module];
        tableContent += `| ${module} | ${moduleStats.components} | ${moduleStats.directives} | ${moduleStats.pipes} | ${moduleStats.injectables} | ${moduleStats.total} |\n`;

        totalComponents += moduleStats.components;
        totalDirectives += moduleStats.directives;
        totalPipes += moduleStats.pipes;
        totalInjectables += moduleStats.injectables;
        grandTotal += moduleStats.total;
    });

    tableContent += `| **Total** | **${totalComponents}** | **${totalDirectives}** | **${totalPipes}** | **${totalInjectables}** | **${grandTotal}** |\n`;

    return tableContent;
}

// Generate change detection strategy table as markdown
function generateChangeDetectionTable(): string {
    const stats = analyzeChangeDetection();
    let tableContent = '## Change Detection Strategy Usage\n\n';

    tableContent += '| Module | OnPush | Explicit Default | Implicit Default | Total |\n';
    tableContent += '|--------|--------|-----------------|-----------------|-------|\n';

    let totalOnPush = 0;
    let totalExplicitDefault = 0;
    let totalImplicitDefault = 0;
    let grandTotal = 0;

    // Sort modules by total count in descending order
    const sortedModules = [...modules].sort((a, b) => stats[b].total - stats[a].total);

    sortedModules.forEach(module => {
        const moduleStats = stats[module];

        // Skip modules with no components
        if (moduleStats.total === 0) return;

        tableContent += `| ${module} | ${moduleStats.onPush} | ${moduleStats.default} | ${moduleStats.implicit} | ${moduleStats.total} |\n`;

        totalOnPush += moduleStats.onPush;
        totalExplicitDefault += moduleStats.default;
        totalImplicitDefault += moduleStats.implicit;
        grandTotal += moduleStats.total;
    });

    tableContent += `| **Total** | **${totalOnPush}** | **${totalExplicitDefault}** | **${totalImplicitDefault}** | **${grandTotal}** |\n`;

    // Add percentage row for better visibility of progress
    if (grandTotal > 0) {
        const onPushPercent = (totalOnPush / grandTotal * 100).toFixed(1);
        const explicitDefaultPercent = (totalExplicitDefault / grandTotal * 100).toFixed(1);
        const implicitDefaultPercent = (totalImplicitDefault / grandTotal * 100).toFixed(1);

        tableContent += `| **Percentage** | **${onPushPercent}%** | **${explicitDefaultPercent}%** | **${implicitDefaultPercent}%** | **100%** |\n`;
    }

    return tableContent;
}

function generateReport(outputToFile = false): string {
    let reportContent = '# Angular Technical Debt Analysis Report\n\n';

    // Component inventory section
    reportContent += generateComponentInventoryTable();

    reportContent += '\n\n';

    // Change detection section
    reportContent += generateChangeDetectionTable();

    reportContent += `\n\n_Report generated on ${new Date().toLocaleString()}_\n`;

    // Output handling
    if (outputToFile) {
        const reportsDir = path.join(process.cwd(), 'reports');
        // Create reports directory if it doesn't exist
        if (!fs.existsSync(reportsDir)) {
            fs.mkdirSync(reportsDir);
        }

        const timestamp = new Date().toISOString().replace(/[:.]/g, '-');
        const reportFilePath = path.join(reportsDir, `angular-debt-report-${timestamp}.md`);
        fs.writeFileSync(reportFilePath, reportContent);
        console.log(`Report saved to: ${reportFilePath}`);
    } else {
        console.log(reportContent);
    }

    return reportContent;
}

// Execute the analysis with a more flexible entry point
function main() {
    // Parse command line arguments if needed
    const args = process.argv.slice(2);
    const outputToFile = args.includes('--file') || args.includes('-f');

    generateReport(outputToFile);
}

// Execute the analysis
main();
