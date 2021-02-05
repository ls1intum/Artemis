import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { Subject } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';

@Component({
    selector: 'jhi-file-upload-editor-exam',
    templateUrl: './file-upload-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: FileUploadExamSubmissionComponent }],
    styleUrls: ['./file-upload-exam-submission.component.scss'],
})
export class FileUploadExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: FileUploadSubmission;
    @Input()
    exercise: Exercise;

    readonly IncludedInOverallScore = IncludedInOverallScore;

    // answer represents the view state
    answer: string;
    private textEditorInput = new Subject<string>();

    constructor(
        private jhiAlertService: JhiAlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private stringCountService: StringCountService,
        changeDetectorReference: ChangeDetectorRef,
    ) {
        super(changeDetectorReference);
    }

    ngOnInit(): void {
        // show submission answers in UI
        this.updateViewFromSubmission();
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    getSubmission(): Submission {
        return this.studentSubmission;
    }

    updateViewFromSubmission(): void {
        if (this.studentSubmission.filePath) {
            this.answer = this.studentSubmission.filePath;
        } else {
            this.answer = '';
        }
    }

    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission.isSynced!;
    }

    public updateSubmissionFromView(): void {
        this.studentSubmission.filePath = this.answer;
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
        this.studentSubmission.isSynced = false;
        this.textEditorInput.next((<HTMLTextAreaElement>event.target).value);
    }
}
