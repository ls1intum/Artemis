import { Component, Input, OnChanges, inject } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
})
export class OpenCodeEditorButtonComponent implements OnChanges {
    private participationService = inject(ParticipationService);

    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    participations: ProgrammingExerciseStudentParticipation[];
    @Input()
    courseAndExerciseNavigationUrlSegment: any[];
    @Input()
    exercise: Exercise;

    courseAndExerciseNavigationUrl: string;
    activeParticipation: ProgrammingExerciseStudentParticipation;
    isPracticeMode: boolean = true;

    // Icons
    faFolderOpen = faFolderOpen;

    ngOnChanges() {
        this.courseAndExerciseNavigationUrl = this.courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => `${acc}/${segment}`);
        const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise);
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, shouldPreferPractice) ?? this.participations[0];
    }

    switchPracticeMode() {
        this.isPracticeMode = !this.isPracticeMode;
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, this.isPracticeMode)!;
    }
}
