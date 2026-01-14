import { ChangeDetectionStrategy, Component, computed, effect, input, signal, viewChild } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MonacoDiffEditorComponent } from 'app/shared/monaco-editor/diff-editor/monaco-diff-editor.component';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { ApplySuggestedChangeResult, InlineConsistencyIssue } from 'app/shared/monaco-editor/model/actions/artemis-intelligence/consistency-check';

@Component({
    selector: 'jhi-consistency-issue-comment',
    templateUrl: './consistency-issue-comment.component.html',
    styleUrls: ['./consistency-issue-comment.component.scss'],
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [TranslateDirective, ArtemisTranslatePipe, MonacoDiffEditorComponent],
})
export class ConsistencyIssueCommentComponent {
    readonly diffEditor = viewChild(MonacoDiffEditorComponent);

    readonly issue = input.required<InlineConsistencyIssue>();
    readonly onApply = input.required<(issue: InlineConsistencyIssue) => ApplySuggestedChangeResult>();
    readonly showDetails = signal(true);
    readonly applyStatus = signal<boolean | undefined>(undefined);

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
    readonly canApply = computed(() => this.hasDiff());

    constructor() {
        effect(() => {
            this.issue();
            this.applyStatus.set(undefined);
        });
        effect(() => {
            if (!this.hasDiff() || !this.showDetails()) {
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
        const result = this.onApply()(this.issue());
        this.applyStatus.set(result.ok);
    }

    toggleDetails(): void {
        this.showDetails.update((current) => !current);
    }
}
