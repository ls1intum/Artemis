import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { TumAetUiMessageComponent } from '@tumaet/ui-angular';
import { faUsers } from '@fortawesome/free-solid-svg-icons';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { ParticipationMode } from 'app/exercise/exercise-headers/participation-mode-toggle/participation-mode-toggle.component';
import { isStartPracticeAvailable } from 'app/exercise/util/exercise.utils';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';

/**
 * Explains that practicing a team exercise is individual: the result of the team stays untouched and teammates cannot see the practice work.
 * It sits beside the practice action while the student can still start practice, and it reminds the student in the practice view that the work is their own.
 * It renders nothing for an individual exercise.
 */
@Component({
    selector: 'jhi-team-practice-hint',
    templateUrl: './team-practice-hint.component.html',
    imports: [TumAetUiMessageComponent, ArtemisTranslatePipe],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class TeamPracticeHintComponent {
    readonly exercise = input.required<Exercise>();
    readonly participationMode = input<ParticipationMode>('graded');
    /** The practice participation of the student, if one exists. */
    readonly practiceParticipation = input<StudentParticipation>();
    /** The graded participation of the team, which defines the individual due date. */
    readonly gradedParticipation = input<StudentParticipation>();

    protected readonly faUsers = faUsers;

    /** The key of the sentence to show, or undefined when there is nothing to explain. */
    readonly messageKey = computed(() => {
        const exercise = this.exercise();
        if (!exercise.teamMode) {
            return undefined;
        }
        if (this.participationMode() === 'practice') {
            return 'artemisApp.exerciseActions.practiceMode.teamNote';
        }
        return isStartPracticeAvailable(exercise, this.practiceParticipation(), this.gradedParticipation()) ? 'artemisApp.exerciseActions.practiceMode.teamHint' : undefined;
    });
}
