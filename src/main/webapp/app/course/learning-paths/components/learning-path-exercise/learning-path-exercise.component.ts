import { Component, InputSignal, Signal, effect, input, viewChild } from '@angular/core';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { CourseExerciseDetailsModule } from 'app/overview/exercise-details/course-exercise-details.module';

@Component({
    selector: 'jhi-learning-path-exercise',
    standalone: true,
    imports: [CourseExerciseDetailsModule],
    templateUrl: './learning-path-exercise.component.html',
    styleUrl: './learning-path-exercise.component.scss',
})
export class LearningPathExerciseComponent {
    public readonly courseId: InputSignal<number> = input.required<number>();
    public readonly exerciseId: InputSignal<number> = input.required<number>();

    private readonly exercise: Signal<CourseExerciseDetailsComponent> = viewChild.required(CourseExerciseDetailsComponent);

    constructor() {
        effect(() => {
            this.exercise().courseId = this.courseId();
            this.exercise().exerciseId = this.exerciseId();
            this.exercise().learningPathMode = true;
            this.exercise().loadExercise();
        });
    }
}
