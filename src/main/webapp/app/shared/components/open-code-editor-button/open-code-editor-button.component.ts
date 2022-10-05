import { Component, Input, OnInit } from '@angular/core';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faFolderOpen } from '@fortawesome/free-solid-svg-icons';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';

@Component({
    selector: 'jhi-open-code-editor-button',
    templateUrl: './open-code-editor-button.component.html',
})
export class OpenCodeEditorButtonComponent implements OnInit {
    readonly FeatureToggle = FeatureToggle;

    @Input()
    loading = false;
    @Input()
    smallButtons: boolean;
    @Input()
    exercise: ProgrammingExercise;
    @Input()
    courseAndExerciseNavigationUrlSegment: any[];

    participations: ProgrammingExerciseStudentParticipation[];
    courseAndExerciseNavigationUrl: string;
    activeParticipation: ProgrammingExerciseStudentParticipation;

    // Icons
    faFolderOpen = faFolderOpen;

    constructor(private participationService: ParticipationService) {}

    ngOnInit() {
        this.participations = this.exercise.studentParticipations!;
        this.courseAndExerciseNavigationUrl = this.courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => acc + '/' + segment);
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, true) ?? this.participations[0];
    }

    switchPracticeMode() {
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations!, !this.activeParticipation.testRun)!;
    }
}
