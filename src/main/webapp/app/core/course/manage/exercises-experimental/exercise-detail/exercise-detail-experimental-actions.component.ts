import { Component, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { FeatureOverlayComponent } from 'app/shared/components/feature-overlay/feature-overlay.component';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ExerciseVariantAiModalDispatcherComponent } from 'app/core/course/manage/exercises-experimental/create-modal/exercise-variant-ai-modal-dispatcher.component';

@Component({
    selector: 'jhi-exercise-detail-experimental-actions',
    templateUrl: './exercise-detail-experimental-actions.component.html',
    imports: [
        RouterLink,
        FaIconComponent,
        TranslateDirective,
        NgbTooltip,
        DeleteButtonDirective,
        ArtemisTranslatePipe,
        FeatureOverlayComponent,
        ExerciseVariantAiModalDispatcherComponent,
    ],
})
export class ExerciseDetailExperimentalActionsComponent extends NonProgrammingExerciseDetailCommonActionsComponent {
    readonly aiVariantModalVisible = signal(false);

    openAiVariantModal(): void {
        this.aiVariantModalVisible.set(true);
    }
}
