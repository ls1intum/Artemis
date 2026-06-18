import { Component, OnDestroy, OnInit, inject, input, signal, viewChild } from '@angular/core';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result/updating-result.component';
import { Observable, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { ButtonSize } from 'app/shared-ui/components/buttons/button/button.component';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/assessment/shared/entities/feedback.model';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/shared/entities/participation/programming-exercise-student-participation.model';
import { isPracticeMode } from 'app/exercise/shared/entities/participation/student-participation.model';
import { getManualUnreferencedFeedback } from 'app/exercise/result/result.utils';
import { getAllResultsOfAllSubmissions } from 'app/exercise/shared/entities/submission/submission.model';
import { SubmissionPolicyType } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { Course } from 'app/course/shared/entities/course.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { hasExerciseDueDatePassed } from 'app/exercise/util/exercise.utils';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { isManualResult as isManualResultFunction } from 'app/exercise/result/result.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ProgrammingExerciseInstructionComponent } from '../../shared/instructions-render/programming-exercise-instruction.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
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
        AdditionalFeedbackComponent,
    ],
})
export class CodeEditorStudentContainerComponent implements OnInit, OnDestroy {
    private domainService = inject(DomainService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private submissionPolicyService = inject(SubmissionPolicyService);
    private route = inject(ActivatedRoute);

    readonly codeEditorContainer = viewChild(CodeEditorContainerComponent);
    readonly IncludedInOverallScore = IncludedInOverallScore;

    readonly participationId = input<number>();
    readonly lightweight = signal(false);
    readonly SubmissionPolicyType = SubmissionPolicyType;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub: Subscription;
    // Template-read state written in async callbacks (route params subscription + HTTP loads) must be
    // signal-backed under zoneless change detection, otherwise the loaded editor never renders.
    readonly participation = signal<ProgrammingExerciseStudentParticipation>(undefined!);
    readonly exercise = signal<ProgrammingExercise>(undefined!);
    readonly course = signal<Course | undefined>(undefined);

    // Fatal error state: when the participation can't be retrieved, the code editor is unusable for the student
    readonly loadingParticipation = signal(false);
    readonly participationCouldNotBeFetched = signal(false);
    readonly repositoryIsLocked = signal(false);
    // Written only in a template event handler, which schedules change detection itself — may stay plain.
    showEditorInstructions = true;
    readonly latestResult = signal<Result | undefined>(undefined);
    readonly hasTutorAssessment = signal(false);
    readonly numberOfSubmissionsForSubmissionPolicy = signal<number | undefined>(undefined);

    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    /**
     * On init set up the route param subscription.
     * Will load the participation according to participation Id with the latest result and result details.
     */
    ngOnInit(): void {
        this.paramSub = this.route!.params.subscribe((params) => {
            this.loadingParticipation.set(true);
            this.participationCouldNotBeFetched.set(false);
            const participationId = this.participationId() ?? Number(params['participationId']);
            this.loadParticipationWithLatestResult(participationId)
                .pipe(
                    tap((participationWithResults) => {
                        this.domainService.setDomain([DomainType.PARTICIPATION, participationWithResults]);
                        this.participation.set(participationWithResults);
                        const exercise = participationWithResults.exercise as ProgrammingExercise;
                        this.exercise.set(exercise);
                        this.lightweight.set(!exercise?.exerciseGroup);
                        const dueDateHasPassed = hasExerciseDueDatePassed(exercise, participationWithResults);
                        // TODO: load this information from the server in case submission policies are enabled for programming exercises
                        this.repositoryIsLocked.set(dueDateHasPassed && !isPracticeMode(participationWithResults));
                        const allResults = getAllResultsOfAllSubmissions(participationWithResults.submissions);
                        this.latestResult.set(allResults.length >= 1 ? allResults.first() : undefined);
                        this.checkForTutorAssessment(dueDateHasPassed);
                        this.course.set(getCourseFromExercise(exercise));
                        this.submissionPolicyService.getSubmissionPolicyOfProgrammingExercise(exercise.id!).subscribe((submissionPolicy) => {
                            if (submissionPolicy?.active) {
                                this.exercise().submissionPolicy = submissionPolicy;
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
                        this.loadingParticipation.set(false);
                    },
                    error: () => {
                        this.participationCouldNotBeFetched.set(true);
                        this.loadingParticipation.set(false);
                    },
                });
        });
    }

    commit(): void {
        this.codeEditorContainer()?.commit();
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
        const latestResult = this.latestResult();
        if (latestResult) {
            // latest result is the first element of results, see loadParticipationWithLatestResult
            isManualResult = isManualResultFunction(latestResult);
            if (isManualResult) {
                hasTutorFeedback = latestResult.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL);
            }
        }
        // Also check for assessment due date to never show manual feedback before the due date
        this.hasTutorAssessment.set(dueDateHasPassed && isManualResult && hasTutorFeedback);
    }

    getNumberOfSubmissionsForSubmissionPolicy() {
        const participationId = this.participation()?.id;
        if (participationId) {
            this.submissionPolicyService.getParticipationSubmissionCount(participationId).subscribe((numberOfSubmissions) => {
                this.numberOfSubmissionsForSubmissionPolicy.set(numberOfSubmissions);
            });
        }
    }

    /**
     * Check whether a latestResult exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] {
        const latestResult = this.latestResult();
        if (latestResult?.feedbacks) {
            checkSubsequentFeedbackInAssessment(latestResult.feedbacks);
            return getManualUnreferencedFeedback(latestResult.feedbacks) ?? [];
        }
        return [];
    }

    receivedNewResult() {
        this.getNumberOfSubmissionsForSubmissionPolicy();
    }
}
