import { Component, computed, effect, inject, input, output, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';
import { FloatLabelModule } from 'primeng/floatlabel';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PlannedExercise, PlannedExerciseCreateDTO } from 'app/core/course/shared/entities/planned-exercise.model';
import { PlannedExerciseService } from 'app/core/course/shared/services/planned-exercise.service';
import { addOneMinuteTo, convertDateToDayjsDate, convertDayjsDateToDate, isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';
import { take } from 'rxjs';

@Component({
    selector: 'jhi-planned-exercise-create',
    imports: [FormsModule, DialogModule, InputTextModule, DatePickerModule, ButtonModule, AutoFocusModule, TranslateDirective, FloatLabelModule],
    templateUrl: './planned-exercise-create-or-update.component.html',
    styleUrl: './planned-exercise-create-or-update.component.scss',
})
export class PlannedExerciseCreateOrUpdateComponent {
    private plannedExerciseService = inject(PlannedExerciseService);

    courseId = input.required<number>();
    plannedExercise = input<PlannedExercise>();
    title = signal<string>('');
    isTitleInvalid = computed(() => this.title() === '');
    releaseDate = signal<Date | undefined>(undefined);
    startDate = signal<Date | undefined>(undefined);
    minimumStartDate = computed(() => addOneMinuteTo(this.releaseDate()));
    isStartDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(this.releaseDate(), this.startDate()));
    dueDate = signal<Date | undefined>(undefined);
    minimumDueDate = computed(() => addOneMinuteTo(this.startDate()) ?? addOneMinuteTo(this.releaseDate()));
    isDueDateInvalid = computed(() => isFirstDateAfterOrEqualSecond(this.releaseDate(), this.dueDate()) || isFirstDateAfterOrEqualSecond(this.startDate(), this.dueDate()));
    assessmentDueDate = signal<Date | undefined>(undefined);
    minimumAssessmentDueDate = computed(() => addOneMinuteTo(this.dueDate()) ?? addOneMinuteTo(this.startDate()) ?? addOneMinuteTo(this.releaseDate()));
    isAssessmentDueDateInvalid = computed(() => this.isOtherDateAfterOrEqualAssessmentDueDate(this.releaseDate(), this.startDate(), this.dueDate(), this.assessmentDueDate()));
    areInputsInvalid = computed(() => this.isTitleInvalid() || this.isStartDateInvalid() || this.isDueDateInvalid());
    onOperationFinished = output<void>();

    constructor() {
        effect(() => {
            const plannedExercise = this.plannedExercise();
            if (plannedExercise) {
                this.title.set(plannedExercise.title);
                this.releaseDate.set(convertDayjsDateToDate(plannedExercise.releaseDate));
                this.startDate.set(convertDayjsDateToDate(plannedExercise.startDate));
                this.dueDate.set(convertDayjsDateToDate(plannedExercise.dueDate));
                this.assessmentDueDate.set(convertDayjsDateToDate(plannedExercise.assessmentDueDate));
            }
        });
    }

    cancel() {
        this.onOperationFinished.emit();
    }

    save() {
        const plannedExercise = this.plannedExercise();
        const courseId = this.courseId();
        if (plannedExercise) {
            plannedExercise.title = this.title();
            plannedExercise.releaseDate = convertDateToDayjsDate(this.releaseDate());
            plannedExercise.startDate = convertDateToDayjsDate(this.startDate());
            plannedExercise.dueDate = convertDateToDayjsDate(this.dueDate());
            plannedExercise.assessmentDueDate = convertDateToDayjsDate(this.assessmentDueDate());
            this.plannedExerciseService
                .update(plannedExercise, courseId)
                .pipe(take(1))
                .subscribe(() => {
                    this.onOperationFinished.emit();
                });
        } else {
            const plannedExerciseCreateDTO = new PlannedExerciseCreateDTO(
                this.title(),
                convertDateToDayjsDate(this.releaseDate()),
                convertDateToDayjsDate(this.startDate()),
                convertDateToDayjsDate(this.dueDate()),
                convertDateToDayjsDate(this.assessmentDueDate()),
            );
            this.plannedExerciseService
                .create(plannedExerciseCreateDTO, courseId)
                .pipe(take(1))
                .subscribe(() => {
                    this.onOperationFinished.emit();
                });
        }
    }

    onTitleChange(value: string) {
        this.title.set(value);
    }

    onReleaseDateChange(value: Date | null) {
        this.releaseDate.set(value ? value : undefined);
    }

    onStartDateChange(value: Date | null) {
        this.startDate.set(value ? value : undefined);
    }

    onDueDateChange(value: Date | null) {
        this.dueDate.set(value ? value : undefined);
    }

    onAssessmentDueDateChange(value: Date | null) {
        this.assessmentDueDate.set(value ? value : undefined);
    }

    private isOtherDateAfterOrEqualAssessmentDueDate(releaseDate?: Date, startDate?: Date, dueDate?: Date, assessmentDueDate?: Date): boolean {
        return (
            isFirstDateAfterOrEqualSecond(releaseDate, assessmentDueDate) ||
            isFirstDateAfterOrEqualSecond(startDate, assessmentDueDate) ||
            isFirstDateAfterOrEqualSecond(dueDate, assessmentDueDate)
        );
    }
}
