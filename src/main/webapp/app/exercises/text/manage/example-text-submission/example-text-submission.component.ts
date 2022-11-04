import { ChangeDetectorRef, Component, OnInit } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';
import { AlertService } from 'app/core/util/alert.service';
import { HttpResponse } from '@angular/common/http';
import { EntityResponseType, ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { TextAssessmentService } from 'app/exercises/text/assess/text-assessment.service';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { ArtemisMarkdownService } from 'app/shared/markdown.service';
import { AccountService } from 'app/core/auth/account.service';
import { GuidedTourService } from 'app/guided-tour/guided-tour.service';
import { tutorAssessmentTour } from 'app/guided-tour/tours/tutor-assessment-tour';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ExampleSubmission, ExampleSubmissionMode } from 'app/entities/example-submission.model';
import { Feedback, FeedbackCorrectionError, FeedbackType } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ResultService } from 'app/exercises/shared/result/result.service';
import { TextExercise } from 'app/entities/text-exercise.model';
import { TextSubmission } from 'app/entities/text-submission.model';
import { Result } from 'app/entities/result.model';
import { setLatestSubmissionResult } from 'app/entities/submission.model';
import { TextAssessmentBaseComponent } from 'app/exercises/text/assess/text-assessment-base.component';
import { StructuredGradingCriterionService } from 'app/exercises/shared/structured-grading-criterion/structured-grading-criterion.service';
import { notUndefined } from 'app/shared/util/global.utils';
import { AssessButtonStates, Context, State, SubmissionButtonStates, UIStates } from 'app/exercises/text/manage/example-text-submission/example-text-submission-state.model';
import { filter, mergeMap, switchMap, tap } from 'rxjs/operators';
import { ExampleSubmissionAssessCommand, FeedbackMarker } from 'app/exercises/shared/example-submission/example-submission-assess-command';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { Observable, of } from 'rxjs';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';

type ExampleSubmissionResponseType = EntityResponseType;

@Component({
    selector: 'jhi-example-text-submission',
    templateUrl: './example-text-submission.component.html',
    styleUrls: ['./example-text-submission.component.scss'],
})
export class ExampleTextSubmissionComponent extends TextAssessmentBaseComponent implements OnInit, Context, FeedbackMarker {
    isNewSubmission: boolean;
    areNewAssessments = true;

    // Is set to true, if there are any changes to the submission.text or exampleSubmissionusedForTutorial
    unsavedSubmissionChanges = false;
    private exerciseId: number;
    private exampleSubmissionId: number;
    exampleSubmission = new ExampleSubmission();
    assessmentsAreValid = false;
    result?: Result;
    unreferencedFeedback: Feedback[] = [];
    totalScore: number;
    readOnly: boolean;
    toComplete: boolean;
    state = State.initialWithContext(this);
    SubmissionButtonStates = SubmissionButtonStates;
    AssessButtonStates = AssessButtonStates;
    UIStates = UIStates;
    selectedMode: ExampleSubmissionMode;
    ExampleSubmissionMode = ExampleSubmissionMode;

    // Icons
    faSave = faSave;
    faEdit = faEdit;
    farListAlt = faListAlt;

    constructor(
        alertService: AlertService,
        accountService: AccountService,
        assessmentsService: TextAssessmentService,
        structuredGradingCriterionService: StructuredGradingCriterionService,
        private cdr: ChangeDetectorRef,
        private exerciseService: ExerciseService,
        private textSubmissionService: TextSubmissionService,
        private exampleSubmissionService: ExampleSubmissionService,
        private tutorParticipationService: TutorParticipationService,
        private route: ActivatedRoute,
        private router: Router,
        private location: Location,
        private artemisMarkdown: ArtemisMarkdownService,
        private resultService: ResultService,
        private guidedTourService: GuidedTourService,
        private navigationUtilService: ArtemisNavigationUtilService,
    ) {
        super(alertService, accountService, assessmentsService, structuredGradingCriterionService);
        this.textBlockRefs = [];
        this.unusedTextBlockRefs = [];
        this.submission = new TextSubmission();
    }

    private get referencedFeedback(): Feedback[] {
        return this.textBlockRefs.map(({ feedback }) => feedback).filter(notUndefined) as Feedback[];
    }

    private get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    /**
     * Reads route params and loads the example submission on initialWithContext.
     */
    async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        // (+) converts string 'id' to a number
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            this.exampleSubmissionId = +exampleSubmissionId!;
        }
        this.loadAll();
    }

    /**
     * Loads the exercise.
     * Also loads the example submission if the new parameter is not set.
     */
    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<TextExercise>) => {
            this.exercise = exerciseResponse.body!;
            this.guidedTourService.enableTourForExercise(this.exercise, tutorAssessmentTour, false);
        });

        if (this.isNewSubmission) {
            return; // We don't need to load anything else
        }
        this.state.edit();

        this.exampleSubmissionService.get(this.exampleSubmissionId).subscribe(async (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.submission = this.exampleSubmission.submission as TextSubmission;
            await this.fetchExampleResult();
            if (this.toComplete) {
                this.state = State.forCompletion(this);
                this.textBlockRefs.forEach((ref) => delete ref.feedback);
                this.validateFeedback();
            } else if (this.result?.id) {
                this.state = State.forExistingAssessmentWithContext(this);
            }
            // do this here to make sure everything is loaded before the guided tour step is loaded
            this.guidedTourService.componentPageLoaded();
            if (this.exampleSubmission.usedForTutorial) {
                this.selectedMode = ExampleSubmissionMode.ASSESS_CORRECTLY;
            } else {
                this.selectedMode = ExampleSubmissionMode.READ_AND_CONFIRM;
            }
        });
    }

    private fetchExampleResult(): Promise<void> {
        return new Promise((resolve) => {
            this.assessmentsService
                .getExampleResult(this.exerciseId, this.submission?.id!)
                .pipe(filter(notUndefined))
                .subscribe((result) => {
                    if (result) {
                        this.result = result;
                        this.exampleSubmission.submission = this.submission = result.submission;
                    } else {
                        this.result = new Result();
                        this.result.submission = this.submission;
                        this.submission!.results = [this.result];
                    }
                    this.prepareTextBlocksAndFeedbacks();
                    this.areNewAssessments = this.assessments.length <= 0;
                    this.validateFeedback();
                    resolve();
                });
        });
    }

    /**
     * Creates the example submission.
     */
    createNewExampleTextSubmission(): void {
        const newExampleSubmission = new ExampleSubmission();
        newExampleSubmission.submission = this.submission!;
        newExampleSubmission.exercise = this.exercise;
        newExampleSubmission.usedForTutorial = this.selectedMode === ExampleSubmissionMode.ASSESS_CORRECTLY;

        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe({
            next: (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmission.exercise = this.exercise;
                this.exampleSubmissionId = this.exampleSubmission.id!;
                this.submission = this.exampleSubmission.submission as TextSubmission;
                this.isNewSubmission = false;
                this.unsavedSubmissionChanges = false;
                this.state.edit();

                // Update the url with the new id, without reloading the page, to make the history consistent
                this.navigationUtilService.replaceNewWithIdInUrl(window.location.href, this.exampleSubmissionId);

                this.alertService.success('artemisApp.exampleSubmission.submitSuccessful');
            },
            error: this.alertService.error,
        });
    }

    /**
     * Updates the example submission.
     */
    updateExampleTextSubmission(): void {
        this.saveSubmissionIfNeeded().subscribe({
            next: () => {
                this.state.edit();
                this.alertService.success('artemisApp.exampleSubmission.saveSuccessful');
            },
            error: this.alertService.error,
        });
    }

    saveSubmissionIfNeeded(): Observable<ExampleSubmissionResponseType> {
        // If there are no unsaved changes, no need for server call
        if (!this.unsavedSubmissionChanges) {
            return of({} as ExampleSubmissionResponseType);
        }

        return this.exampleSubmissionService.update(this.exampleSubmissionForNetwork(), this.exerciseId).pipe(
            tap((exampleSubmissionResponse) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.unsavedSubmissionChanges = false;
            }),
        );
    }

    public async startAssessment(): Promise<void> {
        this.exampleSubmissionService
            .prepareForAssessment(this.exerciseId, this.exampleSubmissionId)
            .pipe(
                mergeMap(() => {
                    return this.exampleSubmissionService.get(this.exampleSubmissionId);
                }),
            )
            .subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.submission = this.exampleSubmission.submission as TextSubmission;

                this.result = new Result();
                this.result.submission = this.submission;
                this.submission!.results = [this.result];
                this.prepareTextBlocksAndFeedbacks();
                this.areNewAssessments = this.assessments.length <= 0;
                this.validateFeedback();
                this.state.assess();
            });
    }

    /**
     * Checks if the score boundaries have been respected and save the assessment.
     */
    public saveAssessments(): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        this.saveSubmissionIfNeeded()
            .pipe(switchMap(() => this.assessmentsService.saveExampleAssessment(this.exerciseId, this.exampleSubmission.id!, this.assessments, this.textBlocksWithFeedback)))
            .subscribe({
                next: (response) => {
                    this.result = response.body!;
                    this.areNewAssessments = false;
                    this.state.assess();
                    this.alertService.success('artemisApp.textAssessment.saveSuccessful');
                },
                error: this.alertService.error,
            });
    }

    /**
     * Redirects back to the assessment dashboard if route param readOnly or toComplete is set.
     * Otherwise redirects back to the exercise's edit view either for exam exercises or normal exercises.
     */
    async back(): Promise<void> {
        const courseId = getCourseFromExercise(this.exercise!)?.id;
        // check if exam exercise
        if (!!this.exercise?.exerciseGroup) {
            const examId = this.exercise.exerciseGroup.exam?.id;
            const exerciseGroupId = this.exercise.exerciseGroup.id;
            if (this.readOnly || this.toComplete) {
                await this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
            } else {
                await this.router.navigate([
                    '/course-management',
                    courseId,
                    'exams',
                    examId,
                    'exercise-groups',
                    exerciseGroupId,
                    'text-exercises',
                    this.exerciseId,
                    'example-submissions',
                ]);
            }
        } else {
            if (this.readOnly || this.toComplete) {
                this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
            } else {
                this.router.navigate(['/course-management', courseId, 'text-exercises', this.exerciseId, 'example-submissions']);
            }
        }
    }

    /**
     * Checks the assessment of the tutor to the example submission tutorial.
     * The tutor is informed if its assessment is different from the one of the instructor.
     */
    checkAssessment(): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        const command = new ExampleSubmissionAssessCommand(this.tutorParticipationService, this.alertService, this);
        command.assessExampleSubmission(this.exampleSubmissionForNetwork(), this.exerciseId);
    }

    /**
     * Mark all referenced and unreferenced feedback as 'CORRECT'
     */
    markAllFeedbackToCorrect() {
        this.textBlockRefs
            .map((ref) => ref.feedback)
            .filter((feedback) => feedback != undefined)
            .concat(this.unreferencedFeedback)
            .forEach((feedback) => {
                feedback!.correctionStatus = 'CORRECT';
            });
    }

    markWrongFeedback(correctionErrors: FeedbackCorrectionError[]) {
        correctionErrors.forEach((correctionError) => {
            const textBlockRef = this.textBlockRefs.find((ref) => ref.feedback?.reference === correctionError.reference);
            if (textBlockRef != undefined && textBlockRef.feedback != undefined) {
                textBlockRef.feedback.correctionStatus = correctionError.type;
            } else {
                const unreferencedFeedbackToBeMarked = this.unreferencedFeedback.find((feedback) => feedback.reference === correctionError.reference);
                if (unreferencedFeedbackToBeMarked) {
                    unreferencedFeedbackToBeMarked.correctionStatus = correctionError.type;
                }
            }
        });
    }

    private exampleSubmissionForNetwork() {
        const exampleSubmission = Object.assign({}, this.exampleSubmission);
        exampleSubmission.submission = Object.assign({}, this.submission);

        if (this.result) {
            const result = Object.assign({}, this.result);
            setLatestSubmissionResult(exampleSubmission.submission, result);
            result.feedbacks = this.assessments;
            delete result?.submission;
        } else {
            delete exampleSubmission.submission.results;
            delete exampleSubmission.submission.latestResult;
        }

        return exampleSubmission;
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        this.assessmentsAreValid = this.assessments.length > 0;
        this.totalScore = this.computeTotalScore(this.assessments);

        if (this.guidedTourService.currentTour && this.toComplete) {
            this.guidedTourService.updateAssessmentResult(this.assessments.length, this.totalScore);
        }
    }

    /**
     * After the tutor declared that he read and understood the example submission a corresponding submission will be added to the
     * tutor participation of the exercise. Then a success alert is invoked and the user gets redirected back.
     */
    readAndUnderstood(): void {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(() => {
            this.alertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }

    private prepareTextBlocksAndFeedbacks() {
        const matchBlocksWithFeedbacks = TextAssessmentService.matchBlocksWithFeedbacks(this.submission?.blocks || [], this.result?.feedbacks || []);
        this.sortAndSetTextBlockRefs(matchBlocksWithFeedbacks, this.textBlockRefs, this.unusedTextBlockRefs, this.submission);

        if (!this.toComplete) {
            this.unreferencedFeedback = this.result?.feedbacks?.filter((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED) ?? [];
        }
    }

    editSubmission(): void {
        this.assessmentsService.deleteExampleAssessment(this.exercise!.id!, this.exampleSubmission?.id!).subscribe(() => {
            delete this.submission?.blocks;
            if (this.submission && this.submission.results) {
                this.submission.results = undefined;
                this.submission.latestResult = undefined;
            }
            this.result = undefined;
            this.textBlockRefs = [];
            this.unusedTextBlockRefs = [];
            this.state.edit();
        });
    }

    onModeChange(mode: ExampleSubmissionMode) {
        this.selectedMode = mode;
        this.unsavedSubmissionChanges = true;
        this.exampleSubmission.usedForTutorial = mode === ExampleSubmissionMode.ASSESS_CORRECTLY;
    }
}
