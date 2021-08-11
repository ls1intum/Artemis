import { Component, Input, OnInit } from '@angular/core';
import { Subscription } from 'rxjs';
import { ExamExerciseUpdateService } from 'app/exam/manage/exam-exercise-update.service';
import { Exercise } from 'app/entities/exercise.model';
import { DiffMatchPatch } from 'diff-match-patch-typescript';

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

    constructor(private examExerciseUpdateService: ExamExerciseUpdateService) {}

    ngOnInit(): void {
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
}
