import { Component, Input } from '@angular/core';
import { faCheckCircle, faExclamationTriangle } from '@fortawesome/free-solid-svg-icons';

@Component({
    selector: 'jhi-task-count-warning',
    templateUrl: './task-count-warning.component.html',
})
export class TaskCountWarningComponent {
    @Input() advisedMaxNumOfTasks = 15;
    @Input() numOfTasks: number;
    @Input() faExclamationTriangle = faExclamationTriangle;
    @Input() faCheckCircle = faCheckCircle;
}
