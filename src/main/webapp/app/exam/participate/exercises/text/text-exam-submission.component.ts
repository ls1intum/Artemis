import { ChangeDetectorRef, Component, Input, OnInit, output } from '@angular/core';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { Subject } from 'rxjs';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { ExamParticipationService } from '../../exam-participation.service';

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: TextExamSubmissionComponent }],
    styleUrls: ['./text-exam-submission.component.scss'],
})
export class TextExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    exerciseType = ExerciseType.TEXT;

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    @Input()
    studentSubmission: TextSubmission;
    @Input()
    exercise: Exercise;

    saveCurrentExercise = output<void>();

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;

    // answer represents the view state
    answer: string;
    problemStatementHtml: string;
    private textEditorInput = new Subject<string>();
    submission?: Submission;

    // Icons
    readonly farListAlt = faListAlt;

    constructor(
        private textService: TextEditorService,
        private stringCountService: StringCountService,
        private examParticipationService: ExamParticipationService,
        changeDetectorReference: ChangeDetectorRef,
    ) {
        super(changeDetectorReference);
    }

    ngOnInit(): void {
        // show submission answers in UI
        this.problemStatementHtml = htmlForMarkdown(this.exercise?.problemStatement);
        this.updateViewFromSubmission();
        this.submission = ExamParticipationService.getSubmissionForExercise(this.exercise);
    }

    getExerciseId(): number | undefined {
        return this.exercise.id;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    updateProblemStatement(newProblemStatementHtml: string): void {
        this.problemStatementHtml = newProblemStatementHtml;
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

    private updateViewFromSubmissionVersion() {
        if (this.submissionVersion?.content) {
            this.answer = this.submissionVersion.content;
        } else {
            // the content of the submission version can be undefined if an empty submission was saved
            this.answer = '';
        }
    }

    setSubmissionVersion(submissionVersion: SubmissionVersion): void {
        this.submissionVersion = submissionVersion;
        this.updateViewFromSubmissionVersion();
    }

    /**
     * Trigger save action in exam participation component
     */
    notifyTriggerSave() {
        this.saveCurrentExercise.emit();
    }
}
