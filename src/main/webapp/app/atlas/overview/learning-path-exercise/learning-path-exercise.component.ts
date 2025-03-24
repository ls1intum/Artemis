import { ChangeDetectionStrategy, Component, ViewContainerRef, effect, inject, input } from '@angular/core';
import { CourseExerciseDetailsComponent } from 'app/core/course/overview/exercise-details/course-exercise-details.component';

@Component({
    selector: 'jhi-learning-path-exercise',
    changeDetection: ChangeDetectionStrategy.OnPush,
    templateUrl: './learning-path-exercise.component.html',
})
export class LearningPathExerciseComponent {
    public readonly courseId = input.required<number>();
    public readonly exerciseId = input.required<number>();

    private readonly viewContainerRef = inject(ViewContainerRef);

    constructor() {
        effect(() => {
            this.viewContainerRef.clear();
            // The exercise component can not be directly added to the template as before rendering the learning path mode
            // has to be activated. This is done by setting the learningPathMode property of the exercise component to true.
            const exerciseComponent = this.viewContainerRef.createComponent(CourseExerciseDetailsComponent);
            exerciseComponent.instance.courseId = this.courseId();
            exerciseComponent.instance.exerciseId = this.exerciseId();
            exerciseComponent.instance.learningPathMode = true;
        });
    }
}
