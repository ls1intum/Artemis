import { Component, HostBinding, Input } from '@angular/core';

@Component({
    selector: '[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../course-overview.scss']
})
export class ExerciseActionButtonComponent {
    @Input() buttonVisible: boolean = true;
    @Input() buttonIcon: string;
    @Input() buttonLabel: string;
    @Input() outlined: boolean = false;
    @HostBinding('disabled') @Input() buttonLoading: boolean = false;

    @HostBinding('class')
    public get buttonClass(): string {
        return this.outlined ? 'btn btn-outline-primary' : 'btn btn-primary';
    }

}
