import { Component } from '@angular/core';
import { CourseExerciseDetailsComponent } from 'app/overview/exercise-details/course-exercise-details.component';
import { OrionExerciseDetailsStudentActionsComponent } from './orion-exercise-details-student-actions.component';

@Component({
    selector: 'jhi-orion-course-exercise-details',
    template: `
        <jhi-course-exercise-details>
            <ng-template #overrideStudentActions let-exercise="exercise" let-courseId="courseId">
                <jhi-orion-exercise-details-student-actions [exercise]="exercise" [courseId]="courseId" />
            </ng-template>
        </jhi-course-exercise-details>
    `,
    imports: [CourseExerciseDetailsComponent, OrionExerciseDetailsStudentActionsComponent],
})
export class OrionCourseExerciseDetailsComponent {
    // only replaces the student actions with Orion student actions in the overview
}
