import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';

type InlineConsistencyIssue = {
    filePath: string;
    type: ArtifactLocation.TypeEnum;
    startLine: number;
    endLine: number;
    description: string;
    suggestedFix: string;
    category: ConsistencyIssue.CategoryEnum;
    severity: ConsistencyIssue.SeverityEnum;
};

export function addCommentBox(editor: MonacoEditorComponent, issue: InlineConsistencyIssue) {
    const node = document.createElement('div');
    node.className = 'alert alert-warning alert-dismissible text-start fade show';
    node.innerHTML = `
      <h5 class="alert-heading">Consistency Issue Found</h5>
      <div>${htmlForMarkdown(formatConsistencyCheckResults(issue))}</div>
    `;

    // Place box beneath the line
    editor.addLineWidget(issue.endLine, `comment-${issue.startLine}-${issue.endLine}-${issue.category}`, node);
}

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
            const issueFile = isProblemStatement ? 'problem_statement.md' : loc.filePath.split('/').slice(1).join('/');

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
 * Formats consistency check issue into well-structured markdown for instructor review.
 */
export function formatConsistencyCheckResults(issue: InlineConsistencyIssue): string {
    let md = '';

    const categoryRaw = issue.category || 'GENERAL';
    const category = humanizeCategory(categoryRaw);
    md += `**[${severityToString(issue.severity)}] ${category}**\n\n`;
    md += `${issue.description}\n\n`;
    if (issue.suggestedFix) {
        md += `**Suggested fix:** ${issue.suggestedFix}\n\n`;
    }

    // Full location listing (no collapsing)
    md += `**Location:** `;
    const typeLabel = formatArtifactType(issue.type as ArtifactLocation.TypeEnum);
    const file = issue.filePath ?? '';
    let linePart = '';
    if (issue.startLine && issue.endLine) {
        linePart = issue.startLine === issue.endLine ? `:L${issue.startLine}` : `:L${issue.startLine}-${issue.endLine}`;
    } else if (issue.startLine) {
        linePart = `:L${issue.startLine}`;
    }
    md += `${typeLabel}${file ? `: ${file}` : ''}${linePart}\n`;
    md += `\n`;

    return md;
}

/**
 * Convert an ENUM_STYLE category (e.g. IDENTIFIER_NAMING_INCONSISTENCY) into Title Case (e.g. Identifier Naming Inconsistency)
 */
export function humanizeCategory(category: string): string {
    return category
        .split('_')
        .filter((p) => p.length > 0)
        .map((p) => p.charAt(0) + p.slice(1).toLowerCase())
        .join(' ');
}

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
            return type;
    }
}
