import { Component, effect, inject, input, output, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import {
    TumUiAutoCompleteComponent,
    TumUiAutoCompleteSearchEvent,
    TumUiButtonComponent,
    TumUiInputDirective,
    TumUiInputNumberComponent,
    TumUiMessageComponent,
} from '@tumaet/ui-angular';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export interface PresentationAssessmentFormDialogResult {
    presentationAssessment: PresentationAssessment;
}

@Component({
    selector: 'jhi-presentation-assessment-form-dialog',
    templateUrl: './presentation-assessment-form-dialog.component.html',
    styleUrl: './presentation-assessment-form-dialog.component.scss',
    imports: [
        FormsModule,
        ReactiveFormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        TumUiAutoCompleteComponent,
        TumUiButtonComponent,
        TumUiInputDirective,
        TumUiInputNumberComponent,
        TumUiMessageComponent,
    ],
})
export class PresentationAssessmentFormDialogComponent {
    private readonly formBuilder = inject(FormBuilder);

    readonly courseId = input.required<number>();
    readonly presentationAssessment = input<PresentationAssessment>();
    readonly exercises = input<Exercise[]>([]);
    readonly saved = output<PresentationAssessmentFormDialogResult>();
    readonly cancelled = output<void>();

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    readonly filteredExercises = signal<Exercise[]>([]);

    editForm = this.formBuilder.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        description: ['', [Validators.maxLength(1000)]],
        maxPoints: [0, [Validators.required, Validators.min(0.01)]],
        exercise: [undefined as Exercise | undefined],
    });

    constructor() {
        effect(() => {
            const presentationAssessment = this.presentationAssessment();
            const exercises = this.exercises();
            this.filteredExercises.set(exercises);
            this.editForm.reset({
                title: presentationAssessment?.title ?? '',
                description: presentationAssessment?.description ?? '',
                maxPoints: presentationAssessment?.maxPoints ?? 20,
                exercise: exercises.find((exercise) => exercise.id === presentationAssessment?.exerciseId),
            });
        });
    }

    filterExercises(event: TumUiAutoCompleteSearchEvent): void {
        const query = event.query.trim().toLocaleLowerCase();
        this.filteredExercises.set(query ? this.exercises().filter((exercise) => exercise.title?.toLocaleLowerCase().includes(query)) : this.exercises());
    }

    save(): void {
        if (this.editForm.invalid) {
            this.editForm.markAllAsTouched();
            return;
        }
        this.saved.emit({ presentationAssessment: this.createFromForm() });
    }

    cancel(): void {
        this.cancelled.emit();
    }

    private createFromForm(): PresentationAssessment {
        const formValue = this.editForm.getRawValue();
        return {
            id: this.presentationAssessment()?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            maxPoints: formValue.maxPoints ?? undefined,
            courseId: this.courseId(),
            exerciseId: formValue.exercise?.id,
        };
    }
}
