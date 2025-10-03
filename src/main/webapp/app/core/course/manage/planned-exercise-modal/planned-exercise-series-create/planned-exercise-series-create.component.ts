import { Component, signal } from '@angular/core';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FormsModule } from '@angular/forms';
import { DialogModule } from 'primeng/dialog';
import { InputTextModule } from 'primeng/inputtext';
import { DatePickerModule } from 'primeng/datepicker';
import { FloatLabelModule } from 'primeng/floatlabel';
import { ButtonModule } from 'primeng/button';
import { AutoFocusModule } from 'primeng/autofocus';

@Component({
    selector: 'jhi-planned-exercise-series-create',
    imports: [FormsModule, DialogModule, InputTextModule, DatePickerModule, ButtonModule, AutoFocusModule, FloatLabelModule, TranslateDirective],
    templateUrl: './planned-exercise-series-create.component.html',
    styleUrl: './planned-exercise-series-create.component.scss',
})
export class PlannedExerciseSeriesCreateComponent {
    releaseDate = signal<Date | undefined>(undefined);
    startDate = signal<Date | undefined>(undefined);
    dueDate = signal<Date | undefined>(undefined);
    assessmentDueDate = signal<Date | undefined>(undefined);
    seriesEndDate = signal<Date | undefined>(undefined);

    onReleaseDateChange(date: Date) {
        this.releaseDate.set(date);
    }

    onStartDateChange(date: Date) {
        this.startDate.set(date);
    }

    onDueDateChange(date: Date) {
        this.dueDate.set(date);
    }

    onAssessmentDueDateChange(date: Date) {
        this.assessmentDueDate.set(date);
    }

    onSeriesEndDateChange(date: Date) {
        this.seriesEndDate.set(date);
    }
}
