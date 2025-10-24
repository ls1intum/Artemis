import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { Observable, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ButtonSize } from 'app/shared/components/buttons/button/button.component';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { getManualUnreferencedFeedback } from 'app/exercise/result/result.utils';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { isManualResult as isManualResultFunction } from 'app/exercise/result/result.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructionComponent } from '../../shared/instructions-render/programming-exercise-instruction.component';
import { UnifiedFeedbackComponent } from 'app/shared/components/unified-feedback/unified-feedback.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/trigger-build-button/student/programming-exercise-student-trigger-build-button.component';
import { DomainService } from 'app/programming/shared/code-editor/services/code-editor-domain.service';
import { DomainType } from 'app/programming/shared/code-editor/model/code-editor.model';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-student-container.component.html',
    imports: [
        FaIconComponent,
        TranslateDirective,
        CodeEditorContainerComponent,
        IncludedInScoreBadgeComponent,
        CodeEditorRepositoryIsLockedComponent,
        UpdatingResultComponent,
        ProgrammingExerciseStudentTriggerBuildButtonComponent,
        ProgrammingExerciseInstructionComponent,
        UnifiedFeedbackComponent,
    ],
})
export class CodeEditorStudentContainerComponent implements OnInit, OnDestroy {
    private domainService = inject(DomainService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private submissionPolicyService = inject(SubmissionPolicyService);
    private route = inject(ActivatedRoute);

    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly SubmissionPolicyType = SubmissionPolicyType;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    course?: Course;

    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    loadingParticipation = false;
    participationCouldNotBeFetched = false;
    repositoryIsLocked = false;
    showEditorInstructions = true;
    latestResult: Result | undefined;
    hasTutorAssessment = false;
    numberOfSubmissionsForSubmissionPolicy?: number;

    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        this.paramSub = this.route!.params.subscribe((params) => {
            this.loadingParticipation = true;
            this.participationCouldNotBeFetched = false;
            const participationId = Number(params['participationId']);
            this.loadParticipationWithLatestResult(participationId)
                .pipe(
                    tap((participationWithResults) => {
                        this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                        this.participation = participationWithResults;
                        this.exercise = this.participation.exercise as ProgrammingExercise;
                        const dueDateHasPassed = hasExerciseDueDatePassed(this.exercise, this.participation);
                        this.repositoryIsLocked = false; // TODO: load this information dynamically from the server
                        const allResults = getAllResultsOfAllSubmissions(this.participation.submissions);
                        this.latestResult = allResults.length >= 1 ? allResults.first() : undefined;
                        this.checkForTutorAssessment(dueDateHasPassed);
                        this.course = getCourseFromExercise(this.exercise);
                        this.submissionPolicyService.getSubmissionPolicyOfProgrammingExercise(this.exercise.id!).subscribe((submissionPolicy) => {
                            if (submissionPolicy?.active) {
                                this.exercise.submissionPolicy = submissionPolicy;
                                this.getNumberOfSubmissionsForSubmissionPolicy();
                            }
                        });
                        if (allResults && allResults[0] && allResults[0].feedbacks) {
                            checkSubsequentFeedbackInAssessment(allResults[0].feedbacks);
                        }
                    }),
                )
                .subscribe({
                    next: () => {
                        this.loadingParticipation = false;
                    },
                    error: () => {
                        this.participationCouldNotBeFetched = true;
                        this.loadingParticipation = false;
                    },
                });
        });
    }

    /**
     * If a subscription exists for paramSub, unsubscribe
     */
    ngOnDestroy() {
        if (this.paramSub) {
            this.paramSub.unsubscribe();
        }
    }

    /**
     * Load the participation from server with the latest result.
     * @param participationId
     */
    loadParticipationWithLatestResult(participationId: number): Observable<ProgrammingExerciseStudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            map((participation: ProgrammingExerciseStudentParticipation) => {
                return participation;
            }),
        );
    }

    checkForTutorAssessment(dueDateHasPassed: boolean) {
        let isManualResult = false;
        let hasTutorFeedback = false;
        if (this.latestResult) {
            // latest result is the first element of results, see loadParticipationWithLatestResult
            isManualResult = isManualResultFunction(this.latestResult);
            if (isManualResult) {
                hasTutorFeedback = this.latestResult.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL);
            }
        }
        // Also check for assessment due date to never show manual feedback before the due date
        this.hasTutorAssessment = dueDateHasPassed && isManualResult && hasTutorFeedback;
    }

    getNumberOfSubmissionsForSubmissionPolicy() {
        if (this.participation.id) {
            this.submissionPolicyService.getParticipationSubmissionCount(this.participation.id).subscribe((numberOfSubmissions) => {
                this.numberOfSubmissionsForSubmissionPolicy = numberOfSubmissions;
            });
        }
    }

    /**
     * Check whether a latestResult exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] {
        if (this.latestResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.latestResult.feedbacks);
            return getManualUnreferencedFeedback(this.latestResult.feedbacks) ?? [];
        }
        return [];
    }

    receivedNewResult() {
        this.getNumberOfSubmissionsForSubmissionPolicy();
    }
}
