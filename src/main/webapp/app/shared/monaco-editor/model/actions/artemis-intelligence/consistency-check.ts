import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

export type InlineConsistencyIssue = {
    filePath: string;
    type: ArtifactLocation.TypeEnum;
    startLine: number;
    endLine: number;
    description: string;
    suggestedFix: string;
    category: ConsistencyIssue.CategoryEnum;
    severity: ConsistencyIssue.SeverityEnum;
};

/**
 * Adds a comment boxes below code lines in Monaco based on the issues.
 * @param editor       Monaco editor wrapper component.
 * @param issues       Issues to render.
 * @param selectedFile The currently selected file in the repo.
 * @param selectedRepo The currently selected repository.
 */
export function addCommentBoxes(
    editor: MonacoEditorComponent,
    issues: ConsistencyIssue[],
    selectedFile: string | undefined,
    selectedRepo: RepositoryType | 'PROBLEM_STATEMENT' | undefined,
) {
    for (const [index, issue] of issuesForSelectedFile(selectedFile, selectedRepo, issues).entries()) {
        addCommentBox(editor, issue, index);
    }
}

/**
 * Adds a comment box below a code line in Monaco.
 * @param editor Monaco editor wrapper component.
 * @param issue  Issue to render.
 * @param id     The unique identifier for this comment.
 */
export function addCommentBox(editor: MonacoEditorComponent, issue: InlineConsistencyIssue, id: number) {
    const node = document.createElement('div');
    node.className = 'alert alert-warning alert-dismissible text-start fade show';
    node.innerHTML = `
      <h5 class='alert-heading'>Consistency Issue Found</h5>
      <div>${htmlForMarkdown(formatConsistencyCheckResults(issue))}</div>
    `;

    editor.addLineWidget(issue.endLine, `comment-${id}`, node);
}

/**
 * Filters raw issues to those matching the currently selected file/repo and
 * maps them into inline issues consumable by the editor UI.
 * @param selectedFile  File path (relative to repo root) currently open.
 * @param selectedRepo  Active repository scope or 'PROBLEM_STATEMENT'.
 * @param issues        All consistency issues.
 * @returns Inline issues for the selected file/repo.
 */
export function issuesForSelectedFile(
    selectedFile: string | undefined,
    selectedRepo: RepositoryType | 'PROBLEM_STATEMENT' | undefined,
    issues: ConsistencyIssue[],
): InlineConsistencyIssue[] {
    if (!selectedFile || !selectedRepo) {
        return [];
    }

    const inlineIssues: InlineConsistencyIssue[] = [];

    for (const issue of issues) {
        for (const loc of issue.relatedLocations) {
            if (!isMatchingRepository(loc.type, selectedRepo!)) {
                continue;
            }

            // Problem statement filePath is either problem_statement.md or empty
            const isProblemStatement = loc.filePath === 'problem_statement.md' || loc.filePath === '';
            // Remove the first part of e.g. template_repository/src/TEST/BubbleSort.java
            // Remove only known repo prefixes (template_repository/..., solution_repository/..., tests_repository/...)
            const issueFile = (() => {
                if (isProblemStatement) {
                    return 'problem_statement.md';
                }
                const parts = (loc.filePath ?? '').split('/');
                const knownPrefixes = ['template_repository', 'solution_repository', 'tests_repository'];
                return knownPrefixes.includes(parts[0]) ? parts.slice(1).join('/') : loc.filePath;
            })();

            if (issueFile !== selectedFile) {
                continue;
            }

            inlineIssues.push({
                filePath: loc.filePath,
                type: loc.type,
                startLine: loc.startLine,
                endLine: loc.endLine,
                description: issue.description,
                suggestedFix: issue.suggestedFix,
                category: issue.category,
                severity: issue.severity,
            });
        }
    }

    return inlineIssues;
}

/**
 * Checks whether a location's artifact type matches the selected repository scope.
 * @param repo1 Artifact location type (from issue).
 * @param repo2 Selected repository scope.
 * @returns True if both represent the same repo domain.
 */
export function isMatchingRepository(repo1: ArtifactLocation.TypeEnum, repo2: RepositoryType | 'PROBLEM_STATEMENT') {
    if (repo1 === ArtifactLocation.TypeEnum.TemplateRepository && repo2 === RepositoryType.TEMPLATE) {
        return true;
    } else if (repo1 === ArtifactLocation.TypeEnum.SolutionRepository && repo2 === RepositoryType.SOLUTION) {
        return true;
    } else if (repo1 === ArtifactLocation.TypeEnum.TestsRepository && repo2 === RepositoryType.TESTS) {
        return true;
    } else if (repo1 === ArtifactLocation.TypeEnum.ProblemStatement && repo2 === 'PROBLEM_STATEMENT') {
        return true;
    }

    return false;
}

/**
 * Formats a single inline issue as Markdown (title, description, fix, and location).
 * @param issue Inline issue to format.
 * @returns Markdown string.
 */
export function formatConsistencyCheckResults(issue: InlineConsistencyIssue): string {
    let md = '';

    let linePart = '';
    if (issue.startLine && issue.endLine) {
        linePart = issue.startLine === issue.endLine ? `(L${issue.startLine})` : `(L${issue.startLine}-${issue.endLine})`;
    } else if (issue.startLine) {
        linePart = `(L${issue.startLine})`;
    }

    const categoryRaw = issue.category || 'GENERAL';
    const category = humanizeCategory(categoryRaw);
    md += `**[${severityToString(issue.severity)}] ${category} ${linePart}**\n\n`;
    md += `${issue.description}\n\n`;
    if (issue.suggestedFix) {
        md += `**Suggested fix:** ${issue.suggestedFix}\n\n`;
    }

    md += `\n`;

    return md;
}

/**
 * Converts ENUM_STYLE text (e.g., IDENTIFIER_NAMING_INCONSISTENCY) to Title Case.
 * @param category Enum-style category string.
 * @returns Human-friendly title string.
 */
export function humanizeCategory(category: string): string {
    return category
        .split('_')
        .filter((p) => p.length > 0)
        .map((p) => p.charAt(0) + p.slice(1).toLowerCase())
        .join(' ');
}

/**
 * Maps severity enum to a display string.
 * @param severity Severity enum.
 * @returns Display label for severity.
 */
export function severityToString(severity: ConsistencyIssue.SeverityEnum) {
    switch (severity) {
        case ConsistencyIssue.SeverityEnum.High:
            return 'HIGH';
        case ConsistencyIssue.SeverityEnum.Medium:
            return 'MEDIUM';
        case ConsistencyIssue.SeverityEnum.Low:
            return 'LOW';
        default:
            return 'UNKNOWN';
    }
}

/**
 * Maps artifact type enum to a human-readable label.
 * @param type Artifact location type.
 * @returns Display label for artifact domain.
 */
export function formatArtifactType(type: ArtifactLocation.TypeEnum): string {
    switch (type) {
        case ArtifactLocation.TypeEnum.ProblemStatement:
            return 'Problem Statement';
        case ArtifactLocation.TypeEnum.TemplateRepository:
            return 'Template';
        case ArtifactLocation.TypeEnum.SolutionRepository:
            return 'Solution';
        case ArtifactLocation.TypeEnum.TestsRepository:
            return 'Tests';
        default:
            return 'Other';
    }
}
