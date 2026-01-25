import { Component, HostBinding, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgClass } from '@angular/common';

@Component({
    selector: 'button[jhi-exercise-action-button]',
    templateUrl: './exercise-action-button.component.html',
    styleUrls: ['../../../../core/course/overview/course-overview/course-overview.scss'],
    imports: [FaIconComponent, NgClass],
})
export class ExerciseActionButtonComponent {
    buttonIcon = input<IconProp>(undefined!);
    buttonLabel = input<string>(undefined!);
    hideLabelMobile = input(true);
    overwriteDisabled = input(false);
    buttonLoading = input(false);

    @HostBinding('class.btn-outline-primary')
    outlined = input(false);
    @HostBinding('class.btn-sm')
    smallButton = input(false);
    @HostBinding('class.btn') isButton = true;

    @HostBinding('class.btn-primary')
    public get btnPrimary(): boolean {
        return !this.outlined();
    }

    @HostBinding('disabled')
    get disabled(): boolean {
        return this.buttonLoading() || this.overwriteDisabled();
    }

    // Icons
    faCircleNotch = faCircleNotch;
}
