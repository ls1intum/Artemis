import { Component, OnInit, inject } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { HttpResponse } from '@angular/common/http';
import { EntityResponseType, ExampleParticipationService } from 'app/assessment/shared/services/example-participation.service';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { ExampleParticipation, ExampleSubmissionMode } from 'app/exercise/shared/entities/participation/example-participation.model';
import { Feedback, FeedbackCorrectionError, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { TextAssessmentBaseComponent } from 'app/text/manage/assess/assessment-base/text-assessment-base.component';
import { notUndefined } from 'app/shared/util/string-pure.utils';
import { AssessButtonStates, Context, State, SubmissionButtonStates, UIStates } from 'app/text/manage/example-text-submission/example-text-submission-state.model';
import { filter, mergeMap, switchMap, tap } from 'rxjs/operators';
import { ExampleSubmissionAssessCommand, FeedbackMarker } from 'app/exercise/example-submission/example-submission-assess-command';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { faEdit, faSave } from '@fortawesome/free-solid-svg-icons';
import { faListAlt } from '@fortawesome/free-regular-svg-icons';
import { Observable, of } from 'rxjs';
import { ArtemisNavigationUtilService } from 'app/shared/util/navigation.utils';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ConfirmAutofocusButtonComponent } from 'app/shared/components/buttons/confirm-autofocus-button/confirm-autofocus-button.component';
import { ResizeableContainerComponent } from 'app/shared/resizeable-container/resizeable-container.component';
import { ScoreDisplayComponent } from 'app/shared/score-display/score-display.component';
import { TextAssessmentAreaComponent } from 'app/text/manage/assess/text-assessment-area/text-assessment-area.component';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';

type ExampleParticipationResponseType = EntityResponseType;

@Component({
    selector: 'jhi-example-text-submission',
    templateUrl: './example-text-submission.component.html',
    styleUrls: ['./example-text-submission.component.scss'],
    imports: [
        TranslateDirective,
        HelpIconComponent,
        FormsModule,
        FaIconComponent,
        ConfirmAutofocusButtonComponent,
        ResizeableContainerComponent,
        ScoreDisplayComponent,
        TextAssessmentAreaComponent,
        AssessmentInstructionsComponent,
        UnreferencedFeedbackComponent,
        ArtemisTranslatePipe,
    ],
})
export class ExampleTextSubmissionComponent extends TextAssessmentBaseComponent implements OnInit, Context, FeedbackMarker {
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private exampleParticipationService = inject(ExampleParticipationService);
    private tutorParticipationService = inject(TutorParticipationService);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private exerciseService = inject(ExerciseService);

    isNewSubmission: boolean;
    areNewAssessments = true;

    // Is set to true, if there are any changes to the submission.text or exampleParticipation usedForTutorial
    unsavedSubmissionChanges = false;
    private exerciseId: number;
    private exampleParticipationId: number;
    exampleParticipation = new ExampleParticipation();
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
    referencedBlocksInExampleSubmission: string[] = [];

    // Icons
    faSave = faSave;
    faEdit = faEdit;
    farListAlt = faListAlt;

    constructor() {
        super();
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
     * Reads route params and loads the example participation on initialWithContext.
     */
    async ngOnInit(): Promise<void> {
        await super.ngOnInit();
        // (+) converts string 'id' to a number
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleParticipationId = this.route.snapshot.paramMap.get('exampleParticipationId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleParticipationId === 'new') {
            this.isNewSubmission = true;
            this.exampleParticipationId = -1;
        } else {
            this.exampleParticipationId = +exampleParticipationId!;
        }
        this.loadAll();
    }

    /**
     * Loads the exercise.
     * Also loads the example participation if the new parameter is not set.
     */
    private loadAll(): void {
        this.exerciseService.find(this.exerciseId).subscribe((exerciseResponse: HttpResponse<TextExercise>) => {
            this.exercise = exerciseResponse.body!;
        });

        if (this.isNewSubmission) {
            return; // We don't need to load anything else
        }
        this.state.edit();

        this.exampleParticipationService.get(this.exampleParticipationId).subscribe(async (exampleParticipationResponse: HttpResponse<ExampleParticipation>) => {
            this.exampleParticipation = exampleParticipationResponse.body!;
            this.submission = this.exampleParticipationService.getSubmission(this.exampleParticipation) as TextSubmission;
            await this.fetchExampleResult();
            if (this.toComplete) {
                this.state = State.forCompletion(this);
                this.restrictSelectableTextBlocks();
                this.textBlockRefs.forEach((ref) => delete ref.feedback);
                this.validateFeedback();
            } else if (this.result?.id) {
                this.state = State.forExistingAssessmentWithContext(this);
            }
            if (this.exampleParticipation.usedForTutorial) {
                this.selectedMode = ExampleSubmissionMode.ASSESS_CORRECTLY;
            } else {
                this.selectedMode = ExampleSubmissionMode.READ_AND_CONFIRM;
            }
        });
    }

    private restrictSelectableTextBlocks() {
        this.textBlockRefs.forEach((ref) => {
            if (ref.block && this.referencedBlocksInExampleSubmission.includes(ref.block.id!)) {
                ref.selectable = true;
                ref.highlighted = true;
                ref.deletable = false;
            } else {
                ref.selectable = false;
                ref.highlighted = false;
                ref.deletable = true;
            }
        });
    }

    private fetchExampleResult(): Promise<void> {
        return new Promise((resolve) => {
            this.assessmentsService
                // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
                .getExampleResult(this.exerciseId, this.submission?.id!)
                .pipe(filter(notUndefined))
                .subscribe((result) => {
                    if (result && result.id) {
                        this.result = result;
                        this.submission = result.submission;
                        if (this.exampleParticipation.submissions) {
                            this.exampleParticipation.submissions[0] = this.submission!;
                        } else {
                            this.exampleParticipation.submissions = [this.submission!];
                        }
                        this.updateExampleAssessmentSolution(result);
                    } else {
                        if (result && !result.id) {
                            this.updateExampleAssessmentSolution(result);
                        }
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
     * Creates the example participation.
     */
    createNewExampleTextSubmission(): void {
        const newExampleParticipation = new ExampleParticipation();
        newExampleParticipation.submissions = [this.submission!];
        newExampleParticipation.exercise = this.exercise;
        newExampleParticipation.usedForTutorial = this.selectedMode === ExampleSubmissionMode.ASSESS_CORRECTLY;

        this.exampleParticipationService.create(newExampleParticipation, this.exerciseId).subscribe({
            next: (exampleParticipationResponse: HttpResponse<ExampleParticipation>) => {
                this.exampleParticipation = exampleParticipationResponse.body!;
                this.exampleParticipation.exercise = this.exercise;
                this.exampleParticipationId = this.exampleParticipation.id!;
                this.submission = this.exampleParticipationService.getSubmission(this.exampleParticipation) as TextSubmission;
                this.isNewSubmission = false;
                this.unsavedSubmissionChanges = false;
                this.state.edit();

                // Update the url with the new id, without reloading the page, to make the history consistent
                this.navigationUtilService.replaceNewWithIdInUrl(window.location.href, this.exampleParticipationId);

                this.alertService.success('artemisApp.exampleSubmission.submitSuccessful');
            },
            error: this.alertService.error,
        });
    }

    /**
     * Updates the example participation.
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

    saveSubmissionIfNeeded(): Observable<ExampleParticipationResponseType> {
        // If there are no unsaved changes, no need for server call
        if (!this.unsavedSubmissionChanges) {
            return of({} as ExampleParticipationResponseType);
        }

        return this.exampleParticipationService.update(this.exampleParticipationForNetwork(), this.exerciseId).pipe(
            tap((exampleParticipationResponse) => {
                this.exampleParticipation = exampleParticipationResponse.body!;
                this.unsavedSubmissionChanges = false;
            }),
        );
    }

    public async startAssessment(): Promise<void> {
        this.exampleParticipationService
            .prepareForAssessment(this.exerciseId, this.exampleParticipationId)
            .pipe(
                mergeMap(() => {
                    return this.exampleParticipationService.get(this.exampleParticipationId);
                }),
            )
            .subscribe((exampleParticipationResponse: HttpResponse<ExampleParticipation>) => {
                this.exampleParticipation = exampleParticipationResponse.body!;
                this.submission = this.exampleParticipationService.getSubmission(this.exampleParticipation) as TextSubmission;

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
            .pipe(switchMap(() => this.assessmentsService.saveExampleAssessment(this.exerciseId, this.exampleParticipation.id!, this.assessments, this.textBlocksWithFeedback)))
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
        if (this.exercise?.exerciseGroup) {
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
     * Checks the assessment of the tutor to the example participation tutorial.
     * The tutor is informed if its assessment is different from the one of the instructor.
     */
    checkAssessment(): void {
        this.validateFeedback();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.textAssessment.error.invalidAssessments');
            return;
        }

        const command = new ExampleSubmissionAssessCommand(this.tutorParticipationService, this.alertService, this);
        command.assessExampleParticipation(this.exampleParticipationForNetwork(), this.exerciseId);
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

    private exampleParticipationForNetwork() {
        const exampleParticipation = Object.assign({}, this.exampleParticipation);
        const submission = Object.assign({}, this.submission);
        exampleParticipation.submissions = [submission];

        if (this.result) {
            const result = Object.assign({}, this.result);
            setLatestSubmissionResult(submission, result);
            result.feedbacks = this.assessments;
            delete result?.submission;
        } else {
            delete submission.results;
            delete submission.latestResult;
        }

        return exampleParticipation;
    }

    /**
     * Validate the feedback of the assessment
     */
    validateFeedback(): void {
        this.assessmentsAreValid = this.assessments.length > 0;
        this.totalScore = this.computeTotalScore(this.assessments);
    }

    /**
     * After the tutor declared that he read and understood the example participation a corresponding submission will be added to the
     * tutor participation of the exercise. Then a success alert is invoked and the user gets redirected back.
     */
    readAndUnderstood(): void {
        this.tutorParticipationService.assessExampleParticipation(this.exampleParticipation, this.exerciseId).subscribe(() => {
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
        // eslint-disable-next-line @typescript-eslint/no-non-null-asserted-optional-chain
        this.assessmentsService.deleteExampleAssessment(this.exercise!.id!, this.exampleParticipation?.id!).subscribe(() => {
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
        this.exampleParticipation.usedForTutorial = mode === ExampleSubmissionMode.ASSESS_CORRECTLY;
    }

    private updateExampleAssessmentSolution(result: Result) {
        if (result && result.feedbacks) {
            this.referencedBlocksInExampleSubmission =
                result.feedbacks.filter((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED && feedback.reference).map((feedback) => feedback.reference!) || [];
        }
    }
}
