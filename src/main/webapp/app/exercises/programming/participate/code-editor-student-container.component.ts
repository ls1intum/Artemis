import { Component, OnDestroy, OnInit, ViewChild, inject } from '@angular/core';
import { Observable, Subscription } from 'rxjs';
import { map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { codeEditorTour } from 'app/guided-tour/tours/code-editor-tour';
import { ButtonSize } from 'app/shared/components/button.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ExerciseType, IncludedInOverallScore, getCourseFromExercise } from 'app/entities/exercise.model';
import { Result } from 'app/entities/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { SubmissionType } from 'app/entities/submission.model';
import { SubmissionPolicyType } from 'app/entities/submission-policy.model';
import { Course } from 'app/entities/course.model';
import { SubmissionPolicyService } from 'app/exercises/programming/manage/services/submission-policy.service';
import { hasExerciseDueDatePassed } from 'app/exercises/shared/exercise/exercise.utils';
import { faCircleNotch, faTimesCircle } from '@fortawesome/free-solid-svg-icons';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintService } from 'app/exercises/shared/exercise-hint/shared/exercise-hint.service';
import { HttpResponse } from '@angular/common/http';
import { AlertService } from 'app/core/util/alert.service';

@Component({
    selector: 'jhi-code-editor-student',
    templateUrl: './code-editor-student-container.component.html',
})
export class CodeEditorStudentContainerComponent implements OnInit, OnDestroy {
    private resultService = inject(ResultService);
    private domainService = inject(DomainService);
    private programmingExerciseParticipationService = inject(ProgrammingExerciseParticipationService);
    private guidedTourService = inject(GuidedTourService);
    private submissionPolicyService = inject(SubmissionPolicyService);
    private route = inject(ActivatedRoute);
    private alertService = inject(AlertService);
    private exerciseHintService = inject(ExerciseHintService);

    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly SubmissionPolicyType = SubmissionPolicyType;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub: Subscription;
    participation: ProgrammingExerciseStudentParticipation;
    exercise: ProgrammingExercise;
    course?: Course;

    activatedExerciseHints?: ExerciseHint[];
    availableExerciseHints?: ExerciseHint[];

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
                        this.repositoryIsLocked = this.participation.locked ?? false;
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
                        this.loadStudentExerciseHints();
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
            isManualResult = Result.isManualResult(this.latestResult);
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
            return getUnreferencedFeedback(this.latestResult.feedbacks) ?? [];
        }
        return [];
    }

    receivedNewResult() {
        this.loadStudentExerciseHints();
        this.getNumberOfSubmissionsForSubmissionPolicy();
    }

    loadStudentExerciseHints() {
        this.exerciseHintService.getActivatedExerciseHints(this.exercise.id!).subscribe((activatedRes?: HttpResponse<ExerciseHint[]>) => {
            this.activatedExerciseHints = activatedRes!.body!;

            this.exerciseHintService.getAvailableExerciseHints(this.exercise.id!).subscribe((availableRes?: HttpResponse<ExerciseHint[]>) => {
                // filter out the activated hints from the available hints
                this.availableExerciseHints = availableRes!.body!.filter(
                    (availableHint) => !this.activatedExerciseHints?.some((activatedHint) => availableHint.id === activatedHint.id),
                );
                const filteredAvailableExerciseHints = this.availableExerciseHints.filter((hint) => hint.displayThreshold !== 0);
                if (filteredAvailableExerciseHints.length) {
                    this.alertService.info('artemisApp.exerciseHint.availableHintsAlertMessage', {
                        taskName: filteredAvailableExerciseHints.first()?.programmingExerciseTask?.taskName,
                    });
                }
            });
        });
    }

    onHintActivated(exerciseHint: ExerciseHint) {
        this.availableExerciseHints = this.availableExerciseHints?.filter((hint) => hint.id !== exerciseHint.id);
        this.activatedExerciseHints?.push(exerciseHint);
    }
}
