import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { get } from 'lodash';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';

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
    @Input() public numberOfComplaints: number;
    @Input() public numberOfOpenComplaints: number;
    @Input() public numberOfMoreFeedbackRequests: number;
    @Input() public numberOfOpenMoreFeedbackRequests: number;
    @Input() exercise: Exercise;

    tutorParticipationStatus: TutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;

    ExerciseType = ExerciseType;
    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    percentageAssessmentProgress = 0;
    percentageComplaintsProgress = 0;

    routerLink: string;

    constructor(private router: Router) {}

    /**
     * Life cycle hook, called on initialisation.
     * Sets the {@link tutorParticipationStatus} and creates the {@link routerLink} using the {@link trainedExampleSubmissions} exercise and course ID.
     * It also triggers {@link calculatePercentageAssessmentProgress} and {@link calculatePercentageComplaintsProgress}.
     */
    ngOnInit() {
        this.tutorParticipationStatus = this.tutorParticipation.status;
        const exerciseId = get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.id');
        const courseId = get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.course.id');

        if (courseId && exerciseId) {
            this.routerLink = `/course-management/${courseId}/exercises/${exerciseId}/tutor-dashboard`;
        }
        this.calculatePercentageAssessmentProgress();
        this.calculatePercentageComplaintsProgress();
    }

    /**
     * Wrapper function to {@link router}.navigate safely by checking for null and empty string
     */
    navigate() {
        if (this.routerLink && this.routerLink.length > 0) {
            this.router.navigate([this.routerLink]);
        }
    }

    /**
     * A lifecycle hook called by Angular when any data-bound property of a component changes.
     * Sets {@link tutorParticipation} and {@link tutorParticipationStatus} to the new values.
     *
     * @param changes Changes made.
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.tutorParticipation) {
            this.tutorParticipation = changes.tutorParticipation.currentValue;
            this.tutorParticipationStatus = this.tutorParticipation.status;
        }
        this.calculatePercentageAssessmentProgress();
        this.calculatePercentageComplaintsProgress();
    }

    /**
     * Calculates the Assessment Progress percentage. {@link numberOfAssessments} divided by {@link numberOfParticipations}.
     * Sets the value in {@link calculatePercentageAssessmentProgress}
     * @method
     */
    calculatePercentageAssessmentProgress() {
        if (this.numberOfParticipations !== 0) {
            this.percentageAssessmentProgress = Math.round((this.numberOfAssessments / this.numberOfParticipations) * 100);
        }
    }

    /**
     * Calculate the percentage of responded complaints.
     * This is calculated adding the number of {@link numberOfOpenComplaints} and all feedback requests
     * {@link numberOfMoreFeedbackRequests} + {@link numberOfOpenMoreFeedbackRequests}.
     * That is divided by the total of {@link numberOfComplaints} and {@link numberOfMoreFeedbackRequests}.
     * Sets the value in {@link percentageComplaintsProgress}.
     * @method
     */
    calculatePercentageComplaintsProgress() {
        if (this.numberOfComplaints + this.numberOfMoreFeedbackRequests !== 0) {
            this.percentageComplaintsProgress = Math.round(
                ((this.numberOfComplaints -
                this.numberOfOpenComplaints + // nr of evaluated complaints
                    (this.numberOfMoreFeedbackRequests - this.numberOfOpenMoreFeedbackRequests)) / // nr of evaluated more feedback requests
                    (this.numberOfComplaints + this.numberOfMoreFeedbackRequests)) * // total nr of complaints and feedback requests
                    100,
            );
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
            const reviewedByTutor = this.tutorParticipation.trainedExampleSubmissions.filter((exampleSubmission) => !exampleSubmission.usedForTutorial);
            const exercisesToReview = this.exercise.exampleSubmissions.filter((exampleSubmission) => !exampleSubmission.usedForTutorial);
            const assessedByTutor = this.tutorParticipation.trainedExampleSubmissions.filter((exampleSubmission) => exampleSubmission.usedForTutorial);
            const exercisesToAssess = this.exercise.exampleSubmissions.filter((exampleSubmission) => exampleSubmission.usedForTutorial);

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

    /**
     * Returns a string representation of the progress bar class based on the current status.
     * @method
     * @returns {string} 'opaque', 'active', 'orange'
     */
    calculateClassProgressBar() {
        if (this.tutorParticipationStatus !== this.TRAINED && this.tutorParticipationStatus !== this.COMPLETED) {
            return 'opaque';
        }

        if (
            this.tutorParticipationStatus === this.COMPLETED ||
            this.numberOfParticipations === this.numberOfAssessments ||
            this.numberOfOpenComplaints + this.numberOfOpenMoreFeedbackRequests === 0
        ) {
            return 'active';
        }

        return 'orange';
    }

    /**
     * Calculates the number of evaluated/closed complaints and evaluated/closed feedback requests.
     * @method
     * @returns {number}  Number of Evaluated Complaints and Feedback Requests
     */
    calculateComplaintsNumerator() {
        return this.numberOfComplaints - this.numberOfOpenComplaints + (this.numberOfMoreFeedbackRequests - this.numberOfOpenMoreFeedbackRequests);
    }

    /**
     * Calculates the total of {@link numberOfComplaints} {@link numberOfMoreFeedbackRequests}
     * @method
     * @returns {number} Total number of Complaints and Feedback Requests
     */
    calculateComplaintsDenominator() {
        return this.numberOfComplaints + this.numberOfMoreFeedbackRequests;
    }
}
