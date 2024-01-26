import { Component, Input } from '@angular/core';
import { ProgrammingExerciseStudentParticipation } from 'app/entities/participation/programming-exercise-student-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { FeatureToggle } from 'app/shared/feature-toggle/feature-toggle.service';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-open-repository-button',
    templateUrl: './open-repository-button.component.html',
    styleUrl: './open-repository-button.component.scss',
})
export class OpenRepositoryButtonComponent {
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
    isPracticeMode: boolean | undefined;

    // Icons
    faArrowUpRightFromSquare = faArrowUpRightFromSquare;

    constructor(private participationService: ParticipationService) {}

    ngOnChanges() {
        this.courseAndExerciseNavigationUrl = this.courseAndExerciseNavigationUrlSegment.reduce((acc, segment) => `${acc}/${segment}`);
        const shouldPreferPractice = this.participationService.shouldPreferPractice(this.exercise);
        this.activeParticipation = this.participationService.getSpecificStudentParticipation(this.participations, shouldPreferPractice) ?? this.participations[0];
    }
}
