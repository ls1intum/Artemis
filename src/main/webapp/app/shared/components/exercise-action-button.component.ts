import { Component, HostBinding, Input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

@Component({
    selector: 'button[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../../core/course/overview/course-overview.scss'],
    imports: [FaIconComponent, NgClass],
})
export class ExerciseActionButtonComponent {
    @Input() buttonIcon: IconProp;
    @Input() buttonLabel: string;
    @Input() hideLabelMobile = true;
    @Input() overwriteDisabled = false;
    @Input() buttonLoading = false;

    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;

    @HostBinding('class.btn-primary')
    public get btnPrimary(): boolean {
        return !this.outlined;
    }

    @HostBinding('disabled')
    get disabled(): boolean {
        return this.buttonLoading || this.overwriteDisabled;
    }

    // Icons
    faCircleNotch = faCircleNotch;
}
