import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from 'app/core/util/alert.service';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { Subject } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: TextExamSubmissionComponent }],
    styleUrls: ['./text-exam-submission.component.scss'],
})
export class TextExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: TextSubmission;
    @Input()
    exercise: Exercise;

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;

    // answer represents the view state
    answer: string;
    private textEditorInput = new Subject<string>();

    // Icons
    farListAlt = faListAlt;

    constructor(
        private textService: TextEditorService,
        private alertService: AlertService,
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

    updateProblemStatement(newProblemStatement: string): void {
        this.exercise.problemStatement = newProblemStatement;
        this.changeDetectorReference.detectChanges();
    }

    getSubmission(): Submission {
        return this.studentSubmission;
    }

    updateViewFromSubmission(): void {
        if (this.studentSubmission.text) {
            this.answer = this.studentSubmission.text;
        } else {
            this.answer = '';
        }
    }

    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission.isSynced!;
    }

    public updateSubmissionFromView(): void {
        this.studentSubmission.text = this.answer;
        this.studentSubmission.language = this.textService.predictLanguage(this.answer);
    }

    get wordCount(): number {
        return this.stringCountService.countWords(this.answer);
    }

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.answer);
    }

    onTextEditorTab(editor: HTMLTextAreaElement, event: Event) {
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
