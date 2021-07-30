import { ChangeDetectorRef, Component, Input, OnInit } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { JhiAlertService } from 'ng-jhipster';
import { TextEditorService } from 'app/exercises/text/participate/text-editor.service';
import { Subject, Subscription } from 'rxjs';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { TextSubmission } from 'app/entities/text-submission.model';
import { StringCountService } from 'app/exercises/text/participate/string-count.service';
import { Exercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { ExamSubmissionComponent } from 'app/exam/participate/exercises/exam-submission.component';
import { Submission } from 'app/entities/submission.model';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

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

    // answer represents the view state
    answer: string;
    private textEditorInput = new Subject<string>();

    subscriptionToLiveExamExerciseUpdates: Subscription;
    previousProblemStatementUpdate: string;
    updatedProblemStatementWithHighlightedDifferences: string;
    updatedProblemStatement: string;
    showHighlightedDifferences = true;

    constructor(
        private textService: TextEditorService,
        private jhiAlertService: JhiAlertService,
        private artemisMarkdown: ArtemisMarkdownService,
        private translateService: TranslateService,
        private stringCountService: StringCountService,
        private examExerciseUpdateService: ExamExerciseUpdateService,
        changeDetectorReference: ChangeDetectorRef,
    ) {
        super(changeDetectorReference);
    }

    ngOnInit(): void {
        // show submission answers in UI
        this.updateViewFromSubmission();

        this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdAndProblemStatement.subscribe((update) => {
            this.updateExerciseProblemStatementById(update.exerciseId, update.problemStatement);
        });
    }

    toggleHighlightedProblemStatement(): void {
        if (this.showHighlightedDifferences) {
            this.exercise.problemStatement = this.updatedProblemStatement;
        } else {
            this.exercise.problemStatement = this.updatedProblemStatementWithHighlightedDifferences;
        }
        this.showHighlightedDifferences = !this.showHighlightedDifferences;
    }

    getExercise(): Exercise {
        return this.exercise;
    }

    updateExerciseProblemStatementById(exerciseId: number, updatedProblemStatement: string) {
        if (updatedProblemStatement != undefined && exerciseId === this.exercise.id) {
            this.updatedProblemStatement = updatedProblemStatement;
            this.exercise.problemStatement = this.highlightProblemStatementDifferences();
        }
    }

    highlightProblemStatementDifferences() {
        if (!this.updatedProblemStatement) {
            return;
        }

        this.showHighlightedDifferences = true;

        // creates the diffMatchPatch library object to be able to modify strings
        const dmp = new DiffMatchPatch();
        let outdatedProblemStatement: string;

        // checks if first update i.e. no highlight
        if (!this.previousProblemStatementUpdate) {
            outdatedProblemStatement = this.exercise.problemStatement!;
            this.previousProblemStatementUpdate = this.updatedProblemStatement;
            // else use previousProblemStatementUpdate as new outdatedProblemStatement to avoid inserted HTML elements
        } else {
            outdatedProblemStatement = this.previousProblemStatementUpdate;
        }

        // finds the initial difference then cleans the text with added html & css elements
        const diff = dmp.diff_main(outdatedProblemStatement!, this.updatedProblemStatement);
        dmp.diff_cleanupEfficiency(diff);
        // remove ¶; (= &para;) symbols
        this.updatedProblemStatementWithHighlightedDifferences = dmp.diff_prettyHtml(diff).replace(/&para;/g, '');
        return this.updatedProblemStatementWithHighlightedDifferences;
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
