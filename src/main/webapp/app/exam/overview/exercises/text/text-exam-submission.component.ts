import { Component, OnInit, inject, input, output } from '@angular/core';
import { TextEditorService } from 'app/text/overview/text-editor.service';
import { Subject } from 'rxjs';
import { TextSubmission } from 'app/entities/text/text-submission.model';
import { StringCountService } from 'app/text/overview/string-count.service';
import { Exercise, ExerciseType, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { faListAlt } from '@fortawesome/free-solid-svg-icons';
import { MAX_SUBMISSION_TEXT_LENGTH } from 'app/shared/constants/input.constants';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge.component';
import { ExerciseSaveButtonComponent } from '../exercise-save-button/exercise-save-button.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExamExerciseUpdateHighlighterComponent } from '../exam-exercise-update-highlighter/exam-exercise-update-highlighter.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { onTextEditorTab } from 'app/shared/util/text.utils';

@Component({
    selector: 'jhi-text-editor-exam',
    templateUrl: './text-exam-submission.component.html',
    providers: [{ provide: ExamSubmissionComponent, useExisting: TextExamSubmissionComponent }],
    styleUrls: ['./text-exam-submission.component.scss'],
    imports: [
        TranslateDirective,
        IncludedInScoreBadgeComponent,
        ExerciseSaveButtonComponent,
        ResizeableContainerComponent,
        FormsModule,
        FaIconComponent,
        ExamExerciseUpdateHighlighterComponent,
        ArtemisTranslatePipe,
    ],
})
export class TextExamSubmissionComponent extends ExamSubmissionComponent implements OnInit {
    private textService = inject(TextEditorService);
    private stringCountService = inject(StringCountService);

    exerciseType = ExerciseType.TEXT;

    // IMPORTANT: this reference must be contained in this.studentParticipation.submissions[0] otherwise the parent component will not be able to react to changes
    studentSubmission = input.required<TextSubmission>();
    exercise = input.required<Exercise>();

    saveCurrentExercise = output<void>();

    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly maxCharacterCount = MAX_SUBMISSION_TEXT_LENGTH;

    // answer represents the view state
    answer: string;
    problemStatementHtml: string;
    private textEditorInput = new Subject<string>();

    // Icons
    protected readonly faListAlt = faListAlt;

    // used in the html template
    protected readonly onTextEditorTab = onTextEditorTab;

    ngOnInit(): void {
        // show submission answers in UI
        this.problemStatementHtml = htmlForMarkdown(this.exercise()?.problemStatement);
        this.updateViewFromSubmission();
    }

    getExerciseId(): number | undefined {
        return this.exercise().id;
    }

    getExercise(): Exercise {
        return this.exercise();
    }

    updateProblemStatement(newProblemStatementHtml: string): void {
        this.problemStatementHtml = newProblemStatementHtml;
        this.changeDetectorReference.detectChanges();
    }

    getSubmission(): Submission {
        return this.studentSubmission();
    }

    updateViewFromSubmission(): void {
        this.answer = this.studentSubmission().text ?? '';
    }

    public hasUnsavedChanges(): boolean {
        return !this.studentSubmission().isSynced!;
    }

    public updateSubmissionFromView(): void {
        this.studentSubmission().text = this.answer;
        this.studentSubmission().language = this.textService.predictLanguage(this.answer);
    }

    get wordCount(): number {
        return this.stringCountService.countWords(this.answer);
    }

    get characterCount(): number {
        return this.stringCountService.countCharacters(this.answer);
    }

    onTextEditorInput(event: Event) {
        this.studentSubmission().isSynced = false;
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
