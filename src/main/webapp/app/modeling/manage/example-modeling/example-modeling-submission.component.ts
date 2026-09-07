import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { UMLModel, importDiagram } from '@tumaet/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ExampleSubmission, ExampleSubmissionMode } from 'app/assessment/shared/entities/example-submission.model';
import { Feedback, FeedbackCorrectionError, FeedbackCorrectionStatus, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { catchError, concatMap, map, tap } from 'rxjs/operators';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { StructuredGradingCriterionService } from 'app/exercise/structured-grading-criterion/structured-grading-criterion.service';
import { onError } from 'app/foundation/util/global.utils';
import { parseJson } from 'app/foundation/util/json.util';
import { ExampleSubmissionAssessCommand, FeedbackMarker } from 'app/exercise/example-submission/example-submission-assess-command';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { faCheck, faClipboardCheck, faSave, faShapes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { forkJoin } from 'rxjs';
import { filterInvalidFeedback } from 'app/modeling/manage/assess/modeling-assessment.util';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { AssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/assessment-instructions/assessment-instructions.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { ModelingEditorTopLeftDirective } from 'app/modeling/shared/modeling-editor/modeling-editor-top-left.directive';
import { ScoreDisplayComponent } from 'app/exercise/score-display/score-display.component';
import { AssessmentWorkspaceComponent } from 'app/assessment/manage/assessment-workspace/assessment-workspace.component';
import { TumUiButtonDirective, TumUiInputDirective, TumUiSelectButtonComponent } from '@tumaet/ui-angular';
import { CdkTextareaAutosize } from '@angular/cdk/text-field';
import { TranslateService } from '@ngx-translate/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { ModelingAssessmentTopLeftDirective } from 'app/modeling/manage/assess/modeling-assessment-top-left.directive';
import { ModelingAssessmentTopRightDirective } from 'app/modeling/manage/assess/modeling-assessment-top-right.directive';
import { ModelingAssessmentLegendComponent, ModelingAssessmentLegendHighlight } from 'app/modeling/manage/assess/modeling-assessment-legend/modeling-assessment-legend.component';

@Component({
    selector: 'jhi-example-modeling-submission',
    templateUrl: './example-modeling-submission.component.html',
    styleUrls: ['./example-modeling-submission.component.scss'],
    imports: [
        TranslateDirective,
        FormsModule,
        FaIconComponent,
        ModelingEditorComponent,
        ModelingAssessmentComponent,
        UnreferencedFeedbackComponent,
        AssessmentInstructionsComponent,
        ArtemisTranslatePipe,
        TumUiButtonDirective,
        TumUiSelectButtonComponent,
        ModelingEditorTopLeftDirective,
        ScoreDisplayComponent,
        AssessmentWorkspaceComponent,
        TumUiInputDirective,
        CdkTextareaAutosize,
        ModelingAssessmentTopLeftDirective,
        ModelingAssessmentTopRightDirective,
        ModelingAssessmentLegendComponent,
    ],
})
export class ExampleModelingSubmissionComponent implements OnInit, FeedbackMarker {
    private exerciseService = inject(ExerciseService);
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private tutorParticipationService = inject(TutorParticipationService);
    private structuredGradingCriterionService = inject(StructuredGradingCriterionService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private navigationUtilService = inject(ArtemisNavigationUtilService);
    private artemisTranslatePipe = inject(ArtemisTranslatePipe);
    private translateService = inject(TranslateService);
    private readonly languageChange = toSignal(this.translateService.onLangChange, { initialValue: undefined });

    readonly modelingEditor = viewChild(ModelingEditorComponent);
    readonly assessmentEditor = viewChild(ModelingAssessmentComponent);

    readonly isNewSubmission = signal(false);
    readonly assessmentMode = signal(false);
    exerciseId!: number;
    readonly exampleSubmission = signal<ExampleSubmission>(undefined!);
    modelingSubmission!: ModelingSubmission;
    readonly umlModel = signal<UMLModel>(undefined!);
    readonly explanationText = signal<string>(undefined!);
    feedbackChanged = false;
    readonly result = signal<Result>(undefined!);
    readonly exercise = signal<ModelingExercise>(undefined!);
    readonly course = signal<Course | undefined>(undefined);
    readonly readOnly = signal<boolean>(undefined!);
    readonly toComplete = signal<boolean>(undefined!);
    readonly assessmentExplanation = signal<string>(undefined!);
    isExamMode = false;
    readonly selectedMode = signal<ExampleSubmissionMode>(undefined!);
    ExampleSubmissionMode = ExampleSubmissionMode;

    readonly legendHighlights = computed<ModelingAssessmentLegendHighlight[]>(() =>
        this.highlightedElements().size > 0 ? [{ color: this.highlightColor, text: 'artemisApp.modelingAssessment.legend.incorrectAssessment' }] : [],
    );

    protected readonly trainingModeOptions = computed(() => {
        this.languageChange();
        return [
            {
                label: this.artemisTranslatePipe.transform('artemisApp.exampleSubmission.readAndConfirm'),
                value: ExampleSubmissionMode.READ_AND_CONFIRM,
            },
            {
                label: this.artemisTranslatePipe.transform('artemisApp.exampleSubmission.assessCorrectly'),
                value: ExampleSubmissionMode.ASSESS_CORRECTLY,
            },
        ];
    });

    private exampleSubmissionId!: number;
    referencedFeedback = signal<Feedback[]>([]);
    unreferencedFeedback = signal<Feedback[]>([]);

    assessments = computed(() => [...this.referencedFeedback(), ...this.unreferencedFeedback()]);

    readonly getTotalMaxPoints = getTotalMaxPoints;

    private readonly scoreState = computed<{ valid: boolean; totalScore?: number; error?: string }>(() => {
        const feedbacks = this.assessments();
        if (feedbacks.length === 0) {
            return { valid: true, totalScore: 0 };
        }

        const credits = feedbacks.map((feedback) => feedback.credits);
        if (!credits.every((credit) => credit != undefined && !isNaN(credit))) {
            return { valid: false, error: 'The score field must be a number and can not be empty!' };
        }

        // Structured grading usage limits make raw credit sums incorrect.
        return { valid: true, totalScore: this.structuredGradingCriterionService.computeAssessmentScore(feedbacks, getTotalMaxPoints(this.exercise())).total };
    });

    readonly assessmentsAreValid = computed(() => this.scoreState().valid);
    readonly totalScore = computed(() => this.scoreState().totalScore);
    readonly invalidError = computed(() => this.scoreState().error);

    highlightedElements = signal<Map<string, string>>(new Map<string, string>());
    referencedExampleFeedback: Feedback[] = [];
    readonly highlightColor = 'color-mix(in srgb, var(--tumaet-ui-primary-color) 35%, transparent)';

    faSave = faSave;
    faCheck = faCheck;
    faShapes = faShapes;
    faClipboardCheck = faClipboardCheck;

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly.set(!!this.route.snapshot.queryParamMap.get('readOnly'));
        this.toComplete.set(!!this.route.snapshot.queryParamMap.get('toComplete'));

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission.set(true);
            this.exampleSubmissionId = -1;
        } else {
            this.exampleSubmissionId = Number(exampleSubmissionId);
        }

        if (this.readOnly() || this.toComplete()) {
            this.assessmentMode.set(true);
        }
        this.loadAll();
    }

    private loadAll(): void {
        let exerciseSource$ = this.exerciseService.find(this.exerciseId);

        if (this.isNewSubmission()) {
            this.exampleSubmission.set(new ExampleSubmission());
        } else {
            const exampleSubmissionSource$ = this.exampleSubmissionService.get(this.exampleSubmissionId).pipe(
                tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    const exampleSubmission = exampleSubmissionResponse.body!;
                    this.exampleSubmission.set(exampleSubmission);
                    if (exampleSubmission.submission) {
                        this.modelingSubmission = exampleSubmission.submission;
                        if (this.modelingSubmission.model) {
                            this.umlModel.set(importDiagram(parseJson(this.modelingSubmission.model)));
                        }
                        this.explanationText.set(this.modelingSubmission.explanationText ?? '');
                    }

                    if (exampleSubmission.usedForTutorial) {
                        this.selectedMode.set(ExampleSubmissionMode.ASSESS_CORRECTLY);
                    } else {
                        this.selectedMode.set(ExampleSubmissionMode.READ_AND_CONFIRM);
                    }

                    this.assessmentExplanation.set(exampleSubmission.assessmentExplanation!);

                    this.modelingAssessmentService.getExampleAssessment(this.exerciseId, this.modelingSubmission.id!).subscribe((result) => {
                        if (this.toComplete()) {
                            // Practice assessment: the instructor's assessment is the solution the tutor is graded against,
                            // so it is kept aside for the "missed feedback" hint and never shown as the tutor's own.
                            this.updateExampleAssessmentSolution(result);
                        } else {
                            this.updateAssessment(result);
                        }
                    });
                }),
            );

            exerciseSource$ = forkJoin([exerciseSource$, exampleSubmissionSource$]).pipe(map(([exercise]) => exercise));
        }

        exerciseSource$.subscribe((exerciseResponse: HttpResponse<ModelingExercise>) => {
            const exercise = exerciseResponse.body!;
            this.exercise.set(exercise);
            this.course.set(getCourseFromExercise(exercise));
            this.isExamMode = exercise.exerciseGroup != undefined;
        });
    }

    upsertExampleModelingSubmission() {
        if (this.isNewSubmission()) {
            this.createNewExampleModelingSubmission();
        } else {
            this.updateExampleModelingSubmission().subscribe(() => this.updateAssessmentExplanationAndExampleAssessment());
        }
    }

    private createNewExampleModelingSubmission(): void {
        const modelingSubmission: ModelingSubmission = new ModelingSubmission();
        modelingSubmission.model = JSON.stringify(this.modelingEditor()?.getCurrentModel());
        modelingSubmission.explanationText = this.explanationText();
        modelingSubmission.exampleSubmission = true;

        const newExampleSubmission: ExampleSubmission = this.exampleSubmission();
        newExampleSubmission.submission = modelingSubmission;
        newExampleSubmission.exercise = this.exercise();

        newExampleSubmission.usedForTutorial = this.selectedMode() === ExampleSubmissionMode.ASSESS_CORRECTLY;
        this.exampleSubmissionService.create(newExampleSubmission, this.exerciseId).subscribe({
            next: (exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                const exampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmission.set(exampleSubmission);
                this.exampleSubmissionId = exampleSubmission.id!;
                if (exampleSubmission.submission) {
                    this.modelingSubmission = exampleSubmission.submission;
                    if (this.modelingSubmission.model) {
                        this.umlModel.set(importDiagram(parseJson(this.modelingSubmission.model)));
                    }
                    this.explanationText.set(this.modelingSubmission.explanationText ?? '');
                }
                this.isNewSubmission.set(false);

                this.alertService.success('artemisApp.modelingEditor.saveSuccessful');

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
        const currentModel = this.modelingEditor()?.getCurrentModel();
        this.modelingSubmission.model = JSON.stringify(currentModel);

        this.modelingSubmission.explanationText = this.explanationText();
        this.modelingSubmission.exampleSubmission = true;
        const result = this.result();
        if (result) {
            this.referencedFeedback.set(filterInvalidFeedback(this.referencedFeedback(), currentModel));
            result.feedbacks = this.assessments();
            setLatestSubmissionResult(this.modelingSubmission, result);
            delete result.submission;
        }

        const exampleSubmission = this.exampleSubmission();
        exampleSubmission.submission = this.modelingSubmission;
        exampleSubmission.exercise = this.exercise();
        exampleSubmission.usedForTutorial = this.selectedMode() === ExampleSubmissionMode.ASSESS_CORRECTLY;

        return this.exampleSubmissionService.update(exampleSubmission, this.exerciseId).pipe(
            tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                const updatedExampleSubmission = exampleSubmissionResponse.body!;
                this.exampleSubmission.set(updatedExampleSubmission);
                this.exampleSubmissionId = updatedExampleSubmission.id!;
                if (updatedExampleSubmission.submission) {
                    this.modelingSubmission = updatedExampleSubmission.submission;
                    if (this.modelingSubmission.model) {
                        this.umlModel.set(importDiagram(parseJson(this.modelingSubmission.model)));
                    }
                    if (this.modelingSubmission.explanationText) {
                        this.explanationText.set(this.modelingSubmission.explanationText);
                    }
                }
                this.isNewSubmission.set(false);

                this.alertService.success('artemisApp.modelingEditor.saveSuccessful');
            }),
            catchError((error: HttpErrorResponse) => {
                onError(this.alertService, error);
                throw error;
            }),
        );
    }

    onReferencedFeedbackChanged(referencedFeedback: Feedback[]) {
        this.referencedFeedback.set(referencedFeedback);
        this.feedbackChanged = true;
    }

    onUnReferencedFeedbackChanged(unreferencedFeedback: Feedback[]) {
        this.unreferencedFeedback.set(unreferencedFeedback);
        this.feedbackChanged = true;
    }

    showAssessment() {
        if (this.modelChanged()) {
            this.updateExampleModelingSubmission().subscribe();
        }
        this.assessmentMode.set(true);
    }

    private modelChanged(): boolean {
        const modelingEditor = this.modelingEditor();
        return !!modelingEditor && JSON.stringify(this.umlModel()) !== JSON.stringify(modelingEditor.getCurrentModel());
    }

    explanationChanged(explanation: string) {
        this.explanationText.set(explanation);
    }

    showSubmission() {
        if (this.feedbackChanged) {
            this.saveExampleAssessment();
            this.feedbackChanged = false;
        }
        this.assessmentMode.set(false);
    }

    public saveExampleAssessment(): void {
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }
        if (this.assessmentExplanation() !== this.exampleSubmission().assessmentExplanation) {
            this.updateAssessmentExplanationAndExampleAssessment();
        } else {
            this.updateExampleAssessment();
        }
    }

    private updateAssessmentExplanationAndExampleAssessment() {
        this.exampleSubmission().assessmentExplanation = this.assessmentExplanation();
        this.applySelectedModeToExampleSubmission();
        this.exampleSubmissionService
            .update(this.exampleSubmission(), this.exerciseId)
            .pipe(
                tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    const exampleSubmission = exampleSubmissionResponse.body!;
                    this.exampleSubmission.set(exampleSubmission);
                    this.assessmentExplanation.set(exampleSubmission.assessmentExplanation!);
                }),
                concatMap(() => this.modelingAssessmentService.saveExampleAssessment(this.assessments(), this.exampleSubmissionId)),
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

    private applySelectedModeToExampleSubmission(): void {
        this.exampleSubmission().usedForTutorial = this.selectedMode() === ExampleSubmissionMode.ASSESS_CORRECTLY;
    }

    private updateExampleAssessment() {
        if (this.exampleSubmission().usedForTutorial !== (this.selectedMode() === ExampleSubmissionMode.ASSESS_CORRECTLY)) {
            this.updateAssessmentExplanationAndExampleAssessment();
            return;
        }
        this.modelingAssessmentService.saveExampleAssessment(this.assessments(), this.exampleSubmissionId).subscribe({
            next: (result: Result) => {
                this.updateAssessment(result);
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
            },
            error: () => {
                this.alertService.error('artemisApp.modelingAssessmentEditor.messages.saveFailed');
            },
        });
    }

    async back() {
        const exercise = this.exercise();
        const courseId = exercise.course?.id || exercise.exerciseGroup?.exam?.course?.id;
        if (this.readOnly() || this.toComplete()) {
            await this.router.navigate(['/course-management', courseId, 'assessment-dashboard', this.exerciseId]);
        } else if (this.isExamMode) {
            await this.router.navigate([
                '/course-management',
                courseId,
                'exams',
                exercise.exerciseGroup?.exam?.id,
                'exercise-groups',
                exercise.exerciseGroup?.id,
                'modeling-exercises',
                this.exerciseId,
                'example-submissions',
            ]);
        } else {
            await this.router.navigate(['/course-management', courseId, 'modeling-exercises', this.exerciseId, 'example-submissions']);
        }
    }

    checkAssessment() {
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }

        const exampleSubmission = deepClone(this.exampleSubmission());
        const result = new Result();
        setLatestSubmissionResult(exampleSubmission.submission, result);
        delete result.submission;
        getLatestSubmissionResult(exampleSubmission.submission)!.feedbacks = this.assessments();

        const command = new ExampleSubmissionAssessCommand(this.tutorParticipationService, this.alertService, this);
        command.assessExampleSubmission(exampleSubmission, this.exerciseId);
    }

    markAllFeedbackToCorrect() {
        this.applyCorrectionStatus(() => 'CORRECT');
        this.highlightMissedFeedback();
    }

    markWrongFeedback(correctionErrors: FeedbackCorrectionError[]) {
        const byReference = new Map(correctionErrors.map((err) => [err.reference, err]));
        this.applyCorrectionStatus((feedback) => byReference.get(feedback.reference!)?.type);

        this.highlightMissedFeedback();
    }

    /** Replaces both signal arrays while preserving referenced feedback identities used by the canvas. */
    private applyCorrectionStatus(statusFor: (feedback: Feedback) => FeedbackCorrectionStatus | undefined) {
        this.referencedFeedback.update((feedbacks) => {
            for (const feedback of feedbacks) {
                const status = statusFor(feedback);
                if (status) {
                    feedback.correctionStatus = status;
                }
            }
            return [...feedbacks];
        });

        this.unreferencedFeedback.update((feedbacks) =>
            feedbacks.map((feedback) => {
                const status = statusFor(feedback);
                if (!status) {
                    return feedback;
                }
                const marked = deepClone(feedback);
                marked.correctionStatus = status;
                return marked;
            }),
        );
    }

    highlightMissedFeedback() {
        const missedReferencedExampleFeedbacks = this.referencedExampleFeedback.filter(
            (feedback) => !this.referencedFeedback().some((referencedFeedback) => referencedFeedback.reference === feedback.reference),
        );
        const highlightedElements = new Map<string, string>();
        for (const feedback of missedReferencedExampleFeedbacks) {
            highlightedElements.set(feedback.referenceId!, this.highlightColor);
        }
        this.highlightedElements.set(highlightedElements);
    }

    readAndUnderstood() {
        this.tutorParticipationService.assessExampleSubmission(this.exampleSubmission(), this.exerciseId).subscribe(() => {
            this.alertService.success('artemisApp.exampleSubmission.readSuccessfully');
            void this.back();
        });
    }

    private updateExampleAssessmentSolution(result: Result) {
        if (result) {
            this.referencedExampleFeedback = result.feedbacks?.filter((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED) || [];
        }
    }

    private updateAssessment(result: Result) {
        this.result.set(result);
        if (result) {
            this.referencedFeedback.set(result.feedbacks?.filter((f) => f.type !== FeedbackType.MANUAL_UNREFERENCED) || []);
            this.unreferencedFeedback.set(result.feedbacks?.filter((f) => f.type === FeedbackType.MANUAL_UNREFERENCED) || []);
        }
    }
}
