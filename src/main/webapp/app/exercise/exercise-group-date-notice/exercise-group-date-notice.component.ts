import { TumAetUiButtonDirective } from '@tumaet/ui-angular/button';
import { Component, output } from '@angular/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faExclamationTriangle } from '@fortawesome/free-solid-svg-icons/faExclamationTriangle';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-exercise-group-date-notice',
    templateUrl: './exercise-group-date-notice.component.html',
    styleUrl: './exercise-group-date-notice.component.css',
    host: {
        class: 'block',
        'data-testid': 'exercise-group-date-notice',
    },
    imports: [TumAetUiButtonDirective, FaIconComponent, TranslateDirective],
})
export class ExerciseGroupDateNoticeComponent {
    readonly editGroupDates = output<void>();

    protected readonly faExclamationTriangle = faExclamationTriangle;
}
