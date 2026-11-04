import { NgTemplateOutlet } from '@angular/common';
import { Component, DestroyRef, computed, effect, inject, input, output, signal, untracked } from '@angular/core';
import { outputFromObservable, takeUntilDestroyed, toObservable, toSignal } from '@angular/core/rxjs-interop';
import { AbstractControl, FormBuilder, ReactiveFormsModule, ValidationErrors, ValidatorFn, Validators } from '@angular/forms';
import { Observable, startWith } from 'rxjs';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faArrowLeft, faArrowRight, faBan, faCheck, faSave, faTrash } from '@fortawesome/free-solid-svg-icons';
import {
    TumAetUiAutoCompleteComponent,
    TumAetUiAutoCompleteSearchEvent,
    TumAetUiButtonComponent,
    TumAetUiButtonDirective,
    TumAetUiInputDirective,
    TumAetUiMessageComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment, PresentationGradingMethod } from 'app/presentation/shared/entities/presentation-assessment.model';
import { ExerciseTitle } from 'app/exercise/shared/entities/exercise/exercise-title.model';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ActionType } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import { PointsInputComponent } from 'app/shared-ui/tum-aet-ui-integration/points-input/points-input.component';
import { createRubricForm, notBlank, readRubricForm } from 'app/presentation/manage/presentation-rubric-form';
import { PresentationRubricEditorComponent } from 'app/presentation/manage/presentation-rubric-editor.component';
import { GRADING_METHOD_ICONS } from 'app/presentation/shared/presentation-grading-method-icons';

export interface PresentationAssessmentFormDialogResult {
    presentationAssessment: PresentationAssessment;
}

/** The steps of creating a presentation; a presentation graded by pass/fail has no configuration step. */
type PresentationFormStep = 'details' | 'method' | 'configuration';

const MAX_POINTS_UPPER_BOUND = 10000;
const MIN_POINTS = 0.001;
/** The autocomplete writes the typed text into the control; only a selected exercise (an object) or an empty field is a valid value. */
const exerciseSelected: ValidatorFn = (control: AbstractControl): ValidationErrors | null => (typeof control.value === 'string' ? { exerciseNotSelected: true } : null);

@Component({
    selector: 'jhi-presentation-assessment-form-dialog',
    templateUrl: './presentation-assessment-form-dialog.component.html',
    styleUrl: './presentation-assessment-form-dialog.component.scss',
    imports: [
        NgTemplateOutlet,
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumAetUiAutoCompleteComponent,
        TumAetUiButtonComponent,
        TumAetUiButtonDirective,
        TumAetUiInputDirective,
        PointsInputComponent,
        TumAetUiMessageComponent,
        DeleteButtonDirective,
        PresentationRubricEditorComponent,
    ],
})
export class PresentationAssessmentFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);
    private readonly destroyRef = inject(DestroyRef);

    readonly courseId = input.required<number>();
    readonly presentationAssessment = input<PresentationAssessment>();
    readonly exercises = input<ExerciseTitle[]>([]);
    readonly isSaving = input(false);
    /** Whether the current user may delete the presentation; only instructors may. */
    readonly canDelete = input(false);
    readonly dialogError = input<Observable<string>>();
    readonly saved = output<PresentationAssessmentFormDialogResult>();
    readonly cancelled = output<void>();
    readonly deleteRequested = output<PresentationAssessment>();

    protected readonly ActionType = ActionType;
    protected readonly PresentationGradingMethod = PresentationGradingMethod;
    protected readonly gradingMethods = Object.values(PresentationGradingMethod);
    protected readonly faArrowLeft = faArrowLeft;
    protected readonly faArrowRight = faArrowRight;
    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    protected readonly faTrash = faTrash;
    protected readonly faCheck = faCheck;
    protected readonly gradingMethodIcons = GRADING_METHOD_ICONS;
    protected readonly maxPointsUpperBound = MAX_POINTS_UPPER_BOUND;
    protected readonly minPoints = MIN_POINTS;
    readonly filteredExercises = signal<ExerciseTitle[]>([]);

    editForm = this.formBuilder.group({
        title: ['', [Validators.required, notBlank, Validators.maxLength(255)]],
        description: ['', [Validators.maxLength(1000)]],
        gradingMethod: [undefined as PresentationGradingMethod | undefined, [Validators.required]],
        maxPoints: [undefined as number | undefined, [Validators.required, Validators.min(MIN_POINTS), Validators.max(MAX_POINTS_UPPER_BOUND)]],
        exercise: [undefined as ExerciseTitle | undefined, [exerciseSelected]],
    });
    /** The rubric of a new presentation graded by rubric; an existing rubric is edited in its own dialog. */
    protected readonly rubricForm = createRubricForm();

    protected readonly isEdit = computed(() => this.presentationAssessment() !== undefined);
    protected readonly step = signal<PresentationFormStep>('details');
    protected readonly selectedGradingMethod = toSignal(this.editForm.controls.gradingMethod.valueChanges.pipe(startWith(this.editForm.controls.gradingMethod.value)));
    protected readonly steps = computed<PresentationFormStep[]>(() =>
        this.selectedGradingMethod() === PresentationGradingMethod.PASS_FAIL ? ['details', 'method'] : ['details', 'method', 'configuration'],
    );
    /** Whether the current step is the last one of creating, where saving replaces continuing. */
    protected readonly isLastStep = computed(() => this.step() === this.steps().at(-1));
    /** Whether the rubric errors are shown, which they are only after an attempt to save. */
    protected readonly showRubricErrors = signal(false);
    /** Only the rubric editor needs the wide dialog; the dialog itself belongs to the management view. */
    private readonly wide = computed(() => !this.isEdit() && this.step() === 'configuration' && this.selectedGradingMethod() === PresentationGradingMethod.RUBRIC);
    readonly wideChanged = outputFromObservable(toObservable(this.wide));

    constructor() {
        effect(() => {
            const presentationAssessment = this.presentationAssessment();
            const exercises = untracked(this.exercises);
            this.editForm.reset({
                title: presentationAssessment?.title ?? '',
                description: presentationAssessment?.description ?? '',
                gradingMethod: presentationAssessment?.gradingMethod,
                maxPoints: presentationAssessment?.maxPoints,
                exercise: exercises.find((exercise) => exercise.id === presentationAssessment?.exerciseId),
            });
            this.step.set('details');
            this.showRubricErrors.set(false);
            untracked(() => this.applyControlStates());
        });
        effect(() => {
            const exercises = this.exercises();
            this.filteredExercises.set(exercises);
            const presentationAssessment = untracked(this.presentationAssessment);
            if (this.editForm.controls.exercise.pristine && !this.editForm.controls.exercise.value && presentationAssessment?.exerciseId) {
                this.editForm.controls.exercise.setValue(
                    exercises.find((exercise) => exercise.id === presentationAssessment.exerciseId),
                    { emitEvent: false },
                );
            }
        });
        effect(() => {
            if (this.isSaving()) {
                this.editForm.disable({ emitEvent: false });
                this.rubricForm.disable({ emitEvent: false });
            } else {
                this.editForm.enable({ emitEvent: false });
                this.rubricForm.enable({ emitEvent: false });
                untracked(() => this.applyControlStates());
            }
        });
        this.editForm.controls.gradingMethod.valueChanges.pipe(takeUntilDestroyed(this.destroyRef)).subscribe(() => this.applyControlStates());
    }

    readonly filterExercises = (event: TumAetUiAutoCompleteSearchEvent): void => {
        const query = event.query.trim().toLocaleLowerCase();
        this.filteredExercises.set(query ? this.exercises().filter((exercise) => exercise.title?.toLocaleLowerCase().includes(query)) : this.exercises());
    };

    /** Moves to the next step of creating once the current one is valid; otherwise shows its errors. */
    readonly continue = (): void => {
        const controls = this.editForm.controls;
        if (this.step() === 'details') {
            if (controls.title.invalid || controls.exercise.invalid || controls.description.invalid) {
                controls.title.markAsTouched();
                controls.exercise.markAsTouched();
                controls.description.markAsTouched();
                return;
            }
            this.step.set('method');
        } else if (this.step() === 'method') {
            if (controls.gradingMethod.invalid) {
                controls.gradingMethod.markAsTouched();
                return;
            }
            this.step.set('configuration');
        }
    };

    readonly back = (): void => {
        this.step.update((step) => (step === 'configuration' ? 'method' : 'details'));
    };

    protected isStepComplete(candidate: PresentationFormStep): boolean {
        return this.steps().indexOf(candidate) < this.steps().indexOf(this.step());
    }

    readonly save = (): void => {
        const createsRubric = !this.isEdit() && this.selectedGradingMethod() === PresentationGradingMethod.RUBRIC;
        if (this.isSaving() || this.editForm.invalid || (createsRubric && this.rubricForm.invalid)) {
            this.editForm.markAllAsTouched();
            if (createsRubric) {
                this.rubricForm.markAllAsTouched();
                this.showRubricErrors.set(true);
            }
            return;
        }
        this.saved.emit({ presentationAssessment: this.createFromForm() });
    };

    readonly cancel = (): void => {
        this.cancelled.emit();
    };

    readonly requestDelete = (): void => {
        const presentationAssessment = this.presentationAssessment();
        if (presentationAssessment?.id) {
            this.deleteRequested.emit(presentationAssessment);
        }
    };

    /** The method of an existing presentation is fixed, and only a presentation graded by score has max. points. */
    private applyControlStates(): void {
        if (this.isSaving()) {
            return;
        }
        const controls = this.editForm.controls;
        if (this.isEdit()) {
            controls.gradingMethod.disable({ emitEvent: false });
        } else {
            controls.gradingMethod.enable({ emitEvent: false });
        }
        if (controls.gradingMethod.value === PresentationGradingMethod.SCORE) {
            controls.maxPoints.enable({ emitEvent: false });
        } else {
            controls.maxPoints.disable({ emitEvent: false });
        }
    }

    private readonly createFromForm = (): PresentationAssessment => {
        const formValue = this.editForm.getRawValue();
        const gradingMethod = formValue.gradingMethod ?? undefined;
        return {
            id: this.presentationAssessment()?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            gradingMethod,
            maxPoints: gradingMethod === PresentationGradingMethod.SCORE ? (formValue.maxPoints ?? undefined) : undefined,
            courseId: this.courseId(),
            exerciseId: formValue.exercise?.id ?? (this.editForm.controls.exercise.pristine ? this.presentationAssessment()?.exerciseId : undefined),
            rubricCriteria: !this.isEdit() && gradingMethod === PresentationGradingMethod.RUBRIC ? readRubricForm(this.rubricForm) : undefined,
        };
    };
}
