import { Component, input, viewChildren } from '@angular/core';
import { RouterLink } from '@angular/router';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { faPlayCircle, faUsers } from '@fortawesome/free-solid-svg-icons';
import { ExerciseActionButtonComponent } from 'app/shared-ui/components/buttons/exercise-action-button/exercise-action-button.component';
import { FeatureToggleDirective } from 'app/foundation/feature-toggle/feature-toggle.directive';
import { FeatureToggle } from 'app/foundation/feature-toggle/feature-toggle.service';
import { StartPracticeModeButtonComponent } from 'app/course/overview/exercise-details/start-practice-mode-button/start-practice-mode-button.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { InitializationState } from 'app/exercise/shared/entities/participation/participation.model';
import { StudentExerciseActionsDirective } from '../student-exercise-actions.directive';

/** Shared programming participation controls with the caller's IDE actions projected between start and resume. */
@Component({
    selector: 'div[jhi-programming-exercise-participation-actions]',
    templateUrl: './programming-exercise-participation-actions.component.html',
    imports: [RouterLink, NgbTooltip, ExerciseActionButtonComponent, FeatureToggleDirective, StartPracticeModeButtonComponent, ArtemisTranslatePipe],
})
export class ProgrammingExerciseParticipationActionsComponent {
    readonly actions = input.required<StudentExerciseActionsDirective>();
    readonly startTooltipTabIndex = input<number>();
    /** The header styles its last action as primary, including these buttons in their actual render order. */
    readonly actionButtons = viewChildren(ExerciseActionButtonComponent);
    protected readonly faUsers = faUsers;
    protected readonly faPlayCircle = faPlayCircle;
    protected readonly FeatureToggle = FeatureToggle;
    protected readonly InitializationState = InitializationState;
}
