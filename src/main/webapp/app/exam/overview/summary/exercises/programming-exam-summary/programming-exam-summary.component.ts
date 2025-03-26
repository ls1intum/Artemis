import { Component, Input, OnInit, inject } from '@angular/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseType } from 'app/entities/exercise.model';
import { MissingResultInformation, evaluateTemplateStatus } from 'app/exercise/result/result.utils';
import { FeedbackComponentPreparedParams, prepareFeedbackComponentParameters } from 'app/exercise/feedback/feedback.utils';
import { ExerciseService } from 'app/exercise/exercise.service';
import { ExerciseCacheService } from 'app/exercise/exercise-cache.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Result } from 'app/entities/result.model';
import { createCommitUrl } from 'app/programming/shared/utils/programming-exercise.utils';
import { Router } from '@angular/router';
import { PROFILE_LOCALVC } from 'app/app.constants';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CodeButtonComponent } from 'app/shared/components/code-button/code-button.component';
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
    private profileService = inject(ProfileService);
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

    commitUrl: string | undefined;
    commitHash: string | undefined;

    routerLink: string;
    localVCEnabled = true;
    isInCourseManagement = false;

    ngOnInit() {
        this.routerLink = this.router.url;
        this.result = this.participation.results?.[0];
        this.commitHash = this.submission?.commitHash?.slice(0, 11);
        this.isInCourseManagement = this.router.url.includes('course-management');
        const isBuilding = false;
        const missingResultInfo = MissingResultInformation.NONE;

        const templateStatus = evaluateTemplateStatus(this.exercise, this.participation, this.participation.results?.[0], isBuilding, missingResultInfo);

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

        this.updateCommitUrl();
    }

    private updateCommitUrl() {
        // Get active profiles, to distinguish between VC systems for the commit link of the result
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            const commitHashURLTemplate = profileInfo?.commitHashURLTemplate;
            this.commitUrl = createCommitUrl(commitHashURLTemplate, this.exercise.projectKey, this.participation, this.submission);
            this.localVCEnabled = profileInfo.activeProfiles?.includes(PROFILE_LOCALVC);
        });
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
