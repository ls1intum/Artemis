import { Component, OnDestroy, OnInit, input, output, signal } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import dayjs from 'dayjs/esm';

interface CategorizedExercise {
    type: ExerciseType;
    exercises: Exercise[];
}

interface HiddenPage {
    pageIndex: number;
    date: dayjs.Dayjs;
}

const FOREVER = dayjs('9999-12-31');

@Component({
    selector: 'jhi-pdf-preview-date-box-component',
    templateUrl: './pdf-preview-date-box.component.html',
    styleUrls: ['./pdf-preview-date-box.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class PdfPreviewDateBoxComponent implements OnInit, OnDestroy {
    // Inputs
    course = input<Course>();
    pageIndex = input<number>();

    // Signals
    calendarSelected = signal<boolean>(false);
    exerciseSelected = signal<boolean>(false);
    defaultDate = signal<string>(this.formatDate(new Date()));
    exercises = signal<Exercise[]>([]);
    categorizedExercises = signal<CategorizedExercise[]>([]);
    hideForever = signal<boolean>(false);
    selectedExercise = signal<Exercise | null>(null);
    hiddenPage = signal<HiddenPage | null>(null);

    // Outputs
    dateBoxOpened = output<boolean>();
    hiddenPageOutput = output<HiddenPage>();

    constructor(private courseExerciseService: CourseExerciseService) {}

    ngOnInit(): void {
        this.loadExercises();
        this.dateBoxOpened.emit(true);
    }

    ngOnDestroy(): void {
        this.dateBoxOpened.emit(false);
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
        console.log(this.pageIndex());
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
        if (!this.course()) {
            return;
        }

        const courseId = this.course()!.id!;

        this.courseExerciseService.findAllExercisesForCourse(courseId).subscribe({
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
     * Formats a Date object to YYYY-MM-DD string format required by date input
     * @param date The date to format
     * @returns The formatted date string
     */
    formatDate(date: Date): string {
        const year = date.getFullYear();
        const month = String(date.getMonth() + 1).padStart(2, '0');
        const day = String(date.getDate()).padStart(2, '0');
        return `${year}-${month}-${day}`;
    }

    /**
     * Format a date object to a string for display
     */
    formatDueDate(date: dayjs.Dayjs): string {
        return date!.format('MMM D, YYYY - HH:mm');
    }

    /**
     * Handles selection of an exercise
     * @param exercise the selected exercise
     */
    onExerciseClick(exercise: Exercise): void {
        this.selectedExercise.set(exercise);
    }

    /**
     * Group and sort exercises by type, excluding those without a due date
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
     * Submit the selected date option
     */
    onSubmit(): void {
        let selectedDate: dayjs.Dayjs;
        if (this.hideForever()) {
            selectedDate = FOREVER;
        } else if (this.calendarSelected()) {
            selectedDate = dayjs(this.defaultDate());
        } else if (this.exerciseSelected() && this.selectedExercise()) {
            selectedDate = this.selectedExercise()!.dueDate!;
        } else {
            return;
        }

        const newEntry: HiddenPage = {
            pageIndex: this.pageIndex()!,
            date: selectedDate,
        };

        this.hiddenPage.set(newEntry);
        this.hiddenPageOutput.emit(newEntry);
        this.dateBoxOpened.emit(false);
    }
}
