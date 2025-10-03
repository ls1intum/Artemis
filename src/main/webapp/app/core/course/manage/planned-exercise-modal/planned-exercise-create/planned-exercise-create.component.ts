import { Component, computed, effect, input, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { PlannedExercise } from 'app/core/course/manage/planned-exercise-modal/planned-exercise-modal.component';
import { addOneMinuteTo, convertDayjsDateToDate, isFirstDateAfterOrEqualSecond } from 'app/shared/util/date.utils';

@Component({
    selector: 'jhi-planned-exercise-create',
    imports: [FormsModule, DialogModule, InputTextModule, DatePickerModule, ButtonModule, AutoFocusModule, TranslateDirective],
    templateUrl: './planned-exercise-create.component.html',
    styleUrl: './planned-exercise-create.component.scss',
})
export class PlannedExerciseCreateComponent {
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
    isAssessmentDueDateInvalid = computed(() => this._isAssessmentDueDateInvalid(this.releaseDate(), this.startDate(), this.dueDate(), this.assessmentDueDate()));
    areInputsInvalid = computed(() => this.isTitleInvalid() || this.isStartDateInvalid() || this.isDueDateInvalid());

    constructor() {
        effect(() => {
            const plannedExercise = this.plannedExercise();
            if (plannedExercise) {
                this.title.set(plannedExercise.title);
                this.startDate.set(convertDayjsDateToDate(plannedExercise.startDate));
                this.dueDate.set(convertDayjsDateToDate(plannedExercise.dueDate));
                this.assessmentDueDate.set(convertDayjsDateToDate(plannedExercise.assessmentDueDate));
            }
        });
    }
    cancel() {}

    save() {}

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

    private _isAssessmentDueDateInvalid(releaseDate?: Date, startDate?: Date, dueDate?: Date, assessmentDueDate?: Date): boolean {
        return (
            isFirstDateAfterOrEqualSecond(releaseDate, assessmentDueDate) ||
            isFirstDateAfterOrEqualSecond(startDate, assessmentDueDate) ||
            isFirstDateAfterOrEqualSecond(dueDate, assessmentDueDate)
        );
    }
}
