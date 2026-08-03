import { Component, OnInit, inject, signal } from '@angular/core';
import { FormBuilder, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';

import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ButtonModule } from 'primeng/button';
import { InputTextModule } from 'primeng/inputtext';
import { InputNumberModule } from 'primeng/inputnumber';
import { MessageModule } from 'primeng/message';
import { TextareaModule } from 'primeng/textarea';
import { AutoCompleteCompleteEvent, AutoCompleteModule } from 'primeng/autocomplete';

import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { PresentationAssessment } from 'app/presentation/shared/entities/presentation-assessment.model';
import { Course } from 'app/course/shared/entities/course.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faBan, faSave } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';

export interface PresentationAssessmentFormDialogData {
    courseId: number;
    course?: Course;
    presentationAssessment?: PresentationAssessment;
    exercises: Exercise[];
}

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
        ButtonModule,
        InputTextModule,
        InputNumberModule,
        MessageModule,
        TextareaModule,
        AutoCompleteModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
    ],
})
export class PresentationAssessmentFormDialogComponent implements OnInit {
    private readonly formBuilder = inject(FormBuilder);
    private readonly dialogRef = inject(DynamicDialogRef);
    private readonly dialogConfig = inject(DynamicDialogConfig);

    protected readonly faBan = faBan;
    protected readonly faSave = faSave;
    readonly exercises = signal<Exercise[]>([]);
    readonly filteredExercises = signal<Exercise[]>([]);

    private courseId = 0;
    private presentationAssessment?: PresentationAssessment;

    editForm = this.formBuilder.group({
        title: ['', [Validators.required, Validators.maxLength(255)]],
        description: ['', [Validators.maxLength(1000)]],
        maxPoints: [0, [Validators.required, Validators.min(0.01)]],
        exercise: [undefined as Exercise | undefined],
    });

    ngOnInit(): void {
        const data = this.dialogConfig.data as PresentationAssessmentFormDialogData;
        this.courseId = data.courseId;
        this.presentationAssessment = data.presentationAssessment;
        this.exercises.set(data.exercises ?? []);
        this.filteredExercises.set(data.exercises ?? []);

        this.editForm.reset({
            title: this.presentationAssessment?.title ?? '',
            description: this.presentationAssessment?.description ?? '',
            maxPoints: this.presentationAssessment?.maxPoints ?? 20,
            exercise: this.exercises().find((exercise) => exercise.id === this.presentationAssessment?.exerciseId),
        });
    }

    filterExercises(event: AutoCompleteCompleteEvent): void {
        const query = event.query.trim().toLocaleLowerCase();
        this.filteredExercises.set(query ? this.exercises().filter((exercise) => exercise.title?.toLocaleLowerCase().includes(query)) : this.exercises());
    }

    save(): void {
        if (this.editForm.invalid) {
            this.editForm.markAllAsTouched();
            return;
        }

        this.dialogRef.close({
            presentationAssessment: this.createFromForm(),
        } satisfies PresentationAssessmentFormDialogResult);
    }

    cancel(): void {
        this.dialogRef.close();
    }

    private createFromForm(): PresentationAssessment {
        const formValue = this.editForm.getRawValue();
        return {
            id: this.presentationAssessment?.id,
            title: formValue.title?.trim(),
            description: formValue.description ?? undefined,
            maxPoints: formValue.maxPoints ?? undefined,
            courseId: this.courseId,
            exerciseId: formValue.exercise?.id,
        };
    }
}
