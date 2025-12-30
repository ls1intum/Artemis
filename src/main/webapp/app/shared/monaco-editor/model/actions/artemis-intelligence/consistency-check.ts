import type { ArtifactLocationTypeEnum } from 'app/openapi/models/artifact-location';
import type { ConsistencyIssue, ConsistencyIssueCategoryEnum, ConsistencyIssueSeverityEnum } from 'app/openapi/models/consistency-issue';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TranslateService } from '@ngx-translate/core';

export type InlineConsistencyIssue = {
    filePath: string;
    type: ArtifactLocationTypeEnum;
    startLine: number;
    endLine: number;
    description: string;
    suggestedFix: string;
    category: ConsistencyIssueCategoryEnum;
    severity: ConsistencyIssueSeverityEnum;
};

/**
 * Adds a comment boxes below code lines in Monaco based on the issues.
 * @param editor            Monaco editor wrapper component.
 * @param issues            Issues to render.
 * @param selectedFile      The currently selected file in the repo.
 * @param selectedRepo      The currently selected repository.
 * @param translateService  Service to translate text in the comments.
 */
export function addCommentBoxes(
    editor: MonacoEditorComponent,
    issues: ConsistencyIssue[],
    selectedFile: string | undefined,
    selectedRepo: RepositoryType | 'PROBLEM_STATEMENT' | undefined,
    translateService: TranslateService,
) {
    for (const [index, issue] of issuesForSelectedFile(selectedFile, selectedRepo, issues).entries()) {
        addCommentBox(editor, issue, index, translateService);
    }
}

/**
 * Adds a comment box below a code line in Monaco.
 * @param editor Monaco editor wrapper component.
 * @param issue  Issue to render.
 * @param id     The unique identifier for this comment.
 * @param translateService  Service to translate text in the comments.
 */
export function addCommentBox(editor: MonacoEditorComponent, issue: InlineConsistencyIssue, id: number, translateService: TranslateService) {
    const headingText = translateService.instant('artemisApp.consistencyCheck.issueHeading');
    const node = document.createElement('div');
    node.className = 'alert alert-warning alert-dismissible text-start fade show';
    node.innerHTML = `
      <h5 class='alert-heading'>${headingText}</h5>
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
export function isMatchingRepository(repo1: ArtifactLocationTypeEnum, repo2: RepositoryType | 'PROBLEM_STATEMENT') {
    if (repo1 === 'TEMPLATE_REPOSITORY' && repo2 === RepositoryType.TEMPLATE) {
        return true;
    } else if (repo1 === 'SOLUTION_REPOSITORY' && repo2 === RepositoryType.SOLUTION) {
        return true;
    } else if (repo1 === 'TESTS_REPOSITORY' && repo2 === RepositoryType.TESTS) {
        return true;
    } else if (repo1 === 'PROBLEM_STATEMENT' && repo2 === 'PROBLEM_STATEMENT') {
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
        // Does not need to be localized, as the LLM output is english. Only and it
        // is planed to store the content on the server in the future.
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
    // Does not need to be localized, as the LLM output is english. Only and it
    // is planed to store the content on the server in the future.
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
export function severityToString(severity: ConsistencyIssueSeverityEnum) {
    // Does not need to be localized, as the LLM output is english. Only and it
    // is planed to store the content on the server in the future.
    switch (severity) {
        case 'HIGH':
            return 'HIGH';
        case 'MEDIUM':
            return 'MEDIUM';
        case 'LOW':
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
export function formatArtifactType(type: ArtifactLocationTypeEnum): string {
    // Does not need to be localized, as the LLM output is english. Only and it
    // is planed to store the content on the server in the future.
    switch (type) {
        case 'PROBLEM_STATEMENT':
            return 'Problem Statement';
        case 'TEMPLATE_REPOSITORY':
            return 'Template';
        case 'SOLUTION_REPOSITORY':
            return 'Solution';
        case 'TESTS_REPOSITORY':
            return 'Tests';
        default:
            return 'Other';
    }
}
