import { ChangeDetectionStrategy, Component, computed, input } from '@angular/core';
import { Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { DocumentationButtonComponent, DocumentationType } from 'app/shared-ui/components/buttons/documentation-button/documentation-button.component';

@Component({
    selector: 'jhi-exercise-detail-header',
    templateUrl: './exercise-detail-header.component.html',
    imports: [FaIconComponent, TranslateDirective, DocumentationButtonComponent],
    changeDetection: ChangeDetectionStrategy.OnPush,
})
export class ExerciseDetailHeaderComponent {
    readonly exercise = input.required<Exercise>();
    readonly documentationType = input.required<DocumentationType>();
    protected readonly icon = computed(() => getIcon(this.exercise().type));
    protected readonly typeLabel = computed(() => 'artemisApp.exerciseDetail.types.' + this.exercise().type);
}
