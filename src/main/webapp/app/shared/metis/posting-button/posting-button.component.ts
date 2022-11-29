import { Component, HostBinding, Input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';

@Component({
    /* tslint:disable-next-line component-selector */
    selector: 'button[jhi-posting-button]',
    templateUrl: './posting-button.component.html',
})
export class PostingButtonComponent {
    @Input() buttonIcon: IconProp;
    @Input() buttonLabel: string;
    @Input() buttonLoading = false;
    @Input() hideLabelMobile = true;
    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;

    // Icons
    faCircleNotch = faCircleNotch;
}
