import { Component, OnInit, inject, input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/entities/programming/programming-submission.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { Exam } from 'app/entities/exam/exam.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus } from 'app/exercises/shared/result/result.utils';
import { FeedbackComponentPreparedParams, prepareFeedbackComponentParameters } from 'app/exercises/shared/feedback/feedback.utils';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ExerciseCacheService } from 'app/exercises/shared/exercise/exercise-cache.service';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { Result } from 'app/entities/result.model';
import { createCommitUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';
import { Router } from '@angular/router';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
import { FeedbackComponent } from 'app/exercises/shared/feedback/feedback.component';
import { ProgrammingExerciseInstructionComponent } from 'app/exercises/programming/shared/instructions-render/programming-exercise-instruction.component';
import { ComplaintsStudentViewComponent } from 'app/complaints/complaints-for-students/complaints-student-view.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-programming-exam-summary',
    templateUrl: './programming-exam-summary.component.html',
    imports: [TranslateDirective, CodeButtonComponent, FeedbackComponent, ProgrammingExerciseInstructionComponent, ComplaintsStudentViewComponent, ArtemisTranslatePipe],
})
export class ProgrammingExamSummaryComponent implements OnInit {
    private exerciseService = inject(ExerciseService);
    private exerciseCacheService = inject(ExerciseCacheService, { optional: true });
    private profileService = inject(ProfileService);
    private router = inject(Router);

    exercise = input.required<ProgrammingExercise>();

    participation = input.required<ProgrammingExerciseStudentParticipation>();

    submission = input.required<ProgrammingSubmission>();

    isTestRun = input(false);

    exam = input.required<Exam>();

    isAfterStudentReviewStart = input(false);

    resultsPublished = input(false);

    isPrinting = input(false);

    isAfterResultsArePublished = input(false);

    instructorView = input(false);

    readonly PROGRAMMING: ExerciseType = ExerciseType.PROGRAMMING;

    protected readonly AssessmentType = AssessmentType;
    protected readonly ProgrammingExercise = ProgrammingExercise;
    protected readonly ExerciseType = ExerciseType;

    result: Result | undefined;

    feedbackComponentParameters: FeedbackComponentPreparedParams;

    commitUrl: string | undefined;
    commitHash: string | undefined;

    routerLink: string;
    localVCEnabled = false;
    isInCourseManagement = false;

    ngOnInit() {
        this.routerLink = this.router.url;
        this.result = this.participation().results?.[0];
        this.commitHash = this.submission()?.commitHash?.slice(0, 11);
        this.isInCourseManagement = this.router.url.includes('course-management');
        const isBuilding = false;
        const missingResultInfo = MissingResultInformation.NONE;

        const templateStatus = evaluateTemplateStatus(this.exercise(), this.participation(), this.participation().results?.[0], isBuilding, missingResultInfo);

        if (this.result) {
            this.feedbackComponentParameters = prepareFeedbackComponentParameters(
                this.exercise(),
                this.result,
                this.participation(),
                templateStatus,
                this.exam().latestIndividualEndDate,
                this.exerciseCacheService ?? this.exerciseService,
            );
        }

        this.updateCommitUrl();
    }

    private updateCommitUrl() {
        // Get active profiles, to distinguish between VC systems for the commit link of the result
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            const commitHashURLTemplate = profileInfo?.commitHashURLTemplate;
            this.commitUrl = createCommitUrl(commitHashURLTemplate, this.exercise().projectKey, this.participation(), this.submission());
            this.localVCEnabled = profileInfo.activeProfiles?.includes(PROFILE_LOCALVC);
        });
    }
}
