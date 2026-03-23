import { Component, computed, input, model } from '@angular/core';
import { Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { SubmissionPolicy } from 'app/exercise/shared/entities/submission/submission-policy.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseHeadersInformationComponent } from 'app/exercise/exercise-headers/exercise-headers-information/exercise-headers-information.component';
import { ExerciseHeaderActionsComponent } from 'app/exercise/exercise-headers/exercise-header-actions/exercise-header-actions.component';
import { ParticipationMode, ParticipationModeToggleComponent } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { PlagiarismCaseInfo } from 'app/plagiarism/shared/entities/PlagiarismCaseInfo';

@Component({
    selector: 'jhi-exercise-header',
    templateUrl: './exercise-header.component.html',
    imports: [FaIconComponent, ExerciseHeadersInformationComponent, ExerciseHeaderActionsComponent, ParticipationModeToggleComponent],
})
export class ExerciseHeaderComponent {
    readonly exercise = input.required<Exercise>();
    readonly courseId = input.required<number>();
    readonly studentParticipation = input<StudentParticipation>();
    readonly practiceParticipation = input<StudentParticipation>();
    readonly submissionPolicy = input<SubmissionPolicy>();
    readonly onSubmitExercise = input<() => void>();
    readonly plagiarismCaseInfo = input<PlagiarismCaseInfo>();
    readonly participationMode = model<ParticipationMode>('graded');

    readonly exerciseIcon = computed(() => {
        const exercise = this.exercise();
        return exercise.type ? getIcon(exercise.type) : undefined;
    });

    readonly hasPractice = computed(() => !!this.practiceParticipation());

    readonly activeParticipation = computed(() => {
        return this.participationMode() === 'practice' ? (this.practiceParticipation() ?? this.studentParticipation()) : this.studentParticipation();
    });
}
