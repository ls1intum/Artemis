import { Component, EventEmitter, Input, OnInit, Output } from '@angular/core';
import { Subscription } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { Exercise } from 'app/entities/exercise.model';
import { Diff, DiffMatchPatch, DiffOperation } from 'diff-match-patch-typescript';

@Component({
    selector: 'jhi-exam-exercise-update-highlighter',
    templateUrl: './exam-exercise-update-highlighter.component.html',
    styleUrls: ['./exam-exercise-update-highlighter.component.scss'],
})
export class ExamExerciseUpdateHighlighterComponent implements OnInit {
    subscriptionToLiveExamExerciseUpdates: Subscription;
    previousProblemStatementUpdate: string;
    updatedProblemStatementWithHighlightedDifferences: string;
    updatedProblemStatement: string;
    showHighlightedDifferences = true;

    @Input() exercise: Exercise;

    @Output() problemStatementUpdateEvent: EventEmitter<string> = new EventEmitter<string>();

    constructor(private examExerciseUpdateService: ExamExerciseUpdateService) {}

    ngOnInit(): void {
        this.subscriptionToLiveExamExerciseUpdates = this.examExerciseUpdateService.currentExerciseIdAndProblemStatement.subscribe((update) => {
            this.updateExerciseProblemStatementById(update.exerciseId, update.problemStatement);
        });
    }

    /**
     * Switches the view between the new(updated) problem statement without the difference
     * with the view showing the difference between the new and old problem statement and vice versa.
     */
    toggleHighlightedProblemStatement(): void {
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
            this.updatedProblemStatement = updatedProblemStatement;
            this.exercise.problemStatement = this.highlightProblemStatementDifferences();
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

        this.showHighlightedDifferences = true;

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

        this.previousProblemStatementUpdate = this.updatedProblemStatement;

        // finds the initial difference then cleans the text with added html & css elements
        const diff = dmp.diff_main(outdatedProblemStatement!, this.updatedProblemStatement);
        dmp.diff_cleanupEfficiency(diff);
        // remove ¶; (= &para;) symbols

        this.updatedProblemStatementWithHighlightedDifferences = this.diffPrettyHtml(diff);
        return this.updatedProblemStatementWithHighlightedDifferences;
    }

    /**
     * Convert a diff array into a pretty HTML report.
     * Modified diff_prettHtml() method from DiffMatchPatch
     * Keeps markdown styling intact (not like the original method)
     * @param diffs Array of diff tuples. (from DiffMatchPatch)
     * @return the HTML representation as string with markdown intact.
     */
    diffPrettyHtml = function (diffs: Diff[]): string {
        const html = [];
        for (let x = 0; x < diffs.length; x++) {
            const op = diffs[x][0]; // Operation (insert, delete, equal)
            const text = diffs[x][1]; // Text of change.
            switch (op) {
                case DiffOperation.DIFF_INSERT:
                    html[x] = '<ins style="background:#e6ffe6;">' + text + '</ins>';
                    break;
                case DiffOperation.DIFF_DELETE:
                    html[x] = '<del style="background:#ffe6e6;">' + text + '</del>';
                    break;
                case DiffOperation.DIFF_EQUAL:
                    html[x] = text;
                    break;
            }
        }
        return html.join('');
    };
}
