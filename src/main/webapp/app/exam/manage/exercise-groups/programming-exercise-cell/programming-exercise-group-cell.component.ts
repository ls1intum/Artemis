import { Component, Input } from '@angular/core';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ProgrammingExerciseSimulationUtils } from 'app/exercises/programming/shared/utils/programming-exercise-simulation-utils';
import { ProgrammingExerciseParticipationType } from 'app/entities/programming-exercise-participation.model';
import { Exercise } from 'app/entities/exercise.model';

@Component({
    selector: 'jhi-programming-exercise-group-cell',
    templateUrl: './programming-exercise-group-cell.component.html',
    styles: [':host{display: contents}'],
})
export class ProgrammingExerciseGroupCellComponent {
    participationType = ProgrammingExerciseParticipationType;

    programmingExercise: ProgrammingExercise;

    @Input()
    set exercise(exercise: Exercise) {
        this.programmingExercise = exercise as ProgrammingExercise;
    }

    constructor(private programmingExerciseSimulationUtils: ProgrammingExerciseSimulationUtils) {}

    // ################## ONLY FOR LOCAL TESTING PURPOSE -- START ##################

    /**
     * Checks if the url includes the string "nolocalsetup', which is an indication
     * that the particular programming exercise has no local setup
     * This functionality is only for testing purposes (noVersionControlAndContinuousIntegrationAvailable)
     * @param urlToCheck the url which will be check if it contains the substring
     */
    noVersionControlAndContinuousIntegrationAvailableCheck(urlToCheck: string): boolean {
        return this.programmingExerciseSimulationUtils.noVersionControlAndContinuousIntegrationAvailableCheck(urlToCheck);
    }

    // ################## ONLY FOR LOCAL TESTING PURPOSE -- END ##################
}
