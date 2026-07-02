import { Component, OnInit, computed, inject, signal, viewChild } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { AlertService } from 'app/foundation/service/alert.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExampleSubmissionService } from 'app/assessment/shared/services/example-submission.service';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { UMLModel, importDiagram } from '@tumaet/apollon';
import { ModelingEditorComponent } from 'app/modeling/shared/modeling-editor/modeling-editor.component';
import { ExampleSubmission, ExampleSubmissionMode } from 'app/assessment/shared/entities/example-submission.model';
import { Feedback, FeedbackCorrectionError, FeedbackType } from 'app/assessment/shared/entities/feedback.model';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ModelingAssessmentComponent } from 'app/modeling/manage/assess/modeling-assessment.component';
import { UnreferencedFeedbackComponent } from 'app/exercise/unreferenced-feedback/unreferenced-feedback.component';
import { catchError, concatMap, map, tap } from 'rxjs/operators';
import { getLatestSubmissionResult, setLatestSubmissionResult } from 'app/exercise/shared/entities/submission/submission.model';
import { getPositiveAndCappedTotalScore, getTotalMaxPoints } from 'app/exercise/util/exercise.utils';
import { onError } from 'app/foundation/util/global.utils';
import { parseJson } from 'app/foundation/util/json.util';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { ExampleSubmissionAssessCommand, FeedbackMarker } from 'app/exercise/example-submission/example-submission-assess-command';
import { getCourseFromExercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Course } from 'app/course/shared/entities/course.model';
import { faChalkboardTeacher, faCheck, faCircle, faCodeBranch, faExclamation, faExclamationTriangle, faInfoCircle, faSave, faTimes } from '@fortawesome/free-solid-svg-icons';
import { ArtemisNavigationUtilService } from 'app/foundation/util/navigation.utils';
import { forkJoin } from 'rxjs';
import { filterInvalidFeedback } from 'app/modeling/manage/assess/modeling-assessment.util';
import { scrollToTopOfPage } from 'app/foundation/util/utils';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CollapsableAssessmentInstructionsComponent } from 'app/assessment/manage/assessment-instructions/collapsable-assessment-instructions/collapsable-assessment-instructions.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { TutorParticipationService } from 'app/assessment/shared/assessment-dashboard/exercise-dashboard/tutor-participation.service';

@Component({
    selector: 'jhi-example-modeling-submission',
    templateUrl: './example-modeling-submission.component.html',
    styleUrls: ['./example-modeling-submission.component.scss'],
    imports: [
        TranslateDirective,
        HelpIconComponent,
        FormsModule,
        FaIconComponent,
        ModelingEditorComponent,
        ModelingAssessmentComponent,
        UnreferencedFeedbackComponent,
        CollapsableAssessmentInstructionsComponent,
        ArtemisTranslatePipe,
    ],
})
export class ExampleModelingSubmissionComponent implements OnInit, FeedbackMarker {
    private exerciseService = inject(ExerciseService);
    private exampleSubmissionService = inject(ExampleSubmissionService);
    private modelingAssessmentService = inject(ModelingAssessmentService);
    private tutorParticipationService = inject(TutorParticipationService);
    private alertService = inject(AlertService);
    private route = inject(ActivatedRoute);
    private router = inject(Router);
    private navigationUtilService = inject(ArtemisNavigationUtilService);

    readonly modelingEditor = viewChild(ModelingEditorComponent);
    readonly assessmentEditor = viewChild(ModelingAssessmentComponent);

    readonly isNewSubmission = signal(false);
    readonly assessmentMode = signal(false);
    exerciseId: number;
    readonly exampleSubmission = signal<ExampleSubmission>(undefined!);
    modelingSubmission: ModelingSubmission;
    readonly umlModel = signal<UMLModel>(undefined!);
    readonly explanationText = signal<string>(undefined!);
    feedbackChanged = false;
    readonly assessmentsAreValid = signal(false);
    readonly result = signal<Result>(undefined!);
    readonly totalScore = signal<number>(undefined!);
    invalidError?: string;
    readonly exercise = signal<ModelingExercise>(undefined!);
    readonly course = signal<Course | undefined>(undefined);
    readonly readOnly = signal<boolean>(undefined!);
    readonly toComplete = signal<boolean>(undefined!);
    readonly assessmentExplanation = signal<string>(undefined!);
    isExamMode: boolean;
    readonly selectedMode = signal<ExampleSubmissionMode>(undefined!);
    ExampleSubmissionMode = ExampleSubmissionMode;

    legend = [
        {
            text: 'artemisApp.exampleSubmission.legend.positiveScore',
            icon: faCheck as IconProp,
            color: 'green',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.negativeScore',
            icon: faTimes as IconProp,
            color: 'red',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.feedbackWithoutScore',
            icon: faExclamation as IconProp,
            color: 'blue',
        },
        {
            text: 'artemisApp.exampleSubmission.legend.incorrectAssessment',
            icon: faExclamationTriangle as IconProp,
            color: 'yellow',
        },
    ];

    private exampleSubmissionId: number;
    referencedFeedback = signal<Feedback[]>([]);
    unreferencedFeedback = signal<Feedback[]>([]);

    assessments = computed(() => [...this.referencedFeedback(), ...this.unreferencedFeedback()]);

    highlightedElements = signal<Map<string, string>>(new Map<string, string>());
    referencedExampleFeedback: Feedback[] = [];
    // Apollon paints the highlight as an HTML overlay div (inline background/box-shadow), so a CSS token
    // resolves: a translucent tint of Artemis's primary keeps element text readable and re-resolves on
    // theme toggle for free (primary already lightens in dark).
    readonly highlightColor = 'color-mix(in srgb, var(--p-primary-color) 35%, transparent)';

    // Icons
    faSave = faSave;
    faCircle = faCircle;
    faInfoCircle = faInfoCircle;
    faCodeBranch = faCodeBranch;
    faChalkboardTeacher = faChalkboardTeacher;

    ngOnInit(): void {
        this.exerciseId = Number(this.route.snapshot.paramMap.get('exerciseId'));
        const exampleSubmissionId = this.route.snapshot.paramMap.get('exampleSubmissionId');
        this.readOnly.set(!!this.route.snapshot.queryParamMap.get('readOnly'));
        this.toComplete.set(!!this.route.snapshot.queryParamMap.get('toComplete'));

        if (exampleSubmissionId === 'new') {
            this.isNewSubmission.set(true);
            this.exampleSubmissionId = -1;
        } else {
            // (+) converts string 'id' to a number
            this.exampleSubmissionId = +exampleSubmissionId!;
        }

        // if one of the flags is set, we navigated here from the assessment dashboard which means that we are not
        // interested in the modeling editor, i.e. we only want to use the assessment mode
        if (this.readOnly() || this.toComplete()) {
            this.assessmentMode.set(true);
        }
        this.loadAll();
    }

    private loadAll(): void {
        let exerciseSource$ = this.exerciseService.find(this.exerciseId);

        if (this.isNewSubmission()) {
            this.exampleSubmission.set(new ExampleSubmission());
            // We don't need to load anything else
        } else {
            const exampleSubmissionSource$ = this.exampleSubmissionService.get(this.exampleSubmissionId).pipe(
                tap((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
                    const exampleSubmission = exampleSubmissionResponse.body!;
                    this.exampleSubmission.set(exampleSubmission);
                    if (exampleSubmission.submission) {
                        this.modelingSubmission = exampleSubmission.submission as ModelingSubmission;
                        if (this.modelingSubmission.model) {
                            this.umlModel.set(importDiagram(parseJson(this.modelingSubmission.model)));
                        }
                        // Updates the explanation text with example modeling submission's explanation
                        this.explanationText.set(this.modelingSubmission.explanationText ?? '');
                    }

                    if (exampleSubmission.usedForTutorial) {
                        this.selectedMode.set(ExampleSubmissionMode.ASSESS_CORRECTLY);
                    } else {
                        this.selectedMode.set(ExampleSubmissionMode.READ_AND_CONFIRM);
                    }

                    this.assessmentExplanation.set(exampleSubmission.assessmentExplanation!);

                    if (this.toComplete()) {
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
                    this.modelingSubmission = exampleSubmission.submission as ModelingSubmission;
                    if (this.modelingSubmission.model) {
                        this.umlModel.set(importDiagram(parseJson(this.modelingSubmission.model)));
                    }
                    // Updates the explanation text with example modeling submission's explanation
                    this.explanationText.set(this.modelingSubmission.explanationText ?? '');
                }
                this.isNewSubmission.set(false);

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
                    this.modelingSubmission = updatedExampleSubmission.submission as ModelingSubmission;
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
        this.checkScoreBoundaries();
    }

    onUnReferencedFeedbackChanged(unreferencedFeedback: Feedback[]) {
        this.unreferencedFeedback.set(unreferencedFeedback);
        this.feedbackChanged = true;
        this.checkScoreBoundaries();
    }

    showAssessment() {
        if (this.modelChanged()) {
            this.updateExampleModelingSubmission().subscribe();
        }
        this.assessmentMode.set(true);
    }

    private modelChanged(): boolean {
        const modelingEditor = this.modelingEditor();
        return !!modelingEditor && JSON.stringify(this.umlModel) !== JSON.stringify(modelingEditor.getCurrentModel());
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
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }
        if (this.assessmentExplanation() !== this.exampleSubmission().assessmentExplanation && this.assessments()) {
            this.updateAssessmentExplanationAndExampleAssessment();
        } else if (this.assessmentExplanation() !== this.exampleSubmission().assessmentExplanation) {
            this.updateAssessmentExplanation();
        } else if (this.assessments()) {
            this.updateExampleAssessment();
        }
    }

    private updateAssessmentExplanationAndExampleAssessment() {
        this.exampleSubmission().assessmentExplanation = this.assessmentExplanation();
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

    /**
     * Updates the example submission with the assessment explanation text from the input field if it is different from the explanation already saved with the example submission.
     */
    private updateAssessmentExplanation() {
        this.exampleSubmission().assessmentExplanation = this.assessmentExplanation();
        this.exampleSubmissionService.update(this.exampleSubmission(), this.exerciseId).subscribe((exampleSubmissionResponse: HttpResponse<ExampleSubmission>) => {
            const exampleSubmission = exampleSubmissionResponse.body!;
            this.exampleSubmission.set(exampleSubmission);
            this.assessmentExplanation.set(exampleSubmission.assessmentExplanation!);
        });
    }

    private updateExampleAssessment() {
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

    /**
     * Calculates the total score of the current assessment.
     * Returns an error if the total score cannot be calculated
     * because a score is not a number/empty.
     */
    public checkScoreBoundaries() {
        if (this.assessments().length === 0) {
            this.totalScore.set(0);
            this.assessmentsAreValid.set(true);
            return;
        }

        const credits = this.assessments().map((feedback) => feedback.credits);
        if (!credits.every((credit) => credit != undefined && !isNaN(credit))) {
            this.invalidError = 'The score field must be a number and can not be empty!';
            this.assessmentsAreValid.set(false);
            return;
        }

        const maxPoints = getTotalMaxPoints(this.exercise());
        const creditsTotalScore = credits.reduce((a, b) => a! + b!, 0)!;
        this.totalScore.set(getPositiveAndCappedTotalScore(creditsTotalScore, maxPoints));
        this.assessmentsAreValid.set(true);
        this.invalidError = undefined;
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
        scrollToTopOfPage();
        this.checkScoreBoundaries();
        if (!this.assessmentsAreValid()) {
            this.alertService.error('artemisApp.modelingAssessment.invalidAssessments');
            return;
        }

        const exampleSubmission = Object.assign({}, this.exampleSubmission());
        const result = new Result();
        setLatestSubmissionResult(exampleSubmission.submission, result);
        delete result.submission;
        getLatestSubmissionResult(exampleSubmission.submission)!.feedbacks = this.assessments();

        const command = new ExampleSubmissionAssessCommand(this.tutorParticipationService, this.alertService, this);
        command.assessExampleSubmission(exampleSubmission, this.exerciseId);
    }

    markAllFeedbackToCorrect() {
        this.referencedFeedback.update((list) => list.map((feedback) => ({ ...feedback, correctionStatus: 'CORRECT' })));
        this.unreferencedFeedback.update((list) => list.map((feedback) => ({ ...feedback, correctionStatus: 'CORRECT' })));
    }

    markWrongFeedback(correctionErrors: FeedbackCorrectionError[]) {
        const byReference = new Map(correctionErrors.map((err) => [err.reference, err]));

        // mutate referenced feedback
        const referenced = this.referencedFeedback();
        referenced.forEach((feedback) => {
            const err = byReference.get(feedback.reference!);
            if (err) {
                feedback.correctionStatus = err.type;
            }
        });
        this.referencedFeedback.set(referenced);

        this.highlightMissedFeedback();
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
            this.back();
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
