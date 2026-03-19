import { Component, HostBinding, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'button[jhi-posting-button]',
    templateUrl: './posting-button.component.html',
    imports: [FaIconComponent],
})
export class PostingButtonComponent {
    readonly buttonIcon = input<IconProp>();
    readonly buttonLabel = input.required<string>();
    readonly buttonLoading = input(false);
    readonly hideLabelMobile = input(true);

    readonly outlined = input(false);
    readonly smallButton = input(false);
    @HostBinding('class.btn') isButton = true;

    @HostBinding('class.btn-outline-primary')
    get outlinedClass() {
        return this.outlined();
    }

    @HostBinding('class.btn-sm')
    get smallButtonClass() {
        return this.smallButton();
    }

    // Icons
    faCircleNotch = faCircleNotch;
}
