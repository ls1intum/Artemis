import { Component, Input, OnChanges } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
})
export class OpenCodeEditorButtonComponent implements OnChanges {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    participations: ProgrammingExerciseStudentParticipation[];
    @Input()
    courseAndExerciseNavigationUrlSegment: any[];

    courseAndExerciseNavigationUrl: string;
    activeParticipation: ProgrammingExerciseStudentParticipation;

    // Icons
    faFolderOpen = faFolderOpen;

    constructor(private participationService: ParticipationService) {}

    ngOnChanges() {
        this.courseAndExerciseNavigationUrl = this.courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => `${acc}/${segment}`);
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, true) ?? this.participations[0];
    }

    switchPracticeMode() {
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, !this.activeParticipation.testRun)!;
    }
}
