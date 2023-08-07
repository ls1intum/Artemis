import { Component, EventEmitter, Input, Output } from '@angular/core';
import { HealthStatus, getWarningAction, getWarningBody, getWarningHint, getWarningTitle } from 'app/entities/competency/learning-path-health.model';

@Component({
    selector: 'jhi-learning-path-health-status-warning',
    templateUrl: './learning-path-health-status-warning.component.html',
})
export class LearningPathHealthStatusWarningComponent {
    @Input() status: HealthStatus;
    @Output() onButtonClicked: EventEmitter<void> = new EventEmitter();

    protected readonly getWarningTitle = getWarningTitle;
    protected readonly getWarningBody = getWarningBody;
    protected readonly getWarningHint = getWarningHint;
    protected readonly getWarningAction = getWarningAction;
}
