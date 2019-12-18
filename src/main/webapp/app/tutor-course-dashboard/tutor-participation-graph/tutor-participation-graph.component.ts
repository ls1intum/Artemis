import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/tutor-participation';
import { Router } from '@angular/router';
import { get } from 'lodash';
import { Exercise, ExerciseType } from 'app/entities/exercise';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styleUrls: ['./tutor-participation-graph.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TutorParticipationGraphComponent implements OnInit, OnChanges {
    @Input() public tutorParticipation: TutorParticipation;
    @Input() public numberOfParticipations: number;
    @Input() public numberOfAssessments: number;
    @Input() exercise: Exercise;

    tutorParticipationStatus: TutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;

    ExerciseType = ExerciseType;
    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    percentageAssessmentProgress = 0;

    routerLink: string;

    constructor(private router: Router) {}

    ngOnInit() {
        this.tutorParticipationStatus = this.tutorParticipation.status;
        const exerciseId = get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.id');
        const courseId = get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.course.id');

        if (courseId && exerciseId) {
            this.routerLink = `/course/${courseId}/exercise/${exerciseId}/tutor-dashboard`;
        }

        if (this.numberOfParticipations !== 0) {
            this.percentageAssessmentProgress = Math.round((this.numberOfAssessments / this.numberOfParticipations) * 100);
        }
    }

    navigate() {
        if (this.routerLink && this.routerLink.length > 0) {
            this.router.navigate([this.routerLink]);
        }
    }

    ngOnChanges(changes: SimpleChanges): void {
        if (changes.tutorParticipation) {
            this.tutorParticipation = changes.tutorParticipation.currentValue;
            this.tutorParticipationStatus = this.tutorParticipation.status;
        }

        if (this.numberOfParticipations !== 0) {
            this.percentageAssessmentProgress = Math.round((this.numberOfAssessments / this.numberOfParticipations) * 100);
        }
    }

    /**
     * Calculates the classes for the steps (circles) in the tutor participation graph
     * @param step for which the class should be calculated for (NOT_PARTICIPATED, REVIEWED_INSTRUCTIONS, TRAINED)
     */
    calculateClasses(step: TutorParticipationStatus): string {
        // Returns 'active' if the current participation status is not trained
        if (step === this.tutorParticipationStatus && step !== this.TRAINED) {
            return 'active';
        }

        // Returns 'opaque' if the tutor has not participated yet
        if (step === this.TRAINED && this.tutorParticipationStatus === this.NOT_PARTICIPATED) {
            return 'opaque';
        }

        if (step === this.TRAINED && this.exercise.exampleSubmissions && this.tutorParticipation.trainedExampleSubmissions) {
            const reviewedByTutor = this.tutorParticipation.trainedExampleSubmissions.filter(exampleSubmission => !exampleSubmission.usedForTutorial);
            const exercisesToReview = this.exercise.exampleSubmissions.filter(exampleSubmission => !exampleSubmission.usedForTutorial);
            const assessedByTutor = this.tutorParticipation.trainedExampleSubmissions.filter(exampleSubmission => exampleSubmission.usedForTutorial);
            const exercisesToAssess = this.exercise.exampleSubmissions.filter(exampleSubmission => exampleSubmission.usedForTutorial);

            // Returns 'orange' if there are still open example reviews or assessments
            if (
                (exercisesToReview.length > 0 && exercisesToReview.length !== reviewedByTutor.length) ||
                (exercisesToAssess.length > 0 && exercisesToAssess.length !== assessedByTutor.length)
            ) {
                return 'orange';
            }
        }

        return '';
    }

    calculateProgressBarClass(): string {
        const percentage = this.percentageAssessmentProgress;

        if (percentage < 50) {
            return 'bg-danger';
        } else if (percentage < 100) {
            return 'bg-warning';
        }

        return 'bg-success';
    }

    chooseProgressBarTextColor() {
        if (this.percentageAssessmentProgress < 100) {
            return 'text-dark';
        }

        return 'text-white';
    }

    calculateClassProgressBar() {
        if (this.tutorParticipationStatus !== this.TRAINED && this.tutorParticipationStatus !== this.COMPLETED) {
            return 'opaque';
        }

        if (this.tutorParticipationStatus === this.COMPLETED || this.numberOfParticipations === this.numberOfAssessments) {
            return 'active';
        }

        return 'orange';
    }
}
