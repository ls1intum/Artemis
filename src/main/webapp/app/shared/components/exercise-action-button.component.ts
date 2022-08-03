import { Component, HostBinding, Input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

@Component({
    /* tslint:disable-next-line component-selector */
    selector: 'button[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../../overview/course-overview.scss'],
})
export class ExerciseActionButtonComponent {
    @Input() buttonVisible = true;
    @Input() buttonIcon: IconProp;
    @Input() buttonLabel: string;
    @Input() hideLabelMobile = true;
    @HostBinding('disabled') @Input() buttonLoading = false;
    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;

    @HostBinding('class.btn-primary')
    public get btnPrimary(): boolean {
        return !this.outlined;
    }

    // Icons
    faCircleNotch = faCircleNotch;
}
