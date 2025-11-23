import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { CourseExerciseService } from 'app/exercise/course-exercises/course-exercise.service';
import dayjs from 'dayjs/esm';
import { HiddenPage, OrderedPage } from 'app/lecture/manage/pdf-preview/pdf-preview.component';
import { AlertService } from 'app/shared/service/alert.service';
import { FontAwesomeModule } from '@fortawesome/angular-fontawesome';
import { NgbTooltipModule } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';

interface CategorizedExercise {
    type: ExerciseType;
    exercises: Exercise[];
}

const FOREVER = dayjs('9999-12-31');

@Component({
    selector: 'jhi-pdf-preview-date-box-component',
    templateUrl: './pdf-preview-date-box.component.html',
    styleUrls: ['./pdf-preview-date-box.component.scss'],
    standalone: true,
    imports: [FontAwesomeModule, NgbTooltipModule, RouterModule, TranslateDirective, CommonModule, FormsModule],
})
export class PdfPreviewDateBoxComponent implements OnInit {
    // Inputs
    courseId = input<number>();
    selectedPages = input<OrderedPage[]>([]);

    // Signals
    calendarSelected = signal<boolean>(false);
    exerciseSelected = signal<boolean>(false);
    defaultDate = signal<string>(this.formatDate(new Date()));
    exercises = signal<Exercise[]>([]);
    categorizedExercises = signal<CategorizedExercise[]>([]);
    hideForever = signal<boolean>(false);
    selectedExercise = signal<Exercise | undefined>(undefined);

    // Outputs
    hiddenPagesOutput = output<HiddenPage[]>();
    selectionCancelledOutput = output<boolean>();

    // Computed properties
    pagesDisplay = computed(() => {
        const pages = this.selectedPages();

        if (pages.length === 1) {
            return `${pages[0].order}`;
        }

        return pages
            .map((p) => p.order)
            .sort()
            .join(', ');
    });
    isMultiplePages = computed(() => this.selectedPages().length > 1);
    isSubmitDisabled = computed(() => {
        return !this.hideForever() && !this.calendarSelected() && !this.selectedExercise();
    });

    // Injected services
    private readonly alertService = inject(AlertService);
    private readonly courseExerciseService = inject(CourseExerciseService);

    ngOnInit(): void {
        this.loadExercises();
    }

    /**
     * Toggles the hide forever checkbox
     */
    onHideForeverChange(isChecked: boolean): void {
        this.hideForever.set(isChecked);
        if (isChecked) {
            this.calendarSelected.set(false);
            this.exerciseSelected.set(false);
            this.selectedExercise.set(undefined);
        }
    }

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

    /**
     * Loads all exercises for the current course
     */
    private loadExercises(): void {
        this.courseExerciseService.findAllExercisesWithDueDatesForCourse(this.courseId()!).subscribe({
            next: (response) => {
                if (response.body) {
                    this.exercises.set(response.body);
                    this.categorizedExercises.set(this.processExercises(response.body));
                }
            },
            error: (error) => {
                this.exercises.set([]);
                this.alertService.error(error);
            },
        });
    }

    /**
     * Formats a given `Date` object into a string in the format `YYYY-MM-DDTHH:mm`.
     *
     * @param date - The `Date` object to format.
     * @returns A formatted string representing the date and time.
     */
    formatDate(date: Date): string {
        return dayjs(date).format('YYYY-MM-DDTHH:mm');
    }

    /**
     * Format a date object to a string for display
     */
    formatDueDate(date: dayjs.Dayjs): string {
        return date!.format('MMM D, YYYY - HH:mm');
    }

    /**
     * Group and sort exercises by type
     * The server already filters for exercises with future due dates
     */
    private processExercises(exercises: Exercise[]): CategorizedExercise[] {
        const groupedExercises = new Map<ExerciseType, Exercise[]>();

        exercises.forEach((exercise) => {
            if (exercise.type && exercise.dueDate) {
                if (!groupedExercises.has(exercise.type)) {
                    groupedExercises.set(exercise.type, []);
                }
                groupedExercises.get(exercise.type)!.push(exercise);
            }
        });

        return Array.from(groupedExercises.entries()).map(([type, typeExercises]) => ({
            type,
            exercises: typeExercises.sort((a, b) => a.dueDate!.valueOf() - b.dueDate!.valueOf()),
        }));
    }

    /**
     * Determines the selected date based on the current user selection.
     *
     * - If the "hide forever" option is enabled, returns a date representing the distant future.
     * - If the calendar is selected, returns the default date from the calendar.
     * - If an exercise is selected and it has a due date, returns the due date of the selected exercise.
     * - Otherwise, returns undefined.
     *
     * @returns The selected date as a Dayjs object, or undefined if no valid selection is made.
     */
    getSelectedDate(): dayjs.Dayjs | undefined {
        if (this.hideForever()) {
            return FOREVER;
        } else if (this.calendarSelected()) {
            return dayjs(this.defaultDate());
        } else if (this.exerciseSelected() && this.selectedExercise()) {
            return this.selectedExercise()!.dueDate!;
        }
        return undefined;
    }

    /**
     * Submit the selected date option
     */
    onSubmit(): void {
        const now = dayjs();
        const selectedDate = this.getSelectedDate();

        if (!selectedDate) return;

        if (selectedDate !== FOREVER && selectedDate.isBefore(now)) {
            this.alertService.error('artemisApp.attachment.pdfPreview.dateBox.dateError');
            return;
        }

        const hiddenPages: HiddenPage[] = this.selectedPages().map((page) => ({
            slideId: page.slideId,
            date: selectedDate,
            exerciseId: this.selectedExercise()?.id ?? undefined,
        }));

        this.hiddenPagesOutput.emit(hiddenPages);
    }
}
