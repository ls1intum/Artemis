import { Component, HostBinding, Input } from '@angular/core';

@Component({
    selector: '[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../course-overview.scss'],
    host: {'class': 'btn'}
})
export class ExerciseActionButtonComponent {
    @Input() buttonVisible = true;
    @Input() buttonIcon: string;
    @Input() buttonLabel: string;
    @HostBinding('attr.disabled') @Input() buttonLoading = false;
    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;

    @HostBinding('class.btn-primary')
    public get btnPrimary(): boolean {
        return !this.outlined;
    }
}
