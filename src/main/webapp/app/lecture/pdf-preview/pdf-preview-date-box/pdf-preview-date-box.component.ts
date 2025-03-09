import { Component, OnInit, computed, inject, input, output, signal } from '@angular/core';
import { RouterModule } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import dayjs from 'dayjs/esm';
import { HiddenPage } from 'app/lecture/pdf-preview/pdf-preview.component';
import { AlertService } from 'app/core/util/alert.service';
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
    course = input<Course>();
    pageIndices = input<number[]>([]);

    // Signals
    calendarSelected = signal<boolean>(false);
    exerciseSelected = signal<boolean>(false);
    defaultDate = signal<string>(this.formatDate(new Date()));
    exercises = signal<Exercise[]>([]);
    categorizedExercises = signal<CategorizedExercise[]>([]);
    hideForever = signal<boolean>(false);
    selectedExercise = signal<Exercise | null>(null);
    isMultiplePages = signal<boolean>(false);

    // Outputs
    hiddenPagesOutput = output<HiddenPage[]>();
    selectionCancelledOutput = output<boolean>();

    // Computed properties
    pageIndicesSorted = computed(() => {
        const indices = [...this.pageIndices()];
        return indices.sort((a, b) => a - b).join(', ');
    });

    // Injected services
    private readonly alertService = inject(AlertService);
    private readonly courseExerciseService = inject(CourseExerciseService);

    ngOnInit(): void {
        this.loadExercises();
        this.isMultiplePages.set(this.pageIndices().length > 1);
    }

    /**
     * Toggles the hide forever checkbox
     */
    onHideForeverChange(isChecked: boolean): void {
        this.hideForever.set(isChecked);
        if (isChecked) {
            this.calendarSelected.set(false);
            this.exerciseSelected.set(false);
            this.selectedExercise.set(null);
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
        this.courseExerciseService.findAllExercisesForCourse(this.course()!.id!).subscribe({
            next: (response) => {
                if (response.body) {
                    this.exercises.set(response.body);
                    this.categorizedExercises.set(this.processExercises(response.body));
                }
            },
            error: () => {
                this.exercises.set([]);
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
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        const hours = String(date.getHours()).padStart(2, '0');
        const minutes = String(date.getMinutes()).padStart(2, '0');
        return `${year}-${month}-${day}T${hours}:${minutes}`;
    }

    /**
     * Format a date object to a string for display
     */
    formatDueDate(date: dayjs.Dayjs): string {
        return date!.format('MMM D, YYYY - HH:mm');
    }

    /**
     * Group and sort exercises by type, excluding those without a due date
     */
    private processExercises(exercises: Exercise[]): CategorizedExercise[] {
        const groupedExercises = new Map<ExerciseType, Exercise[]>();
        const now = dayjs();

        exercises.forEach((exercise) => {
            if (exercise.type && exercise.dueDate && exercise.dueDate.isAfter(now)) {
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
     * Submit the selected date option
     */
    onSubmit(): void {
        const now = dayjs();
        const selectedDate = this.hideForever()
            ? FOREVER
            : this.calendarSelected()
              ? dayjs(this.defaultDate())
              : this.exerciseSelected() && this.selectedExercise()
                ? this.selectedExercise()!.dueDate!
                : null;

        if (!selectedDate) return;

        if (selectedDate !== FOREVER && selectedDate.isBefore(now)) {
            this.alertService.error('artemisApp.attachment.pdfPreview.dateBox.dateError');
            return;
        }

        const hiddenPages: HiddenPage[] = this.pageIndices().map((pageIndex) => ({
            pageIndex,
            date: selectedDate,
            exerciseId: this.selectedExercise()?.id ?? null,
        }));

        this.hiddenPagesOutput.emit(hiddenPages);
    }
}
