import { ChangeDetectionStrategy, Component, ViewEncapsulation, computed, inject, input } from '@angular/core';
import { Router } from '@angular/router';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TutorParticipation, TutorParticipationStatus } from 'app/exercise/shared/entities/participation/tutor-participation.model';
import { DueDateStat } from 'app/assessment/shared/assessment-dashboard/due-date-stat.model';
import { faBook, faChalkboardTeacher } from '@fortawesome/free-solid-svg-icons';
import { NgClass } from '@angular/common';
import { TooltipModule } from 'primeng/tooltip';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ProgressBarComponent } from './progress-bar/progress-bar.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';

@Component({
    selector: 'jhi-tutor-participation-graph',
    templateUrl: './tutor-participation-graph.component.html',
    styleUrls: ['./tutor-participation-graph.component.scss'],
    encapsulation: ViewEncapsulation.None,
    imports: [NgClass, TooltipModule, FaIconComponent, ProgressBarComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TutorParticipationGraphComponent {
    private router = inject(Router);

    readonly tutorParticipation = input.required<TutorParticipation>();
    readonly numberOfSubmissions = input<DueDateStat | undefined>();
    readonly totalNumberOfAssessments = input<number | undefined>();
    readonly numberOfComplaints = input<number>(0);
    readonly numberOfOpenComplaints = input<number>(0);
    readonly numberOfMoreFeedbackRequests = input<number>(0);
    readonly numberOfOpenMoreFeedbackRequests = input<number>(0);
    readonly exercise = input.required<Exercise>();
    readonly numberOfAssessmentsOfCorrectionRounds = input<DueDateStat[]>([]);

    readonly tutorParticipationStatus = computed<TutorParticipationStatus>(() => this.tutorParticipation().status ?? TutorParticipationStatus.NOT_PARTICIPATED);

    readonly routerLink = computed<string>(() => {
        const trained = this.tutorParticipation().trainedExampleSubmissions?.[0];
        const exerciseId = trained?.exercise?.id;
        const courseId = trained?.exercise?.course?.id;
        return courseId && exerciseId ? `/course-management/${courseId}/assessment-dashboard/${exerciseId}` : '';
    });

    readonly shouldShowManualAssessments = computed<boolean>(() => {
        const exercise = this.exercise();
        if (exercise?.type === ExerciseType.PROGRAMMING) {
            return !(exercise as ProgrammingExercise).allowComplaintsForAutomaticAssessments;
        }
        return true;
    });

    readonly percentageInTimeAssessmentProgressOfCorrectionRound = computed<number[]>(() => {
        const submissions = this.numberOfSubmissions();
        return this.numberOfAssessmentsOfCorrectionRounds().map((round) => (submissions && submissions.inTime !== 0 ? Math.floor((round.inTime / submissions.inTime) * 100) : 0));
    });

    readonly percentageLateAssessmentProgressOfCorrectionRound = computed<number[]>(() => {
        const submissions = this.numberOfSubmissions();
        return this.numberOfAssessmentsOfCorrectionRounds().map((round) => (submissions && submissions.late !== 0 ? Math.floor((round.late / submissions.late) * 100) : 0));
    });

    readonly complaintsNumerator = computed<number>(
        () => this.numberOfComplaints() - this.numberOfOpenComplaints() + (this.numberOfMoreFeedbackRequests() - this.numberOfOpenMoreFeedbackRequests()),
    );

    readonly complaintsDenominator = computed<number>(() => this.numberOfComplaints() + this.numberOfMoreFeedbackRequests());

    readonly percentageComplaintsProgress = computed<number>(() => {
        const denominator = this.complaintsDenominator();
        return denominator !== 0 ? Math.floor((this.complaintsNumerator() / denominator) * 100) : 0;
    });

    readonly progressBarClass = computed<string>(() => {
        const status = this.tutorParticipationStatus();
        if (status !== TutorParticipationStatus.TRAINED && status !== TutorParticipationStatus.COMPLETED) {
            return 'opaque';
        }
        const submissions = this.numberOfSubmissions();
        const totalAssessments = this.totalNumberOfAssessments();
        if (
            status === TutorParticipationStatus.COMPLETED ||
            (submissions && totalAssessments && submissions.inTime === totalAssessments) ||
            this.numberOfOpenComplaints() + this.numberOfOpenMoreFeedbackRequests() === 0
        ) {
            return 'active';
        }
        return 'orange';
    });

    readonly ExerciseType = ExerciseType;
    readonly NOT_PARTICIPATED = TutorParticipationStatus.NOT_PARTICIPATED;
    readonly REVIEWED_INSTRUCTIONS = TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    readonly TRAINED = TutorParticipationStatus.TRAINED;
    readonly COMPLETED = TutorParticipationStatus.COMPLETED;

    readonly faBook = faBook;
    readonly faChalkboardTeacher = faChalkboardTeacher;

    /** Wraps router.navigate safely by checking for empty string */
    navigate() {
        const link = this.routerLink();
        if (link.length > 0) {
            this.router.navigate([link]);
        }
    }

    /** Calculates the class for a step (circle) based on the current participation status */
    calculateClasses(step: TutorParticipationStatus): string {
        const status = this.tutorParticipationStatus();
        if (step === status && step !== this.TRAINED) {
            return 'active';
        }
        if (step === this.TRAINED && status === this.NOT_PARTICIPATED) {
            return 'opaque';
        }
        if (step === this.TRAINED) {
            const exercise = this.exercise();
            const tutorParticipation = this.tutorParticipation();
            if (exercise?.exampleSubmissions && tutorParticipation?.trainedExampleSubmissions) {
                const reviewedByTutor = tutorParticipation.trainedExampleSubmissions.filter((submission) => !submission.usedForTutorial);
                const exercisesToReview = exercise.exampleSubmissions.filter((submission) => !submission.usedForTutorial);
                const assessedByTutor = tutorParticipation.trainedExampleSubmissions.filter((submission) => submission.usedForTutorial);
                const exercisesToAssess = exercise.exampleSubmissions.filter((submission) => submission.usedForTutorial);

                if (
                    (exercisesToReview.length > 0 && exercisesToReview.length !== reviewedByTutor.length) ||
                    (exercisesToAssess.length > 0 && exercisesToAssess.length !== assessedByTutor.length)
                ) {
                    return 'orange';
                }
            }
        }
        return '';
    }
}
