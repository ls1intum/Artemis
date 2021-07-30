import { Component } from '@angular/core';

@Component({
    selector: 'jhi-orion-course-exercise-details',
    template: `
        <jhi-course-exercise-details>
            <ng-template #overrideStudentActions let-exercise="exercise" let-showResult="showResult" let-courseId="courseId">
                <jhi-orion-exercise-details-student-actions [exercise]="exercise" [showResult]="showResult" [courseId]="courseId"></jhi-orion-exercise-details-student-actions>
            </ng-template>
        </jhi-course-exercise-details>
    `,
})
export class OrionCourseExerciseDetailsComponent {
    // only replaces the student actions with Orion student actions in the overview
}
