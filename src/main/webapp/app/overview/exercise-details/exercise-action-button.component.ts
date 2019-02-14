import { Component, Input } from '@angular/core';

@Component({
    selector: 'jhi-exercise-action-button',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../course-overview.scss']
})
export class ExerciseActionButtonComponent {
    @Input() buttonVisible: boolean = true;
    @Input() buttonDisabled: boolean = false;
    @Input() buttonLoading: boolean = false;
    @Input() buttonIcon: string;
    @Input() buttonLabel: string;
}
