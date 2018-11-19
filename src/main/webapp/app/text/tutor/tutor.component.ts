import { Component } from '@angular/core';
import { TextExercise } from 'app/entities/text-exercise';
import { TextSubmission } from 'app/entities/text-submission';
import { TextAssessment } from 'app/entities/text-assessments/text-assessments.model';
import { colorForIndex, colors } from 'app/text/tutor/highlight-colors';

@Component({
    selector: 'jhi-tutor',
    templateUrl: './tutor.component.html',
    styles: []
})
export class ArTEMiSTextTutorComponent {
    text: string;
    submission: TextSubmission = <TextSubmission>{ text: 'Lorem Ipsum' };
    result: any;
    assessments: TextAssessment[] = [];
    exercise: TextExercise;
    totalScore = 0;
    assessmentsAreValid: boolean;
    invalidError: string;
    isAuthorized = true;
    accountId = 0;
    done = false;
    busy = false;

    public getColorForIndex = colorForIndex;

    public addAssessment(assessmentText: string): void {
        const assessment = new TextAssessment(assessmentText, 0, null);
        this.assessments.push(assessment);
    }

    public deleteAssessment(assessmentToDelete: TextAssessment): void {
        this.assessments = this.assessments.filter(elem => elem !== assessmentToDelete);
    }

    public async save(): Promise<void> {
        this.checkScoreBoundaries();
    }

    public async submit(): Promise<void> {
        this.checkScoreBoundaries();
    }

    public previous(): void {
        console.log('previousState');
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     */
    public checkScoreBoundaries() {
        if (!this.assessments || this.assessments.length === 0) {
            this.totalScore = 0;
            return;
        }

        const credits = this.assessments.map(assessment => assessment.credits);

        if (!credits.every(credit => credit !== null)) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        this.totalScore = credits.reduce((a, b) => a + b, 0);
        this.assessmentsAreValid = true;
        this.invalidError = null;
    }

    public assessNextOptimal(): void {
        console.log('assessNextOptimal()');
    }
}
