import { ConsistencyCheckResponse } from 'app/openapi/model/consistencyCheckResponse';
import { ArtemisIntelligenceService } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/artemis-intelligence.service';
import { ConsistencyIssue } from 'app/openapi/model/consistencyIssue';
import { WritableSignal } from '@angular/core';
import { MonacoEditorComponent } from 'app/shared/monaco-editor/monaco-editor.component';

type InlineConsistencyIssue = { line: number; text: string };

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

    static addCommentBox(editor: MonacoEditorComponent, lineNumber: number, text: string) {
        const line = lineNumber - 1;

        const node = document.createElement('div');
        node.className = 'my-comment-widget';
        node.innerText = text;

        // Place box beneath the line
        editor.addLineWidget(line, `comment-${line}`, node);
    }

    static issuesForSelectedFile(selectedFile: string | undefined, issues: ConsistencyIssue[]): InlineConsistencyIssue[] {
        if (!selectedFile) {
            return [];
        }

        const result = [];

        for (const issue of issues) {
            for (const loc of issue.relatedLocations) {
                // We want to remove the first part of e.g. template_repository/src/TEST/BubbleSort.java
                // The same information is stored in loc.type
                const repoPath = loc.filePath.split('/').slice(1).join('/');

                if (repoPath === selectedFile || (selectedFile === 'problem_statement.md' && repoPath === '')) {
                    result.push({ line: loc.endLine, text: issue.description });
                }
            }
        }

        return result;
    }
}
