import { Component, Input, OnInit } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { Exercise } from 'app/entities/exercise.model';
import { ProfileService } from 'app/shared/layouts/profiles/profile.service';
import { createBuildPlanUrl } from 'app/exercises/programming/shared/utils/programming-exercise.utils';

@Component({
    selector: 'jhi-programming-exercise-group-cell',
    templateUrl: './programming-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class ProgrammingExerciseGroupCellComponent implements OnInit {
    participationType = ProgrammingExerciseParticipationType;

    programmingExercise: ProgrammingExercise;

    @Input()
    displayShortName = false;
    @Input()
    displayRepositoryUrl = false;
    @Input()
    displayTemplateUrls = false;
    @Input()
    displayEditorModus = false;

    @Input()
    set exercise(exercise: Exercise) {
        this.programmingExercise = exercise as ProgrammingExercise;
    }

    constructor(private profileService: ProfileService) {}

    ngOnInit(): void {
        this.profileService.getProfileInfo().subscribe((profileInfo) => {
            if (this.programmingExercise.projectKey) {
                if (this.programmingExercise.solutionParticipation?.buildPlanId) {
                    this.programmingExercise.solutionParticipation!.buildPlanUrl = createBuildPlanUrl(
                        profileInfo.buildPlanURLTemplate,
                        this.programmingExercise.projectKey,
                        this.programmingExercise.solutionParticipation.buildPlanId,
                    );
                }
                if (this.programmingExercise.templateParticipation?.buildPlanId) {
                    this.programmingExercise.templateParticipation!.buildPlanUrl = createBuildPlanUrl(
                        profileInfo.buildPlanURLTemplate,
                        this.programmingExercise.projectKey,
                        this.programmingExercise.templateParticipation.buildPlanId,
                    );
                }
            }
        });
    }
}
