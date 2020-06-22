import { Component, OnInit, Input, OnChanges, SimpleChanges } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { Subject } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { Exercise } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: TextExamSubmissionComponent }],
    styleUrls: ['./text-exam-submission.component.scss'],
})
export class TextExamSubmissionComponent extends ExamSubmissionComponent implements OnChanges {
    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: TextSubmission;
    @Input()
    exercise: Exercise;

    // answer represents the view state
    answer: string;
    private textEditorInput = new Subject<string>();

    constructor(
        private textService: TextEditorService,
        private jhiAlertService: AlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private stringCountService: StringCountService,
    ) {
        super();
    }

    ngOnChanges(changes: SimpleChanges) {
        if (changes.studentSubmission.currentValue !== changes.studentSubmission.previousValue) {
            // show submission answers in UI
            this.updateViewFromSubmission();
        }
    }

    updateViewFromSubmission(): void {
        if (this.studentSubmission.text) {
            this.answer = this.studentSubmission.text;
        } else {
            this.answer = '';
        }
    }

    public hasUnsavedChanges(): boolean {
        return this.studentSubmission.text !== this.answer;
    }

    public updateSubmissionFromView(): void {
        this.studentSubmission.text = this.answer;
        this.studentSubmission.language = this.textService.predictLanguage(this.answer);
        this.studentSubmission.isSynced = false;
    }

    get wordCount(): number {
        return this.stringCountService.countWords(this.answer);
    }

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.answer);
    }

    onTextEditorTab(editor: HTMLTextAreaElement, event: KeyboardEvent) {
        event.preventDefault();
        const value = editor.value;
        const start = editor.selectionStart;
        const end = editor.selectionEnd;

        editor.value = value.substring(0, start) + '\t' + value.substring(end);
        editor.selectionStart = editor.selectionEnd = start + 1;
    }

    onTextEditorInput(event: Event) {
        this.textEditorInput.next((<HTMLTextAreaElement>event.target).value);
    }
}
