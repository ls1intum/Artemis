import { Component } from '@angular/core';
import { faPlus } from '@fortawesome/free-solid-svg-icons';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ExerciseManageButtonComponent } from 'app/exercise/exercise-create-buttons/exercise-manage-button/exercise-manage-button.component';
import { FeatureToggleLinkDirective } from 'app/shared/feature-toggle/feature-toggle-link.directive';

@Component({
    selector: 'jhi-exercise-create-button',
    imports: [TranslateDirective, FaIconComponent, FeatureToggleLinkDirective],
    templateUrl: './exercise-create-button.component.html',
})
export class ExerciseCreateButtonComponent extends ExerciseManageButtonComponent {
    protected readonly faPlus = faPlus;
    linkToExerciseCreation() {
        this.beforeNavigate.emit();
        this.modalService.dismissAll();
        this.router.navigate(['/course-management', this.course()?.id, this.exerciseType() + '-exercises', 'new']);
    }

    protected getTranslationSuffix(): string {
        return 'createLabel';
    }
}
