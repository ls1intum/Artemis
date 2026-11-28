import { Component, input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TumAetUiTooltipDirective } from '@tumaet/ui-angular/tooltip';
import { TranslateDirective } from 'app/foundation/language/translate.directive';

@Component({
    selector: 'jhi-task-count-warning',
    templateUrl: './task-count-warning.component.html',
    imports: [FaIconComponent, TumAetUiTooltipDirective, TranslateDirective],
})
export class TaskCountWarningComponent {
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faCheckCircle = faCheckCircle;
    readonly advisedMaxNumOfTasks = input<number>(15);
    readonly numOfTasks = input.required<number>();
}
