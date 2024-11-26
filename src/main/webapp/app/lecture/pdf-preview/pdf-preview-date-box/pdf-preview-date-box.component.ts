import { Component, input, signal } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Lecture } from 'app/entities/lecture.model';

@Component({
    selector: 'jhi-pdf-preview-date-box-component',
    templateUrl: './pdf-preview-date-box.component.html',
    styleUrls: ['./pdf-preview-date-box.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class PdfPreviewDateBoxComponent {
    // Inputs
    lecture = input<Lecture>();

    // Signals
    calendarSelected = signal<boolean>(false);
    exerciseSelected = signal<boolean>(false);

    /**
     * Toggles the visibility of the calendar.
     */
    selectCalendar(): void {
        this.calendarSelected.set(true);
        this.exerciseSelected.set(false);
    }

    /**
     * Handles the "Select Exercise" button click.
     */
    selectExercise(): void {
        this.calendarSelected.set(false);
        this.exerciseSelected.set(true);
    }
}
