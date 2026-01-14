import { ApplicationRef, EnvironmentInjector, createComponent } from '@angular/core';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';
import { ArtifactLocation } from 'app/openapi/model/artifactLocation';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { RepositoryType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { TranslateService } from '@ngx-translate/core';
import { ConsistencyIssueCommentComponent } from 'app/shared/monaco-editor/consistency-issue-comment/consistency-issue-comment.component';
import * as monaco from 'monaco-editor';

export type InlineConsistencyIssue = {
    filePath: string;
    type: ArtifactLocation.TypeEnum;
    startLine: number;
    endLine: number;
    description: string;
    suggestedFix: string;
    category: ConsistencyIssue.CategoryEnum;
    severity: ConsistencyIssue.SeverityEnum;
    originalText?: string;
    modifiedText?: string;
};

/**
 * Adds a comment boxes below code lines in Monaco based on the issues.
 * @param editor            Monaco editor wrapper component.
 * @param issues            Issues to render.
 * @param selectedFile      The currently selected file in the repo.
 * @param selectedRepo      The currently selected repository.
 * @param translateService  Service to translate text in the comments.
 */
/**
 * Renders consistency issue comments for the currently selected file/repo.
 *
 * @param editor Monaco editor instance.
 * @param issues Raw consistency issues.
 * @param selectedFile Currently selected file path.
 * @param selectedRepo Currently selected repository scope.
 * @param translateService Translate service instance.
 * @param onApply Callback invoked when applying a suggestion.
 * @param appRef ApplicationRef for dynamic component creation.
 * @param environmentInjector Injector for dynamic component creation.
 */
export function addCommentBoxes(
    editor: MonacoEditorComponent,
    issues: ConsistencyIssue[],
    selectedFile: string | undefined,
    selectedRepo: RepositoryType | 'PROBLEM_STATEMENT' | undefined,
    translateService: TranslateService,
    onApply: (issue: InlineConsistencyIssue) => boolean,
    appRef?: ApplicationRef,
    environmentInjector?: EnvironmentInjector,
) {
    for (const [index, issue] of issuesForSelectedFile(selectedFile, selectedRepo, issues).entries()) {
        const resolvedIssue = withDerivedOriginalText(editor, issue);
        addCommentBox(editor, resolvedIssue, index, translateService, onApply, appRef, environmentInjector);
    }
}

/**
 * Adds a comment box below a code line in Monaco.
 * @param editor Monaco editor wrapper component.
 * @param issue  Issue to render.
 * @param id     The unique identifier for this comment.
 * @param translateService  Service to translate text in the comments.
 */
/**
 * Creates a single consistency issue comment widget at the given line.
 *
 * @param editor Monaco editor instance.
 * @param issue Inline issue to render.
 * @param id Unique widget id.
 * @param translateService Translate service instance.
 * @param onApply Callback invoked when applying a suggestion.
 * @param appRef ApplicationRef for dynamic component creation.
 * @param environmentInjector Injector for dynamic component creation.
 * @returns void
 */
export function addCommentBox(
    editor: MonacoEditorComponent,
    issue: InlineConsistencyIssue,
    id: number,
    translateService: TranslateService,
    onApply: (issue: InlineConsistencyIssue) => boolean,
    appRef?: ApplicationRef,
    environmentInjector?: EnvironmentInjector,
) {
    if (!appRef || !environmentInjector) {
        const headingText = translateService.instant('artemisApp.hyperion.consistencyCheck.issueHeading');
        const node = document.createElement('div');
        node.className = 'alert alert-warning alert-dismissible text-start fade show';
        node.innerHTML = `
      <h5 class='alert-heading'>${headingText}</h5>
      <div>${htmlForMarkdown(formatConsistencyCheckResults(issue))}</div>
    `;
        editor.addLineWidget(issue.endLine, `comment-${id}`, node);
        return;
    }

    const node = document.createElement('div');
    const componentRef = createComponent(ConsistencyIssueCommentComponent, {
        hostElement: node,
        environmentInjector,
    });
    componentRef.setInput('issue', issue);
    componentRef.setInput('onApply', onApply);
    appRef.attachView(componentRef.hostView);
    componentRef.changeDetectorRef.detectChanges();

    editor.addLineWidget(issue.endLine, `comment-${id}`, node, () => {
        appRef.detachView(componentRef.hostView);
        componentRef.destroy();
    });
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

            const issueFile = getRepoPath(loc);

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
                modifiedText: loc.modifiedText,
            });
        }
    }

    return inlineIssues;
}

/**
 * Normalizes a repository-relative file path from an artifact location.
 *
 * If the location refers to the problem statement, the fixed filename
 * `problem_statement.md` is returned. Otherwise, known repository prefixes
 * (`template_repository`, `solution_repository`, `tests_repository`)
 * are removed, returning only the internal path.
 *
 * @param {ArtifactLocation} loc
 *        The artifact location containing the raw repository file path.
 *
 * @returns {string}
 *          The normalized file path inside the repository.
 */
export function getRepoPath(loc: ArtifactLocation): string {
    // Problem statement filePath is either problem_statement.md or empty
    const isProblemStatement = loc.filePath === 'problem_statement.md' || loc.filePath === '';
    // Remove the first part of e.g. template_repository/src/TEST/BubbleSort.java
    // Remove only known repo prefixes (template_repository/..., solution_repository/..., tests_repository/...)
    if (isProblemStatement) {
        return 'problem_statement.md';
    }
    const parts = (loc.filePath ?? '').split('/');
    const knownPrefixes = ['template_repository', 'solution_repository', 'tests_repository'];
    return knownPrefixes.includes(parts[0]) ? parts.slice(1).join('/') : loc.filePath;
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
        // Does not need to be localized, as the LLM output is english. Only and it
        // is planed to store the content on the server in the future.
        md += `**Suggested fix:** ${issue.suggestedFix}\n\n`;
    }

    if (issue.originalText !== undefined && issue.modifiedText !== undefined) {
        md += `**Suggested change:**\n\n`;
        if (issue.originalText) {
            md += `**Original:**\n\n\`\`\`\n${issue.originalText}\n\`\`\`\n\n`;
        }
        if (issue.modifiedText) {
            md += `**Modified:**\n\n\`\`\`\n${issue.modifiedText}\n\`\`\`\n\n`;
        }
    }

    md += `\n`;

    return md;
}

/**
 * Derives the current text for the referenced line range and attaches it as originalText.
 *
 * @param editor Monaco editor instance.
 * @param issue Inline issue to enrich.
 * @returns Inline issue with derived original text, if available.
 */
function withDerivedOriginalText(editor: MonacoEditorComponent, issue: InlineConsistencyIssue): InlineConsistencyIssue {
    if (issue.originalText !== undefined || issue.modifiedText === undefined) {
        return issue;
    }
    const model = editor.getModel();
    if (!model || !issue.startLine || !issue.endLine) {
        return issue;
    }
    const endLineContent = model.getLineContent(issue.endLine);
    const range = {
        startLineNumber: issue.startLine,
        startColumn: 1,
        endLineNumber: issue.endLine,
        endColumn: endLineContent.length + 1,
    };
    const originalText = model.getValueInRange(range);
    return { ...issue, originalText };
}

/**
 * Applies a suggested change to a Monaco model.
 *
 * @param model Monaco text model.
 * @param issue Inline issue containing the suggested change.
 * @returns True if the change was applied, otherwise false.
 */
export function applySuggestedChangeToModel(model: monaco.editor.ITextModel, issue: InlineConsistencyIssue): boolean {
    if (issue.originalText === undefined || issue.modifiedText === undefined) {
        return false;
    }

    const startLine = issue.startLine;
    const endLine = issue.endLine;
    const endLineContent = endLine ? model.getLineContent(endLine) : '';
    const range =
        startLine && endLine
            ? {
                  startLineNumber: startLine,
                  startColumn: 1,
                  endLineNumber: endLine,
                  endColumn: endLineContent.length + 1,
              }
            : undefined;

    if (range) {
        const currentText = model.getValueInRange(range);
        if (currentText === issue.originalText) {
            model.pushEditOperations([], [{ range, text: issue.modifiedText }], () => null);
            return true;
        }
    }

    const matches = model.findMatches(issue.originalText, false, false, false, null, true);
    if (matches.length !== 1) {
        return false;
    }

    model.pushEditOperations([], [{ range: matches[0].range, text: issue.modifiedText }], () => null);
    return true;
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
export function severityToString(severity: ConsistencyIssue.SeverityEnum) {
    // Does not need to be localized, as the LLM output is english. Only and it
    // is planed to store the content on the server in the future.
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
    // Does not need to be localized, as the LLM output is english. Only and it
    // is planed to store the content on the server in the future.
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
