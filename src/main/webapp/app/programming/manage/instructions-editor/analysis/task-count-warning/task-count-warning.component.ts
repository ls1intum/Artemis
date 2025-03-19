import { Component, Input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';

@Component({
    selector: 'jhi-task-count-warning',
    templateUrl: './task-count-warning.component.html',
    imports: [FaIconComponent, NgbTooltip, TranslateDirective],
})
export class TaskCountWarningComponent {
    readonly faExclamationTriangle = faExclamationTriangle;
    readonly faCheckCircle = faCheckCircle;
    @Input() advisedMaxNumOfTasks = 15;
    @Input() numOfTasks: number;
}
