import { Component } from '@angular/core';

@Component({
    selector: 'jhi-orion-course-management-exercises',
    template: `
        <jhi-course-management-exercises>
            <ng-template #overrideGenerateAndImportButton>
                <!-- No replacement, just not visible in Orion -->
            </ng-template>
            <ng-template #overrideNonProgrammingExerciseCard>
                <!-- Nothing, only programming exercises are shown -->
            </ng-template>
            <ng-template
                #overrideProgrammingExerciseCard
                let-course="course"
                let-embedded="embedded"
                let-programmingExerciseCountCallback="programmingExerciseCountCallback"
                let-exerciseFilter="exerciseFilter"
                let-filteredProgrammingExercisesCountCallback="filteredProgrammingExercisesCountCallback"
            >
                <jhi-orion-programming-exercise
                    [course]="course"
                    [embedded]="embedded"
                    (exerciseCount)="programmingExerciseCountCallback($event)"
                    [exerciseFilter]="exerciseFilter"
                    (filteredExerciseCount)="filteredProgrammingExercisesCountCallback($event)"
                >
                </jhi-orion-programming-exercise>
            </ng-template>
        </jhi-course-management-exercises>
    `,
})
export class OrionCourseManagementExercisesComponent {
    // only overrides the programming exercise list with Orion's programming exercise list and suppresses the other exercises
}
