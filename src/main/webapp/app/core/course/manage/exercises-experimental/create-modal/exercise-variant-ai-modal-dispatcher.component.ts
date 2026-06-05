import { Component, inject, input, output } from '@angular/core';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExerciseManagementDevSettingsService } from 'app/core/course/manage/exercises-experimental/dev-settings/exercise-management-dev-settings.service';
import { ExerciseVariantAiModalComponent } from './exercise-variant-ai-modal.component';
import { ExerciseVariantAiModalCardsInlineComponent } from './exercise-variant-ai-modal-cards-inline.component';
import { ExerciseVariantAiModalWizardComponent } from './exercise-variant-ai-modal-wizard.component';

@Component({
    selector: 'jhi-exercise-variant-ai-modal-dispatcher',
    templateUrl: './exercise-variant-ai-modal-dispatcher.component.html',
    imports: [ExerciseVariantAiModalComponent, ExerciseVariantAiModalCardsInlineComponent, ExerciseVariantAiModalWizardComponent],
})
export class ExerciseVariantAiModalDispatcherComponent {
    readonly visible = input<boolean>(false);
    readonly sourceExercise = input.required<Exercise>();
    readonly courseId = input<number | undefined>(undefined);

    readonly visibleChange = output<boolean>();
    readonly variantAdded = output<Exercise>();

    protected readonly devSettings = inject(ExerciseManagementDevSettingsService);
}
