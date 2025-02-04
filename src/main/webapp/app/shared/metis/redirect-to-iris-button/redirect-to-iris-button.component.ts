import { Component, HostBinding, Input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { IrisLogoComponent } from 'app/iris/iris-logo/iris-logo.component';
import { IrisLogoSize } from 'app/iris/iris-logo/iris-logo.component';

@Component({
    selector: 'button[jhi-redirect-to-iris-button]',
    templateUrl: './redirect-to-iris-button.component.html',
    imports: [FaIconComponent, IrisLogoComponent],
})
export class RedirectToIrisButtonComponent {
    @Input() buttonIcon: IconProp;
    @Input() buttonLabel: string;
    @Input() buttonLoading = false;
    @Input() hideLabelMobile = true;

    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;

    // Icons
    faCircleNotch = faCircleNotch;
    TEXT = IrisLogoSize.TEXT;
}
