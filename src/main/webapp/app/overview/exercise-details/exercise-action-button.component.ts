import { Component, HostBinding, Input } from '@angular/core';

@Component({
    selector: '[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../course-overview.scss']
})
export class ExerciseActionButtonComponent {
    @Input() buttonVisible = true;
    @Input() buttonIcon: string;
    @Input() buttonLabel: string;
    @Input() outlined = false;
    @Input() smallButton = false;
    @HostBinding('disabled') @Input() buttonLoading = false;

    @HostBinding('class')
    public get buttonClass(): string {
        const btnClass = ['btn'];
        btnClass.push(this.outlined ? 'btn-outline-primary' : 'btn-primary');
        if (this.smallButton) {
            btnClass.push('btn-sm');
        }
        return btnClass.join(' ');
    }

}
