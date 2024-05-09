import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { Diff, DiffMatchPatch, DiffOperation } from 'diff-match-patch-typescript';

@Component({
    selector: 'jhi-exam-exercise-update-highlighter',
    templateUrl: './exam-exercise-update-highlighter.component.html',
    styleUrls: ['./exam-exercise-update-highlighter.component.scss'],
})
export class ExamExerciseUpdateHighlighterComponent implements OnInit, OnDestroy {
    subscriptionToLiveExamExerciseUpdates: Subscription;
    themeSubscription: Subscription;
    previousProblemStatementUpdate?: string;
    updatedProblemStatementWithHighlightedDifferences: string;
    outdatedProblemStatement: string;
    updatedProblemStatement: string;
    showHighlightedDifferences = true;
    isHidden = false;
    @Input() exercise: Exercise;

    @Output() problemStatementUpdateEvent: EventEmitter<string> = new EventEmitter<string>();

    constructor(private examExerciseUpdateService: ExamExerciseUpdateService) {}

    ngOnInit(): void {
        this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdAndProblemStatement.subscribe((update) => {
            if (update) {
                this.updateExerciseProblemStatementById(update.exerciseId, update.problemStatement);
                this.isHidden = false;
            } else {
                // No update so hide the component
                this.isHidden = true;
            }
        });
    }

    ngOnDestroy(): void {
        this.subscriptionToLiveExamExerciseUpdates?.unsubscribe();
        this.themeSubscription?.unsubscribe();
    }

    /**
     * Switches the view between the new(updated) problem statement without the difference
     * with the view showing the difference between the new and old problem statement and vice versa.
     */
    toggleHighlightedProblemStatement(event: MouseEvent): void {
        // prevents the jhi-resizeable-container from collapsing the right panel on a button click
        event.stopPropagation();
        if (this.showHighlightedDifferences) {
            this.exercise.problemStatement = this.updatedProblemStatement;
        } else {
            this.exercise.problemStatement = this.updatedProblemStatementWithHighlightedDifferences;
        }
        this.showHighlightedDifferences = !this.showHighlightedDifferences;
        this.problemStatementUpdateEvent.emit(this.exercise.problemStatement);
    }

    /**
     * Updates the problem statement of the provided exercises based on its id.
     * Also calls the method to highlight the differences between the old and new problem statement.
     * @param exerciseId is the id of the exercise which problem statement should be updated.
     * @param updatedProblemStatement is the new problem statement that should replace the old one.
     */
    updateExerciseProblemStatementById(exerciseId: number, updatedProblemStatement: string) {
        if (updatedProblemStatement != undefined && exerciseId === this.exercise.id) {
            this.outdatedProblemStatement = this.exercise.problemStatement!;
            this.updatedProblemStatement = updatedProblemStatement;
            this.previousProblemStatementUpdate = this.updatedProblemStatement;
            this.showHighlightedDifferences = true;
            // Highlighting of the changes in the problem statement of a programming exercise id handled
            // in ProgrammingExerciseInstructionComponent
            if (this.exercise.type !== ExerciseType.PROGRAMMING) {
                this.exercise.problemStatement = this.highlightProblemStatementDifferences();
            }
        }
        this.problemStatementUpdateEvent.emit(this.exercise.problemStatement);
    }

    /**
     * Computes the difference between the old and new (updated) problem statement and displays this difference.
     */
    highlightProblemStatementDifferences() {
        if (!this.updatedProblemStatement) {
            return;
        }
        // creates the diffMatchPatch library object to be able to modify strings
        const dmp = new DiffMatchPatch();
        let outdatedProblemStatement: string;

        // checks if first update i.e. no highlight
        if (!this.previousProblemStatementUpdate) {
            outdatedProblemStatement = this.exercise.problemStatement!;
            // else use previousProblemStatementUpdate as new outdatedProblemStatement to avoid inserted HTML elements
        } else {
            outdatedProblemStatement = this.previousProblemStatementUpdate;
        }

        const diff = dmp.diff_main(outdatedProblemStatement!, this.updatedProblemStatement);

        // finds the initial difference then cleans the text with added html & css elements
        dmp.diff_cleanupEfficiency(diff);
        this.updatedProblemStatementWithHighlightedDifferences = this.diffPrettyHtml(diff);

        return this.updatedProblemStatementWithHighlightedDifferences;
    }

    /**
     * Convert a diff array into a pretty HTML report.
     * Keeps markdown styling intact (not like the original method)
     * Modified diff_prettHtml() method from DiffMatchPatch
     * The original library method is intended to be modified
     * for more info: https://www.npmjs.com/package/diff-match-patch,
     * https://github.com/google/diff-match-patch/blob/master/javascript/diff_match_patch_uncompressed.js
     *
     * @param diffs Array of diff tuples. (from DiffMatchPatch)
     * @return the HTML representation as string with markdown intact.
     */
    private diffPrettyHtml(diffs: Diff[]): string {
        const html: any[] = [];
        diffs.forEach((diff: Diff, index: number) => {
            const op = diffs[index][0]; // Operation (insert, delete, equal)
            const text = diffs[index][1]; // Text of change.
            switch (op) {
                case DiffOperation.DIFF_INSERT:
                    html[index] = '<ins class="bg-success">' + text + '</ins>';
                    break;
                case DiffOperation.DIFF_DELETE:
                    html[index] = '<del class="bg-danger">' + text + '</del>';
                    break;
                case DiffOperation.DIFF_EQUAL:
                    html[index] = text;
                    break;
            }
        });
        return html.join('');
    }
}
