import { ChangeDetectorRef, Component, OnInit, ViewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/core/util/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExampleSubmissionService } from 'app/exercises/shared/example-submission/example-submission.service';
import { Result } from 'app/entities/result.model';
import { TutorParticipationService } from 'app/exercises/shared/dashboards/tutor/tutor-participation.service';
import { UMLModel } from '@ls1intum/apollon';
import { ModelingEditorComponent } from 'app/exercises/modeling/shared/modeling-editor.component';
import { ExampleSubmission, ExampleSubmissionMode } from 'app/entities/example-submission.model';
import { Feedback, FeedbackCorrectionError, FeedbackType } from 'app/entities/feedback.model';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ModelingAssessmentService } from 'app/exercises/modeling/assess/modeling-assessment.service';
import { ModelingSubmission } from 'app/entities/modeling-submission.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ModelingAssessmentComponent } from 'app/exercises/modeling/assess/modeling-assessment.component';
import { catchError, concatMap, map, tap } from 'rxjs/operators';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/entities/submission.model';
import { getPositiveAndCappedTotalScore } from 'app/exercises/shared/exercise/exercise.utils';
import { onError } from 'app/shared/util/global.utils';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExampleSubmissionAssessCommand, FeedbackMarker } from 'app/exercises/shared/example-submission/example-submission-assess-command';
import { getCourseFromExercise } from 'app/entities/exercise.model';
import { Course } from 'app/entities/course.model';
import { faChalkboardTeacher, faCheck, faCircle, faCodeBranch, faExclamation, faExclamationTriangle, faInfoCircle, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisNavigationUtilService } from 'app/utils/navigation.utils';
import { forkJoin } from 'rxjs';
import { filterInvalidFeedback } from 'app/exercises/modeling/assess/modeling-assessment.util';
import { Theme, ThemeService } from 'app/core/theme/theme.service';

@Component({
    selector: 'jhi-example-modeling-submission',
    templateUrl: './example-modeling-submission.component.html',
    styleUrls: ['./example-modeling-submission.component.scss'],
})
export class ExampleModelingSubmissionComponent implements OnInit, FeedbackMarker {
    @ViewChild(ModelingEditorComponent, { static: false })
    modelingEditor: ModelingEditorComponent;
    @ViewChild(ModelingAssessmentComponent, { static: false })
    assessmentEditor: ModelingAssessmentComponent;

    isNewSubmission: boolean;
    assessmentMode = false;
    exerciseId: number;
    exampleSubmission: ExampleSubmission;
    modelingSubmission: ModelingSubmission;
    umlModel: UMLModel;
    explanationText: string;
    feedbackChanged = false;
    assessmentsAreValid = false;
    result: Result;
    totalScore: number;
    invalidError?: string;
    exercise: ModelingExercise;
    course?: Course;
    readOnly: boolean;
    toComplete: boolean;
    assessmentExplanation: string;
    isExamMode: boolean;
    selectedMode: ExampleSubmissionMode;
    ExampleSubmissionMode = ExampleSubmissionMode;

    legend = [
        {
            text: 'artemisApp.exampleSubmission.legend.positiveScore',
            icon: faCheck as IconProp,
            color: 'green',
            size: '2em',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.negativeScore',
            icon: faTimes as IconProp,
            color: 'red',
            size: '2em',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.feedbackWithoutScore',
            icon: faExclamation as IconProp,
            color: 'blue',
            size: '1.66em',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.incorrectAssessment',
            icon: faExclamationTriangle as IconProp,
            color: 'yellow',
            size: '2em',
        },
    ];

    private exampleSubmissionId: number;

    referencedFeedback: Feedback[] = [];
    unreferencedFeedback: Feedback[] = [];

    get assessments(): Feedback[] {
        return [...this.referencedFeedback, ...this.unreferencedFeedback];
    }

    highlightedElements = new Map<string, string>();
    referencedExampleFeedback: Feedback[] = [];
    highlightColor = 'lightblue';

    // Icons
    faSave = faSave;
    faCircle = faCircle;
    faInfoCircle = faInfoCircle;
    faExclamation = faExclamation;
    faCodeBranch = faCodeBranch;
    faChalkboardTeacher = faChalkboardTeacher;

    constructor(
        private exerciseService: ExerciseService,
        private exampleSubmissionService: ExampleSubmissionService,
        private modelingAssessmentService: ModelingAssessmentService,
        private tutorParticipationService: TutorParticipationService,
        private alertService: AlertService,
        private route: ActivatedRoute,
        private router: Router,
        private navigationUtilService: ArtemisNavigationUtilService,
        private changeDetector: ChangeDetectorRef,
        private themeService: ThemeService,
    ) {}

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly = !!this.route.snapshot.queryParamMap.get('readOnly');
        this.toComplete = !!this.route.snapshot.queryParamMap.get('toComplete');

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission = true;
            this.exampleSubmissionId = -1;
        } else {
            // (+) converts string 'id' to a number
            this.exampleSubmissionId = +exampleSubmissionId!;
        }

        // if one of the flags is set, we navigated here from the assessment dashboard which means that we are not
        // interested in the modeling editor, i.e. we only want to use the assessment mode
        if (this.readOnly || this.toComplete) {
            this.assessmentMode = true;
        }
        this.loadAll();

        this.themeService.getPreferenceObservable().subscribe((themeOrUndefined) => {
            if (themeOrUndefined === Theme.DARK) {
                this.highlightColor = 'darkblue';
            } else {
                this.highlightColor = 'lightblue';
            }

            const updatedHighlights = new Map<string, string>();
            this.highlightedElements.forEach((value, key) => {
                updatedHighlights.set(key, this.highlightColor);
            });
            this.highlightedElements = updatedHighlights;
        });
    }

    private loadAll(): void {
        let exerciseSource$ = this.exerciseService.find(this.exerciseId);

        if (this.isNewSubmission) {
            this.exampleSubmission = new ExampleSubmission();
            // We don't need to load anything else
        } else {
            const exampleSubmissionSource$ = this.exampleSubmissionService.get(this.exampleSubmissionId).pipe(
                tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    this.exampleSubmission = exampleSubmissionResponse.body!;
                    if (this.exampleSubmission.submission) {
                        this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                        if (this.modelingSubmission.model) {
                            this.umlModel = JSON.parse(this.modelingSubmission.model);
                        }
                        // Updates the explanation text with example modeling submission's explanation
                        this.explanationText = this.modelingSubmission.explanationText ?? '';
                    }

                    if (this.exampleSubmission.usedForTutorial) {
                        this.selectedMode = ExampleSubmissionMode.ASSESS_CORRECTLY;
                    } else {
                        this.selectedMode = ExampleSubmissionMode.READ_AND_CONFIRM;
                    }

                    this.assessmentExplanation = this.exampleSubmission.assessmentExplanation!;

                    if (this.toComplete) {
                        this.modelingAssessmentService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id!).subscribe((result) => {
                            this.updateExampleAssessmentSolution(result);
                        });
                    } else {
                        this.modelingAssessmentService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id!).subscribe((result) => {
                            this.updateAssessment(result);
                            this.checkScoreBoundaries();
                        });
                    }
                }),
            );

            // exampleSubmissionSource$ should set the umlModel before exerciseSource$ sets the exercise in order
            // to prevent ModelingAssessmentComponent from displaying the model as empty due to race condition between
            // two requests.
            exerciseSource$ = forkJoin([exerciseSource$, exampleSubmissionSource$]).pipe(map(([exercise]) => exercise));
        }

        exerciseSource$.subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            this.exercise = exerciseResponse.body!;
            this.course = getCourseFromExercise(this.exercise);
            this.isExamMode = this.exercise.exerciseGroup != undefined;
        });
    }

    upsertExampleModelingSubmission() {
        if (this.isNewSubmission) {
            this.createNewExampleModelingSubmission();
        } else {
            this.updateExampleModelingSubmission().subscribe(() => this.updateAssessmentExplanationAndExampleAssessment());
        }
    }

    private createNewExampleModelingSubmission(): void {
        const modelingSubmission: ModelingSubmission = new ModelingSubmission();
        modelingSubmission.model = JSON.stringify(this.modelingEditor.getCurrentModel());
        modelingSubmission.explanationText = this.explanationText;
        modelingSubmission.exampleSubmission = true;

        const newExampleSubmission: ExampleSubmission = this.exampleSubmission;
        newExampleSubmission.submission = modelingSubmission;
        newExampleSubmission.exercise = this.exercise;

        newExampleSubmission.usedForTutorial = this.selectedMode === ExampleSubmissionMode.ASSESS_CORRECTLY;
        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe({
            next: (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmissionId = this.exampleSubmission.id!;
                if (this.exampleSubmission.submission) {
                    this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel = JSON.parse(this.modelingSubmission.model);
                    }
                    // Updates the explanation text with example modeling submission's explanation
                    this.explanationText = this.modelingSubmission.explanationText ?? '';
                }
                this.isNewSubmission = false;

                this.alertService.success('artemisApp.modelingEditor.saveSuccessful');

                // Update the url with the new id, without reloading the page, to make the history consistent
                this.navigationUtilService.replaceNewWithIdInUrl(window.location.href, this.exampleSubmissionId);
            },
            error: (error: HttpErrorResponse) => {
                onError(this.alertService, error);
            },
        });
    }

    private updateExampleModelingSubmission() {
        if (!this.modelingSubmission) {
            this.createNewExampleModelingSubmission();
        }
        const currentModel = this.modelingEditor.getCurrentModel();
        this.modelingSubmission.model = JSON.stringify(currentModel);

        this.modelingSubmission.explanationText = this.explanationText;
        this.modelingSubmission.exampleSubmission = true;
        if (this.result) {
            this.referencedFeedback = filterInvalidFeedback(this.referencedFeedback, currentModel);
            this.result.feedbacks = this.assessments;
            setLatestSubmissionResult(this.modelingSubmission, this.result);
            delete this.result.submission;
        }

        const exampleSubmission = this.exampleSubmission;
        exampleSubmission.submission = this.modelingSubmission;
        exampleSubmission.exercise = this.exercise;
        exampleSubmission.usedForTutorial = this.selectedMode === ExampleSubmissionMode.ASSESS_CORRECTLY;

        return this.exampleSubmissionService.update(exampleSubmission, this.exerciseId).pipe(
            tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                this.exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmissionId = this.exampleSubmission.id!;
                if (this.exampleSubmission.submission) {
                    this.modelingSubmission = this.exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel = JSON.parse(this.modelingSubmission.model);
                    }
                    if (this.modelingSubmission.explanationText) {
                        this.explanationText = this.modelingSubmission.explanationText;
                    }
                }
                this.isNewSubmission = false;

                this.alertService.success('artemisApp.modelingEditor.saveSuccessful');
            }),
            catchError((error: HttpErrorResponse) => {
                onError(this.alertService, error);
                throw error;
            }),
        );
    }

    onReferencedFeedbackChanged(referencedFeedback: Feedback[]) {
        this.referencedFeedback = referencedFeedback;
        this.feedbackChanged = true;
        this.checkScoreBoundaries();
    }

    onUnReferencedFeedbackChanged(unreferencedFeedback: Feedback[]) {
        this.unreferencedFeedback = unreferencedFeedback;
        this.feedbackChanged = true;
        this.checkScoreBoundaries();
    }

    showAssessment() {
        if (this.modelChanged()) {
            this.updateExampleModelingSubmission().subscribe();
        }
        this.assessmentMode = true;
    }

    private modelChanged(): boolean {
        return this.modelingEditor && JSON.stringify(this.umlModel) !== JSON.stringify(this.modelingEditor.getCurrentModel());
    }

    explanationChanged(explanation: string) {
        this.explanationText = explanation;
    }

    showSubmission() {
        if (this.feedbackChanged) {
            this.saveExampleAssessment();
            this.feedbackChanged = false;
        }
        this.assessmentMode = false;
    }

    public saveExampleAssessment(): void {
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }
        if (this.assessmentExplanation !== this.exampleSubmission.assessmentExplanation && this.assessments) {
            this.updateAssessmentExplanationAndExampleAssessment();
        } else if (this.assessmentExplanation !== this.exampleSubmission.assessmentExplanation) {
            this.updateAssessmentExplanation();
        } else if (this.assessments) {
            this.updateExampleAssessment();
        }
    }

    private updateAssessmentExplanationAndExampleAssessment() {
        this.exampleSubmission.assessmentExplanation = this.assessmentExplanation;
        this.exampleSubmissionService
            .update(this.exampleSubmission, this.exerciseId)
            .pipe(
                tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    this.exampleSubmission = exampleSubmissionResponse.body!;
                    this.assessmentExplanation = this.exampleSubmission.assessmentExplanation!;
                }),
                concatMap(() => this.modelingAssessmentService.saveExampleAssessment(this.assessments, this.exampleSubmissionId)),
            )
            .subscribe({
                next: (result: Result) => {
                    this.updateAssessment(result);
                    this.alertService.success('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
                },
                error: () => {
                    this.alertService.error('artemisApp.modelingAssessmentEditor.messages.saveFailed');
                },
            });
    }

    /**
     * Updates the example submission with the assessment explanation text from the input field if it is different from the explanation already saved with the example submission.
     */
    private updateAssessmentExplanation() {
        this.exampleSubmission.assessmentExplanation = this.assessmentExplanation;
        this.exampleSubmissionService.update(this.exampleSubmission, this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            this.exampleSubmission = exampleSubmissionResponse.body!;
            this.assessmentExplanation = this.exampleSubmission.assessmentExplanation!;
        });
    }

    private updateExampleAssessment() {
        this.modelingAssessmentService.saveExampleAssessment(this.assessments, this.exampleSubmissionId).subscribe({
            next: (result: Result) => {
                this.updateAssessment(result);
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
            },
            error: () => {
                this.alertService.error('artemisApp.modelingAssessmentEditor.messages.saveFailed');
            },
        });
    }

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     */
    public checkScoreBoundaries() {
        if (this.assessments.length === 0) {
            this.totalScore = 0;
            this.assessmentsAreValid = true;
            return;
        }

        const credits = this.assessments.map((feedback) => feedback.credits);
        if (!credits.every((credit) => credit != undefined && !isNaN(credit))) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid = false;
            return;
        }

        const maxPoints = this.exercise.maxPoints! + this.exercise.bonusPoints!;
        const creditsTotalScore = credits.reduce((a, b) => a! + b!, 0)!;
        this.totalScore = getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints);
        this.assessmentsAreValid = true;
        this.invalidError = undefined;
    }

    async back() {
        const courseId = this.exercise.course?.id || this.exercise.exerciseGroup?.exam?.course?.id;
        if (this.readOnly || this.toComplete) {
            await this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
        } else if (this.isExamMode) {
            await this.router.navigate([
                '/course-management',
                courseId,
                'exams',
                this.exercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                this.exercise.exerciseGroup?.id,
                'modeling-exercises',
                this.exerciseId,
                'example-submissions',
            ]);
        } else {
            await this.router.navigate(['/course-management', courseId, 'modeling-exercises', this.exerciseId, 'example-submissions']);
        }
    }

    checkAssessment() {
        // scroll to top that the user definitely recognizes the response message (success OR score too low/high)
        window.scroll(0, 0);
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }

        const exampleSubmission = Object.assign({}, this.exampleSubmission);
        const result = new Result();
        setLatestSubmissionResult(exampleSubmission.submission, result);
        delete result.submission;
        getLatestSubmissionResult(exampleSubmission.submission)!.feedbacks = this.assessments;

        const command = new ExampleSubmissionAssessCommand(this.tutorParticipationService, this.alertService, this);
        command.assessExampleSubmission(exampleSubmission, this.exerciseId);
    }

    markAllFeedbackToCorrect() {
        this.assessments.forEach((feedback) => {
            feedback.correctionStatus = 'CORRECT';
        });
        this.assessmentEditor.resultFeedbacks = this.referencedFeedback;
    }

    markWrongFeedback(correctionErrors: FeedbackCorrectionError[]) {
        correctionErrors.forEach((correctionError) => {
            const validatedFeedback = this.assessments.find((feedback) => feedback.reference === correctionError.reference);
            if (validatedFeedback != undefined) {
                validatedFeedback.correctionStatus = correctionError.type;
            }
        });
        this.assessmentEditor.resultFeedbacks = this.referencedFeedback;
        this.highlightMissedFeedback();
    }

    highlightMissedFeedback() {
        const missedReferencedExampleFeedbacks = this.referencedExampleFeedback.filter(
            (feedback) => !this.referencedFeedback.some((referencedFeedback) => referencedFeedback.reference === feedback.reference),
        );
        this.highlightedElements = new Map<string, string>();
        for (const feedback of missedReferencedExampleFeedbacks) {
            this.highlightedElements.set(feedback.referenceId!, this.highlightColor);
        }
        this.changeDetector.detectChanges();
    }

    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission, this.exerciseId).subscribe(() => {
            this.alertService.success('artemisApp.exampleSubmission.readSuccessfully');
            this.back();
        });
    }

    private updateExampleAssessmentSolution(result: Result) {
        if (result) {
            this.referencedExampleFeedback = result.feedbacks?.filter((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED) || [];
        }
    }

    private updateAssessment(result: Result) {
        this.result = result;
        if (result) {
            this.referencedFeedback = result.feedbacks?.filter((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED) || [];
            this.unreferencedFeedback = result.feedbacks?.filter((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED) || [];
        }
    }
}
