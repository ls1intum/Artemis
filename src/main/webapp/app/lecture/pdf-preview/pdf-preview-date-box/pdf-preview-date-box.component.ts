import { Component, OnInit, input, signal } from '@angular/core';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { Course } from 'app/entities/course.model';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
import dayjs from 'dayjs/esm';

interface CategorizedExercise {
    type: ExerciseType;
    exercises: Exercise[];
}

@Component({
    selector: 'jhi-pdf-preview-date-box-component',
    templateUrl: './pdf-preview-date-box.component.html',
    styleUrls: ['./pdf-preview-date-box.component.scss'],
    standalone: true,
    imports: [ArtemisSharedModule],
})
export class PdfPreviewDateBoxComponent implements OnInit {
    // Inputs
    course = input<Course>();

    // Signals
    calendarSelected = signal<boolean>(false);
    exerciseSelected = signal<boolean>(false);
    defaultDate = signal<string>(this.formatDate(new Date()));
    exercises = signal<Exercise[]>([]);
    categorizedExercises = signal<CategorizedExercise[]>([]);
    hideForever = signal<boolean>(false);
    selectedExerciseId = signal<number | null>(null);

    constructor(private courseExerciseService: CourseExerciseService) {}

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
            this.selectedExerciseId.set(null);
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
    formatDueDate(date: dayjs.Dayjs | undefined): string {
        return date ? date.format('MMM D, YYYY') : 'No due date';
    }

    /**
     * Handles selection of an exercise
     * @param exerciseId the id of the selected exercise
     */
    onExerciseClick(exerciseId: number): void {
        this.selectedExerciseId.set(exerciseId);
        console.log(this.selectedExerciseId());
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
}
