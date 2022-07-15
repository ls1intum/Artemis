import { Component, OnDestroy, OnInit, ViewChild } from '@angular/core';
import { Observable, of } from 'rxjs';
import { Subscription } from 'rxjs';
import { catchError, mergeMap, map, tap } from 'rxjs/operators';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { codeEditorTour } from 'app/guided-tour/tours/code-editor-tour';
import { ButtonSize } from 'app/shared/components/button.component';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { DomainService } from 'app/exercises/programming/shared/code-editor/service/code-editor-domain.service';
import { ExerciseType, getCourseFromExercise, IncludedInOverallScore } from 'app/entities/exercise.model';
import { AssessmentType } from 'app/entities/assessment-type.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { Result } from 'app/entities/result.model';
import { Feedback, FeedbackType, checkSubsequentFeedbackInAssessment } from 'app/entities/feedback.model';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DomainType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { ActivatedRoute } from '@angular/router';
import { CodeEditorContainerComponent } from 'app/exercises/programming/shared/code-editor/container/code-editor-container.component';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { getUnreferencedFeedback } from 'app/exercises/shared/result/result.utils';
import { SubmissionType } from 'app/entities/submission.model';
import { Participation } from 'app/entities/participation/participation.model';
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
    @ViewChild(CodeEditorContainerComponent, { static: false }) codeEditorContainer: CodeEditorContainerComponent;
    readonly IncludedInOverallScore = IncludedInOverallScore;
    readonly SubmissionPolicyType = SubmissionPolicyType;

    ButtonSize = ButtonSize;
    PROGRAMMING = ExerciseType.PROGRAMMING;

    paramSub: Subscription;
    participation: StudentParticipation;
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

    // Icons
    faCircleNotch = faCircleNotch;
    faTimesCircle = faTimesCircle;

    constructor(
        private resultService: ResultService,
        private domainService: DomainService,
        private programmingExerciseParticipationService: ProgrammingExerciseParticipationService,
        private guidedTourService: GuidedTourService,
        private submissionPolicyService: SubmissionPolicyService,
        private route: ActivatedRoute,
        private alertService: AlertService,
        private exerciseHintService: ExerciseHintService,
    ) {}

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
                        // We lock the repository when the buildAndTestAfterDueDate is set and the due date has passed or if they require manual assessment.
                        // (this should match ProgrammingExerciseParticipation.isLocked on the server-side)
                        const dueDateHasPassed = hasExerciseDueDatePassed(this.exercise, this.participation);
                        const isEditingAfterDueAllowed =
                            !this.exercise.buildAndTestStudentSubmissionsAfterDueDate &&
                            !this.exercise.allowComplaintsForAutomaticAssessments &&
                            this.exercise.assessmentType === AssessmentType.AUTOMATIC;
                        this.repositoryIsLocked = !isEditingAfterDueAllowed && !!this.exercise.dueDate && dueDateHasPassed;
                        this.latestResult = this.participation.results ? this.participation.results[0] : undefined;
                        this.isIllegalSubmission = this.latestResult?.submission?.type === SubmissionType.ILLEGAL;
                        this.checkForTutorAssessment(dueDateHasPassed);
                        this.course = getCourseFromExercise(this.exercise);
                        this.submissionPolicyService.getSubmissionPolicyOfProgrammingExercise(this.exercise.id!).subscribe((submissionPolicy) => {
                            this.exercise.submissionPolicy = submissionPolicy;
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
    loadParticipationWithLatestResult(participationId: number): Observable<StudentParticipation> {
        return this.programmingExerciseParticipationService.getStudentParticipationWithLatestResult(participationId).pipe(
            mergeMap((participation: ProgrammingExerciseStudentParticipation) =>
                participation.results?.length
                    ? this.loadResultDetails(participation, participation.results[0]).pipe(
                          map((feedbacks) => {
                              participation.results![0].feedbacks = feedbacks;
                              return participation;
                          }),
                          catchError(() => of(participation)),
                      )
                    : of(participation),
            ),
        );
    }

    /**
     * Fetches details for the result (if we received one) and attach them to the result.
     * Mutates the input parameter result.
     */
    loadResultDetails(participation: Participation, result: Result): Observable<Feedback[]> {
        return this.resultService.getFeedbackDetailsForResult(participation.id!, result.id!).pipe(
            map((res) => {
                return res.body || [];
            }),
        );
    }

    checkForTutorAssessment(dueDateHasPassed: boolean) {
        let isManualResult = false;
        let hasTutorFeedback = false;
        if (!!this.latestResult) {
            // latest result is the first element of results, see loadParticipationWithLatestResult
            isManualResult = Result.isManualResult(this.latestResult);
            if (isManualResult) {
                hasTutorFeedback = this.latestResult.feedbacks!.some((feedback) => feedback.type === FeedbackType.MANUAL);
            }
        }
        // Also check for assessment due date to never show manual feedback before the deadline
        this.hasTutorAssessment = dueDateHasPassed && isManualResult && hasTutorFeedback;
    }

    /**
     * Check whether or not a latestResult exists and if, returns the unreferenced feedback of it
     */
    get unreferencedFeedback(): Feedback[] {
        if (this.latestResult && this.latestResult.feedbacks) {
            checkSubsequentFeedbackInAssessment(this.latestResult.feedbacks);
            return getUnreferencedFeedback(this.latestResult.feedbacks) ?? [];
        }
        return [];
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
