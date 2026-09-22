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
import { EMPTY, Observable, Subject, defer, forkJoin, of } from 'rxjs';
import { isEqual } from 'lodash-es';
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
import { takeUntilDestroyed, toSignal } from '@angular/core/rxjs-interop';
import { ModelingAssessmentTopLeftDirective } from 'app/modeling/manage/assess/modeling-assessment-top-left.directive';
import { ModelingAssessmentTopRightDirective } from 'app/modeling/manage/assess/modeling-assessment-top-right.directive';
import { ModelingAssessmentLegendComponent, ModelingAssessmentLegendHighlight } from 'app/modeling/manage/assess/modeling-assessment-legend/modeling-assessment-legend.component';

interface AssessmentSave {
    feedbacks: Feedback[];
    assessmentExplanation: string;
    usedForTutorial: boolean;
}

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
    private readonly savedAssessments = signal<Feedback[]>([]);
    readonly feedbackChanged = computed(() => !isEqual(this.assessments(), this.savedAssessments()));
    private readonly saveRequests = new Subject<() => Observable<unknown>>();
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

        if (!this.hasValidFeedbackScores(feedbacks)) {
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

    constructor() {
        // Defer constructing whole-submission requests until preceding writes have updated the persisted state.
        this.saveRequests
            .pipe(
                concatMap((save) =>
                    defer(save).pipe(
                        catchError((error: HttpErrorResponse) => {
                            onError(this.alertService, error);
                            return EMPTY;
                        }),
                    ),
                ),
                takeUntilDestroyed(),
            )
            .subscribe();
    }

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
        this.queueModelSave(true);
    }

    private captureAssessmentSave(): AssessmentSave {
        return deepClone({
            feedbacks: this.assessments(),
            assessmentExplanation: this.assessmentExplanation(),
            usedForTutorial: this.selectedMode() === ExampleSubmissionMode.ASSESS_CORRECTLY,
        });
    }

    private queueModelSave(saveAssessment: boolean): void {
        // Capture the editor before a mode switch destroys it, and isolate queued intent from subsequent edits.
        const model = deepClone(this.modelingEditor()?.getCurrentModel());
        if (!model) {
            return;
        }
        const explanation = this.explanationText();
        const assessment = this.captureAssessmentSave();
        this.saveRequests.next(() =>
            this.saveModel(model, explanation, assessment).pipe(
                concatMap((created) => {
                    if (created) {
                        return EMPTY;
                    }
                    this.pruneAssessment(assessment, model);
                    return saveAssessment || !isEqual(assessment.feedbacks, this.savedAssessments()) ? this.saveAssessment(assessment) : EMPTY;
                }),
            ),
        );
    }

    private saveModel(model: UMLModel, explanation: string, assessment: AssessmentSave): Observable<boolean> {
        const creating = this.isNewSubmission() || !this.modelingSubmission;
        const submission = deepClone(this.modelingSubmission ?? new ModelingSubmission());
        submission.model = JSON.stringify(model);
        submission.explanationText = explanation;
        submission.exampleSubmission = true;
        const exampleSubmission = deepClone(this.exampleSubmission());
        exampleSubmission.submission = submission;
        exampleSubmission.exercise = this.exercise();
        exampleSubmission.assessmentExplanation = assessment.assessmentExplanation;
        exampleSubmission.usedForTutorial = assessment.usedForTutorial;
        const request = creating
            ? this.exampleSubmissionService.create(exampleSubmission, this.exerciseId)
            : this.exampleSubmissionService.update(exampleSubmission, this.exerciseId);
        return request.pipe(
            tap((response) => {
                this.acceptSavedSubmission(response.body!);
                this.isNewSubmission.set(false);
                this.alertService.success('artemisApp.modelingEditor.saveSuccessful');
                if (creating) {
                    this.navigationUtilService.replaceNewWithIdInUrl(window.location.href, this.exampleSubmissionId);
                }
            }),
            map(() => creating),
        );
    }

    private acceptSavedSubmission(exampleSubmission: ExampleSubmission): void {
        this.exampleSubmission.set(exampleSubmission);
        this.exampleSubmissionId = exampleSubmission.id!;
        if (exampleSubmission.submission) {
            this.modelingSubmission = exampleSubmission.submission;
        }
    }

    onReferencedFeedbackChanged(referencedFeedback: Feedback[]) {
        this.referencedFeedback.set([...referencedFeedback]);
    }

    onUnReferencedFeedbackChanged(unreferencedFeedback: Feedback[]) {
        this.unreferencedFeedback.set([...unreferencedFeedback]);
    }

    showAssessment() {
        if (this.modelChanged()) {
            this.queueModelSave(false);
        }
        // Keep the current canvas separate from persisted state, including when a save is still pending or failed.
        const model = this.modelingEditor()?.getCurrentModel();
        if (model) {
            this.umlModel.set(deepClone(model));
        }
        this.assessmentMode.set(true);
    }

    private modelChanged(): boolean {
        const modelingEditor = this.modelingEditor();
        return !!modelingEditor && this.modelingSubmission?.model !== JSON.stringify(modelingEditor.getCurrentModel());
    }

    explanationChanged(explanation: string) {
        this.explanationText.set(explanation);
    }

    showSubmission() {
        if (this.feedbackChanged()) {
            this.saveExampleAssessment();
        }
        this.assessmentMode.set(false);
    }

    public saveExampleAssessment(): void {
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }
        const assessment = this.captureAssessmentSave();
        this.saveRequests.next(() => this.saveAssessment(assessment));
    }

    private hasValidFeedbackScores(feedbacks: Feedback[]): boolean {
        return feedbacks.every(({ credits }) => credits != undefined && !isNaN(credits));
    }

    private saveAssessment(assessment: AssessmentSave): Observable<Result> {
        // A model saved earlier in the queue may have deleted references this intent still contains.
        if (this.modelingSubmission?.model) {
            this.pruneAssessment(assessment, importDiagram(parseJson(this.modelingSubmission.model)));
        }
        if (!this.hasValidFeedbackScores(assessment.feedbacks)) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return EMPTY;
        }
        const current = this.exampleSubmission();
        let metadataSave: Observable<unknown> = of(undefined);
        if (assessment.assessmentExplanation !== current.assessmentExplanation || assessment.usedForTutorial !== !!current.usedForTutorial) {
            const updated = deepClone(current);
            updated.assessmentExplanation = assessment.assessmentExplanation;
            updated.usedForTutorial = assessment.usedForTutorial;
            metadataSave = this.exampleSubmissionService.update(updated, this.exerciseId).pipe(tap((response) => this.acceptSavedSubmission(response.body!)));
        }
        return metadataSave.pipe(
            concatMap(() => this.modelingAssessmentService.saveExampleAssessment(deepClone(assessment.feedbacks), this.exampleSubmissionId)),
            tap((result) => {
                if (isEqual(this.assessments(), assessment.feedbacks)) {
                    this.updateAssessment(result);
                } else {
                    this.result.set(result);
                    this.rememberSavedAssessments(result.feedbacks ?? []);
                }
                this.alertService.success('artemisApp.modelingAssessmentEditor.messages.saveSuccessful');
            }),
            catchError(() => {
                this.alertService.error('artemisApp.modelingAssessmentEditor.messages.saveFailed');
                return EMPTY;
            }),
        );
    }

    private pruneAssessment(assessment: AssessmentSave, model: UMLModel | undefined): void {
        const referenced = filterInvalidFeedback(
            assessment.feedbacks.filter((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED),
            model,
        );
        const feedbacks = [...referenced, ...assessment.feedbacks.filter((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED)];
        // Prune live feedback only for its current model; an older response must not remove feedback for newer elements.
        const currentModel = this.modelingEditor()?.getCurrentModel() ?? this.umlModel();
        if (!currentModel || isEqual(currentModel, model)) {
            const currentReferenced = this.referencedFeedback();
            const validCurrentReferenced = filterInvalidFeedback(currentReferenced, model);
            if (validCurrentReferenced.length !== currentReferenced.length) {
                this.referencedFeedback.set(validCurrentReferenced);
            }
        }
        assessment.feedbacks = feedbacks;
    }

    private rememberSavedAssessments(feedbacks: Feedback[]): void {
        // The server may interleave feedback kinds, while assessments() always lists referenced feedback first.
        this.savedAssessments.set(
            deepClone([
                ...feedbacks.filter((feedback) => feedback.type !== FeedbackType.MANUAL_UNREFERENCED),
                ...feedbacks.filter((feedback) => feedback.type === FeedbackType.MANUAL_UNREFERENCED),
            ]),
        );
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
            this.rememberSavedAssessments(result.feedbacks ?? []);
            this.referencedFeedback.set(result.feedbacks?.filter((f) => f.type !== FeedbackType.MANUAL_UNREFERENCED) || []);
            this.unreferencedFeedback.set(result.feedbacks?.filter((f) => f.type === FeedbackType.MANUAL_UNREFERENCED) || []);
        }
    }
}
