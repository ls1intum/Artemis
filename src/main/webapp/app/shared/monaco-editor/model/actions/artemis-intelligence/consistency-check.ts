import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { WritableSignal } from '@angular/core';
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

export class ConsistencyCheck {
    run(artemisIntelligenceService: ArtemisIntelligenceService, exerciseId: number, resultSignals: WritableSignal<ConsistencyIssue[]>[]) {
        artemisIntelligenceService.consistencyCheck(exerciseId).subscribe({
            next: (response: ConsistencyCheckResponse) => {
                for (const signal of resultSignals) {
                    signal.set(response.issues ?? []);
                }
            },
        });
    }

    static addCommentBox(editor: MonacoEditorComponent, issue: InlineConsistencyIssue) {
        const node = document.createElement('div');
        node.className = 'alert alert-warning alert-dismissible text-start fade show';
        node.innerHTML = `
          <h5 class="alert-heading">Consistency Issue Found</h5>
          <div>${htmlForMarkdown(this.formatConsistencyCheckResults(issue))}</div>
        `;

        // Place box beneath the line
        editor.addLineWidget(issue.endLine, `comment-${issue.endLine}`, node);
    }

    static test(editor: MonacoEditorComponent, line: number, i: number) {
        const node = document.createElement('div');
        node.className = 'alert alert-warning alert-dismissible text-start fade show';
        node.innerHTML = `
          <h5 class="alert-heading">Consistency Issue Found</h5>
          <div>Number: ${i}</div>
        `;

        // Place box beneath the line
        editor.addLineWidget(line, `test-comment`, node);
    }

    static issuesForSelectedFile(
        selectedFile: string | undefined,
        selectedRepo: RepositoryType | 'PROBLEM_STATEMENT' | undefined,
        issues: ConsistencyIssue[],
    ): InlineConsistencyIssue[] {
        if (!selectedFile || !selectedRepo) {
            return [];
        }

        const result = [];

        for (const issue of issues) {
            for (const loc of issue.relatedLocations) {
                if (!this.isMatchingRepository(loc.type, selectedRepo!)) {
                    continue;
                }

                // Problem statement filePath is either problem_statement.md or empty
                const isProblemStatement = loc.filePath === 'problem_statement.md' || loc.filePath === '';
                // Remove the first part of e.g. template_repository/src/TEST/BubbleSort.java
                const repoPath = isProblemStatement ? 'problem_statement.md' : loc.filePath.split('/').slice(1).join('/');

                if (repoPath === selectedFile) {
                    result.push({
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
        }

        return result;
    }

    static isMatchingRepository(repo1: ArtifactLocation.TypeEnum, repo2: RepositoryType | 'PROBLEM_STATEMENT') {
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
    static formatConsistencyCheckResults(issue: InlineConsistencyIssue): string {
        let md = '';

        const categoryRaw = issue.category || 'GENERAL';
        const category = this.humanizeCategory(categoryRaw);
        md += `**[${ConsistencyCheck.severityToString(issue.severity)}] ${category}**\n\n`;
        md += `${issue.description}\n\n`;
        if (issue.suggestedFix) {
            md += `**Suggested fix:** ${issue.suggestedFix}\n\n`;
        }

        // Full location listing (no collapsing)
        md += `**Location:** `;
        const typeLabel = this.formatArtifactType(issue.type as ArtifactLocation.TypeEnum);
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
    static humanizeCategory(category: string): string {
        return category
            .split('_')
            .filter((p) => p.length > 0)
            .map((p) => p.charAt(0) + p.slice(1).toLowerCase())
            .join(' ');
    }

    static severityToString(severity: ConsistencyIssue.SeverityEnum) {
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

    static formatArtifactType(type: ArtifactLocation.TypeEnum): string {
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
}
