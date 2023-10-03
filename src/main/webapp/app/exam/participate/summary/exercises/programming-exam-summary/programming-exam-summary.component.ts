import { Component, Input, OnInit, Optional } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { FeedbackComponentPreparedParams, prepareFeedbackComponentParameters } from 'app/exercises/shared/feedback/feedback.utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';

@Component({
    selector: 'jhi-programming-exam-summary',
    templateUrl: './programming-exam-summary.component.html',
})
export class ProgrammingExamSummaryComponent implements OnInit {
    @Input() exercise: ProgrammingExercise;

    @Input() participation: ProgrammingExerciseStudentParticipation;

    @Input() submission: ProgrammingSubmission;

    @Input() isTestRun?: boolean = false;

    @Input() exam: Exam;

    @Input() isAfterStudentReviewStart?: boolean = false;

    @Input() resultsPublished?: boolean = false;

    readonly PROGRAMMING: ExerciseType = ExerciseType.PROGRAMMING;

    protected readonly AssessmentType = AssessmentType;
    protected readonly ProgrammingExercise = ProgrammingExercise;
    protected readonly ExerciseType = ExerciseType;

    feedbackComponentParameters: FeedbackComponentPreparedParams;

    constructor(
        private exerciseService: ExerciseService,
        @Optional() private exerciseCacheService: ExerciseCacheService,
    ) {}

    ngOnInit() {
        const isBuilding = false;
        const missingResultInfo = MissingResultInformation.NONE;

        const templateStatus = evaluateTemplateStatus(this.exercise, this.participation, this.participation.results?.[0], isBuilding, missingResultInfo);

        // TODO result may not be defined
        this.feedbackComponentParameters = prepareFeedbackComponentParameters(
            this.exercise,
            this.participation.results![0]!,
            this.participation,
            templateStatus,
            this.exam.latestIndividualEndDate,
            this.exerciseService,
        );
    }
}
