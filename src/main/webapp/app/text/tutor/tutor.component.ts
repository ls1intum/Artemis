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
    totalScore = 5;

    public getColorForIndex = colorForIndex;

    public addAssessment(assessmentText: string): void {
        const assessment = new TextAssessment(assessmentText, 0, null);
        this.assessments.push(assessment);
    }

    public deleteAssessment(assessmentToDelete: TextAssessment): void {
        this.assessments = this.assessments.filter(elem => elem !== assessmentToDelete);
    }
}
