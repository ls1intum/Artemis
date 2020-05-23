import { Component, Input, OnInit } from '@angular/core';
import { SafeHtml } from '@angular/platform-browser';
import { Exercise } from 'app/entities/exercise.model';

import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AssessmentType } from 'app/entities/assessment-type.model';

@Component({
    selector: 'jhi-exercise-details',
    templateUrl: './exercise-details.component.html',
    styleUrls: ['./exercise-details.component.scss'],
})
export class ExerciseDetailsComponent implements OnInit {
    @Input() exerciseDetails: Exercise;

    AssessmentType = AssessmentType;

    superExerciseDetails: Exercise;
    formattedProblemStatement: SafeHtml | null;
    formattedGradingInstructions: SafeHtml | null;

    constructor(private artemisMarkdown: ArtemisMarkdownService) {}
    /**
     * Life cycle hook to indicate component creation is done
     */
    ngOnInit() {
        this.superExerciseDetails = this.exerciseDetails;
        this.formattedGradingInstructions = this.artemisMarkdown.safeHtmlForMarkdown(this.superExerciseDetails.gradingInstructions);
        this.formattedProblemStatement = this.artemisMarkdown.safeHtmlForMarkdown(this.superExerciseDetails.problemStatement);
    }
}
