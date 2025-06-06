import { Component, Input, OnInit, inject } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus } from 'app/exercise/result/result.utils';
import { FeedbackComponentPreparedParams, prepareFeedbackComponentParameters } from 'app/exercise/feedback/feedback.utils';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ExerciseCacheService } from 'app/exercise/services/exercise-cache.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CodeButtonComponent } from 'app/shared/components/buttons/code-button/code-button.component';
import { FeedbackComponent } from 'app/exercise/feedback/feedback.component';
import { ProgrammingExerciseInstructionComponent } from 'app/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ComplaintsStudentViewComponent } from 'app/assessment/overview/complaints-for-students/complaints-student-view.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exam-summary',
    templateUrl: './programming-exam-summary.component.html',
    imports: [TranslateDirective, CodeButtonComponent, FeedbackComponent, ProgrammingExerciseInstructionComponent, ComplaintsStudentViewComponent, ArtemisTranslatePipe],
})
export class ProgrammingExamSummaryComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });
    private router = inject(Router);

    @Input() exercise: ProgrammingExercise;
    @Input() participation: ProgrammingExerciseStudentParticipation;
    @Input() submission: ProgrammingSubmission;
    @Input() isTestRun = false;
    @Input() exam: Exam;
    @Input() isAfterStudentReviewStart = false;
    @Input() resultsPublished = false;
    @Input() isPrinting = false;
    @Input() isAfterResultsArePublished = false;
    @Input() instructorView = false;

    readonly PROGRAMMING: ExerciseType = ExerciseType.PROGRAMMING;

    protected readonly AssessmentType = AssessmentType;
    protected readonly ProgrammingExercise = ProgrammingExercise;
    protected readonly ExerciseType = ExerciseType;

    result: Result | undefined;

    feedbackComponentParameters: FeedbackComponentPreparedParams;

    commitHash: string | undefined;

    routerLink: string;
    isInCourseManagement = false;

    ngOnInit() {
        this.routerLink = this.router.url;
        this.participation.exercise = this.exercise;
        this.result = this.participation.submissions![0].results![0];
        // TODO this is not a a perfect solution.
        this.result.submission = this.submission;
        this.result.submission.participation = this.participation;
        this.commitHash = this.submission?.commitHash?.slice(0, 11);
        this.isInCourseManagement = this.router.url.includes('course-management');
        const isBuilding = false;
        const missingResultInfo = MissingResultInformation.NONE;

        const templateStatus = evaluateTemplateStatus(this.exercise, this.participation, this.result, isBuilding, missingResultInfo);

        if (this.result) {
            this.feedbackComponentParameters = prepareFeedbackComponentParameters(
                this.exercise,
                this.result,
                this.participation,
                templateStatus,
                this.exam.latestIndividualEndDate,
                this.exerciseCacheService ?? this.exerciseService,
            );
        }
    }

    get routerLinkForRepositoryView(): (string | number)[] {
        if (this.isInCourseManagement) {
            return ['..', 'programming-exercises', this.exercise.id!, 'repository', 'USER', this.participation.id!];
        }
        if (this.routerLink.includes('test-exam')) {
            const parts = this.routerLink.split('/');
            const examLink = parts.slice(0, parts.length - 2).join('/');
            return [examLink, 'exercises', this.exercise.id!, 'repository', this.participation.id!];
        }
        return [this.routerLink, 'exercises', this.exercise.id!, 'repository', this.participation.id!];
    }
}
