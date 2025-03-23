import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { IncludedInScoreBadgeComponent } from 'app/exercise/exercise-headers/included-in-score-badge.component';
import { UpdatingResultComponent } from 'app/exercise/result/updating-result.component';
import { Observable, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { GuidedTourService } from 'app/core/guided-tour/guided-tour.service';
import { codeEditorTour } from 'app/core/guided-tour/tours/code-editor-tour';
import { ButtonSize } from 'app/shared/components/button.component';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/exercise/entities/exercise.model';
import { Result } from 'app/exercise/entities/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/programming/manage/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/exercise/entities/participation/programming-exercise-student-participation.model';
import { getManualUnreferencedFeedback } from 'app/exercise/result/result.utils';
import { SubmissionType } from 'app/exercise/entities/submission.model';
import { SubmissionPolicyType } from 'app/exercise/entities/submission-policy.model';
import { Course } from 'app/entities/course.model';
import { SubmissionPolicyService } from 'app/programming/manage/services/submission-policy.service';
import { hasExerciseDueDatePassed } from 'app/exercise/exercise.utils';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { isManualResult as isManualResultFunction } from 'app/exercise/result/result.utils';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingExerciseInstructionComponent } from '../shared/instructions-render/programming-exercise-instruction.component';
import { AdditionalFeedbackComponent } from 'app/exercise/additional-feedback/additional-feedback.component';
import { CodeEditorRepositoryIsLockedComponent } from 'app/programming/shared/code-editor/layout/code-editor-repository-is-locked.component';
import { ProgrammingExerciseStudentTriggerBuildButtonComponent } from 'app/programming/shared/actions/programming-exercise-student-trigger-build-button.component';
import { DomainService } from 'app/programming/shared/code-editor/service/code-editor-domain.service';
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
    private guidedTourService = inject(GuidedTourService);
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
    isIllegalSubmission = false;
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
                        this.latestResult = this.participation.results ? this.participation.results[0] : undefined;
                        this.isIllegalSubmission = this.latestResult?.submission?.type === SubmissionType.ILLEGAL;
                        this.checkForTutorAssessment(dueDateHasPassed);
                        this.course = getCourseFromExercise(this.exercise);
                        this.submissionPolicyService.getSubmissionPolicyOfProgrammingExercise(this.exercise.id!).subscribe((submissionPolicy) => {
                            if (submissionPolicy?.active) {
                                this.exercise.submissionPolicy = submissionPolicy;
                                this.getNumberOfSubmissionsForSubmissionPolicy();
                            }
                        });
                        if (this.participation.results && this.participation.results[0] && this.participation.results[0].feedbacks) {
                            checkSubsequentFeedbackInAssessment(this.participation.results[0].feedbacks);
                        }
                    }),
                )
                .subscribe({
                    next: () => {
                        this.loadingParticipation = false;
                        this.guidedTourService.enableTourForExercise(this.exercise, codeEditorTour, true);
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
                if (participation.results?.length) {
                    // connect result and participation
                    participation.results[0].participation = participation;
                }
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
