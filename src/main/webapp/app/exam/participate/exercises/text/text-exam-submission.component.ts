import { Component, OnInit, Input } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/alert/alert.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { Subject } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { Exercise } from 'app/entities/exercise.model';

export abstract class ExamSubmissionComponent {
    abstract hasUnsavedChanges(): boolean;
    abstract updateSubmissionFromView(): void;
}

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-exam-submission.component.html',
    styleUrls: ['./text-exam-submission.component.scss'],
})
export class TextExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    @Input()
    studentParticipation: StudentParticipation;
    @Input()
    exercise: Exercise;

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    submission: TextSubmission;

    // answer represents the view state
    answer: string;
    private textEditorInput = new Subject<string>();

    isSaving: boolean;

    constructor(
        private textService: TextEditorService,
        private jhiAlertService: AlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private stringCountService: StringCountService,
    ) {
        super();
        this.isSaving = false;
    }

    ngOnInit() {
        if (this.studentParticipation.submissions && this.studentParticipation.submissions.length === 1) {
            this.submission = this.studentParticipation.submissions[0] as TextSubmission;

            // show submission answers in UI
            this.updateViewFromSubmission();
        }
    }

    updateViewFromSubmission(): void {
        if (this.submission.text) {
            this.answer = this.submission.text;
        }
    }

    public hasUnsavedChanges(): boolean {
        return this.submission.text !== this.answer;
    }

    public updateSubmissionFromView(): void {
        this.submission.text = this.answer;
        this.submission.language = this.textService.predictLanguage(this.answer);
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
