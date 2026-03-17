import { Component, computed, input } from '@angular/core';
import { Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { ExerciseHeaderActionsComponent } from 'app/exercise/exercise-headers/exercise-header-actions/exercise-header-actions.component';

@Component({
    selector: 'jhi-exercise-header',
    templateUrl: './exercise-header.component.html',
    imports: [FaIconComponent, ExerciseHeadersInformationComponent, ExerciseHeaderActionsComponent],
})
export class ExerciseHeaderComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly studentParticipation = input<StudentParticipation>();
    readonly submissionPolicy = input<SubmissionPolicy>();

    readonly exerciseIcon = computed(() => {
        const exercise = this.exercise();
        return exercise.type ? getIcon(exercise.type) : undefined;
    });
}
