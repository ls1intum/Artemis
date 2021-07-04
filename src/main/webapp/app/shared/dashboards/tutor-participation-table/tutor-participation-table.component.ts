import { Component, Input, OnInit, ViewEncapsulation } from '@angular/core';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { DueDateStat } from 'app/course/dashboards/instructor-course-dashboard/due-date-stat.model';
import { TutorParticipationStatus } from 'app/entities/participation/tutor-participation.model';

class Fraction {
    constructor(public numerator: number, public denominator: number) {}

    isEqual(): boolean {
        return this.numerator === this.denominator;
    }
}

export class TutorParticipationViewModel {
    constructor(
        public exercise: Exercise,
        public participationStatus: TutorParticipationStatus,
        public numberOfComplaints: number,
        public numberOfOpenComplaints: number,
        public numberOfMoreFeedbackRequests: number,
        public numberOfOpenMoreFeedbackRequests: number,
        public numberOfAssessmentsOfCorrectionRounds: DueDateStat[],
        public numberOfSubmissions: DueDateStat,
        public exampleSubmissions: any,
    ) {}

    get didReadGradingInstructions(): boolean {
        return this.participationStatus !== TutorParticipationStatus.NOT_PARTICIPATED;
    }

    get didTrainOnExampleSubmissions(): boolean {
        return this.participationStatus !== TutorParticipationStatus.NOT_PARTICIPATED && this.participationStatus !== TutorParticipationStatus.REVIEWED_INSTRUCTIONS;
    }

    get dueDateFractions(): [Fraction, Fraction][] {
        return this.numberOfAssessmentsOfCorrectionRounds.map((stat) => [
            new Fraction(stat.inTime, this.numberOfSubmissions?.inTime || 0),
            new Fraction(stat.late, this.numberOfSubmissions?.late || 0),
        ]);
    }

    didReviewStudentsSubmissionsForRound(round: number): boolean {
        return this.didReviewInTimeStudentsSubmissionsForRound(round) && this.didReviewLateStudentsSubmissionsForRound(round);
    }

    didReviewInTimeStudentsSubmissionsForRound(round: number): boolean {
        return this.didTrainOnExampleSubmissions && new Fraction(this.numberOfAssessmentsOfCorrectionRounds[round].inTime, this.numberOfSubmissions?.inTime || 0).isEqual();
    }

    didReviewLateStudentsSubmissionsForRound(round: number): boolean {
        return this.didTrainOnExampleSubmissions && new Fraction(this.numberOfAssessmentsOfCorrectionRounds[round].late, this.numberOfSubmissions?.late || 0).isEqual();
    }

    get evaluatedComplaints(): Fraction {
        return new Fraction(
            this.numberOfComplaints - this.numberOfOpenComplaints + (this.numberOfMoreFeedbackRequests - this.numberOfOpenMoreFeedbackRequests),
            this.numberOfComplaints + this.numberOfMoreFeedbackRequests,
        );
    }

    get didEvaluateAllComplaints(): boolean {
        return this.didTrainOnExampleSubmissions && this.evaluatedComplaints.isEqual();
    }
}

@Component({
    selector: 'jhi-tutor-participation-table',
    templateUrl: './tutor-participation-table.component.html',
    styleUrls: ['./tutor-participation-table.component.scss'],
    encapsulation: ViewEncapsulation.None,
})
export class TutorParticipationTableComponent {
    @Input() public tutorParticipationViewModel: TutorParticipationViewModel;

    ExerciseType = ExerciseType;
}
