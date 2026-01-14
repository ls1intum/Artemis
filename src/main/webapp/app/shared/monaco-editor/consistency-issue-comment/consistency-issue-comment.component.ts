import { ChangeDetectionStrategy, Component, computed, effect, input, viewChild } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { InlineConsistencyIssue } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';

@Component({
    selector: 'jhi-consistency-issue-comment',
    templateUrl: './consistency-issue-comment.component.html',
    styleUrls: ['./consistency-issue-comment.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, MonacoDiffEditorComponent],
})
export class ConsistencyIssueCommentComponent {
    readonly issue = input.required<InlineConsistencyIssue>();
    readonly onApply = input<((issue: InlineConsistencyIssue) => void) | undefined>(undefined);
    readonly diffEditor = viewChild(MonacoDiffEditorComponent);

    readonly descriptionHtml = computed(() => htmlForMarkdown(this.issue().description));
    readonly suggestedFixHtml = computed(() => htmlForMarkdown(this.issue().suggestedFix ? `**Suggested fix:** ${this.issue().suggestedFix}` : ''));
    readonly lineLabel = computed(() => {
        const { startLine, endLine } = this.issue();
        if (!startLine && !endLine) {
            return '';
        }
        if (startLine && endLine) {
            return startLine === endLine ? `L${startLine}` : `L${startLine}-${endLine}`;
        }
        return `L${startLine ?? endLine}`;
    });
    readonly hasDiff = computed(() => {
        const issue = this.issue();
        return issue.originalText !== undefined && issue.modifiedText !== undefined && issue.originalText !== issue.modifiedText;
    });
    readonly canApply = computed(() => this.hasDiff() && !!this.onApply());

    constructor() {
        effect(() => {
            if (!this.hasDiff()) {
                return;
            }
            const diffEditor = this.diffEditor();
            if (!diffEditor) {
                return;
            }
            const issue = this.issue();
            diffEditor.setFileContents(issue.originalText ?? '', issue.modifiedText ?? '', issue.filePath, issue.filePath);
        });
    }

    applySuggestedChange(): void {
        this.onApply()?.(this.issue());
    }
}
