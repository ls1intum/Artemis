import { Component, Input, OnChanges, OnInit, SimpleChanges, ViewEncapsulation } from '@angular/core';
import { Router } from '@angular/router';
import { get } from 'lodash-es';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';
import { DueDateStat } from 'app/course/dashboards/due-date-stat.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { faBook, faChalkboardTeacher } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styleUrls: ['./tutor-participation-graph.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TutorParticipationGraphComponent implements OnInit, OnChanges {
    @Input() public tutorParticipation: TutorParticipation;
    @Input() public numberOfSubmissions?: DueDateStat;
    @Input() public totalNumberOfAssessments?: DueDateStat;
    @Input() public numberOfComplaints: number;
    @Input() public numberOfOpenComplaints: number;
    @Input() public numberOfMoreFeedbackRequests: number;
    @Input() public numberOfOpenMoreFeedbackRequests: number;
    @Input() exercise: Exercise;
    @Input() public numberOfAssessmentsOfCorrectionRounds: DueDateStat[];

    tutorParticipationStatus: TutorParticipationStatus = TutorParticipationStatus.NOT_PARTICIPATED;

    ExerciseType = ExerciseType;
    NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    TRAINED = TutorParticipationStatus.TRAINED;
    COMPLETED = TutorParticipationStatus.COMPLETED;

    percentageComplaintsProgress = 0;

    percentageInTimeAssessmentProgressOfCorrectionRound: number[] = [];
    percentageLateAssessmentProgressOfCorrectionRound: number[] = [];

    routerLink: string;

    shouldShowManualAssessments = true;

    // Icons
    faBook = faBook;
    faChalkboardTeacher = faChalkboardTeacher;

    constructor(private router: Router) {}

    /**
     * Life cycle hook called by Angular to indicate that Angular is done creating the component
     */
    ngOnInit() {
        this.tutorParticipationStatus = this.tutorParticipation.status!;
        const exerciseId = get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.id');
        const courseId = get(this.tutorParticipation, 'trainedExampleSubmissions[0].exercise.course.id');

        if (courseId && exerciseId) {
            this.routerLink = `/course-management/${courseId}/assessment-dashboard/${exerciseId}`;
        }
        this.calculatePercentageAssessmentProgress();
        this.calculatePercentageComplaintsProgress();

        if (this.exercise && this.exercise.type === ExerciseType.PROGRAMMING) {
            this.shouldShowManualAssessments = !(this.exercise as ProgrammingExercise).allowComplaintsForAutomaticAssessments;
        }
    }

    /**
     * Function wrapping router.navigate safely by checking for null and empty string
     */
    navigate() {
        if (this.routerLink && this.routerLink.length > 0) {
            this.router.navigate([this.routerLink]);
        }
    }

    /**
     * A lifecycle hook called by Angular when any data-bound property of a component changes
     *
     * @param changes Changes made
     */
    ngOnChanges(changes: SimpleChanges): void {
        if (changes.tutorParticipation) {
            this.tutorParticipation = changes.tutorParticipation.currentValue;
            this.tutorParticipationStatus = this.tutorParticipation.status!;
        }
        this.calculatePercentageAssessmentProgress();
        this.calculatePercentageComplaintsProgress();
    }

    /**
     * Function to calculate the percentage of the number of assessments divided by the number of participations
     */
    calculatePercentageAssessmentProgress() {
        for (const [index, numberOfAssessments] of this.numberOfAssessmentsOfCorrectionRounds.entries()) {
            this.percentageInTimeAssessmentProgressOfCorrectionRound[index] = 0;
            this.percentageLateAssessmentProgressOfCorrectionRound[index] = 0;
            if (this.numberOfSubmissions && this.numberOfSubmissions.inTime !== 0) {
                this.percentageInTimeAssessmentProgressOfCorrectionRound[index] = Math.floor((numberOfAssessments.inTime / this.numberOfSubmissions.inTime) * 100);
            }
            if (this.numberOfSubmissions && this.numberOfSubmissions?.late !== 0) {
                this.percentageLateAssessmentProgressOfCorrectionRound[index] = Math.floor((numberOfAssessments.late / this.numberOfSubmissions.late) * 100);
            }
        }
    }

    /**
     * Function to calculate the percentage of responded complaints
     * This is calculated adding the number of not evaluated complaints and feedback requests and dividing
     * by the total number of complaints and feedbacks and rounding it tpwards zero.
     */
    calculatePercentageComplaintsProgress() {
        if (this.numberOfComplaints + this.numberOfMoreFeedbackRequests !== 0) {
            this.percentageComplaintsProgress = Math.floor(
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
     * Returns a string representation of the progress bar class based on the current status
     */
    calculateClassProgressBar() {
        if (this.tutorParticipationStatus !== this.TRAINED && this.tutorParticipationStatus !== this.COMPLETED) {
            return 'opaque';
        }

        if (
            this.tutorParticipationStatus === this.COMPLETED ||
            (this.numberOfSubmissions && this.totalNumberOfAssessments && this.numberOfSubmissions.inTime === this.totalNumberOfAssessments.inTime) ||
            this.numberOfOpenComplaints + this.numberOfOpenMoreFeedbackRequests === 0
        ) {
            return 'active';
        }

        return 'orange';
    }

    /**
     * Returns the total number of evaluated complaints and feedback requests
     */
    calculateComplaintsNumerator() {
        return this.numberOfComplaints - this.numberOfOpenComplaints + (this.numberOfMoreFeedbackRequests - this.numberOfOpenMoreFeedbackRequests);
    }

    /**
     * Returns the total number of complaints and feedback requests
     */
    calculateComplaintsDenominator() {
        return this.numberOfComplaints + this.numberOfMoreFeedbackRequests;
    }
}
