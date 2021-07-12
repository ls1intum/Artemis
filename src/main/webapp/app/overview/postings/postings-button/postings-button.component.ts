import { Component, HostBinding, Input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';

@Component({
    /* tslint:disable-next-line component-selector */
    selector: 'button[jhi-postings-button]',
    templateUrl: './postings-button.component.html',
})
export class PostingsButtonComponent {
    @Input() buttonVisible = true;
    @Input() buttonIcon: IconProp;
    @Input() buttonLabel: string;
    @Input() buttonLoading = false;
    @Input() hideLabelMobile = true;
    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;
}
