import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared-ui/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FeatureOverlayComponent } from 'app/shared-ui/components/feature-overlay/feature-overlay.component';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';

@Component({
    selector: 'jhi-exercise-detail-experimental-actions',
    templateUrl: './exercise-detail-experimental-actions.component.html',
    styleUrl: './exercise-detail-experimental-actions.component.scss',
    imports: [RouterLink, FaIconComponent, TranslateDirective, NgbTooltip, DeleteButtonDirective, ArtemisTranslatePipe, FeatureOverlayComponent],
})
export class ExerciseDetailExperimentalActionsComponent extends NonProgrammingExerciseDetailCommonActionsComponent {}
