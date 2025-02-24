import { Component, HostBinding, input } from '@angular/core';
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
    buttonIcon = input<IconProp>();
    buttonLabel = input<string>();
    buttonLoading = input<boolean>(false);
    hideLabelMobile = input<boolean>(true);

    @HostBinding('class.btn-outline-primary') outlined = input<boolean>(false);
    @HostBinding('class.btn-sm') smallButton = input<boolean>(false);
    @HostBinding('class.btn') isButton = true;

    // Icons
    faCircleNotch = faCircleNotch;
    TEXT = IrisLogoSize.TEXT;
}
