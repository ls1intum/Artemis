import { Component, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { TumUiButtonComponent } from '@tumaet/ui-angular';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exercise-group-date-notice',
    templateUrl: './exercise-group-date-notice.component.html',
    host: {
        class: 'd-block',
        'data-testid': 'exercise-group-date-notice',
    },
    imports: [FaIconComponent, TranslateDirective, TumUiButtonComponent],
})
export class ExerciseGroupDateNoticeComponent {
    readonly editGroupDates = output<void>();

    protected readonly faExclamationTriangle = faExclamationTriangle;
}
