import { Component, HostBinding, Input, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'button[jhi-posting-button]',
    templateUrl: './posting-button.component.html',
    imports: [FaIconComponent],
})
export class PostingButtonComponent {
    // TODO: Skipped for migration because:
    //  This input is used in a control flow expression (e.g. `@if` or `*ngIf`)
    //  and migrating would break narrowing currently.
    @Input() buttonIcon: IconProp;
    readonly buttonLabel = input<string>(undefined!);
    readonly buttonLoading = input(false);
    readonly hideLabelMobile = input(true);

    // TODO: Skipped for migration because:
    //  This input is used in combination with `@HostBinding` and migrating would
    //  break.
    @HostBinding('class.btn-outline-primary') @Input() outlined = false;
    // TODO: Skipped for migration because:
    //  This input is used in combination with `@HostBinding` and migrating would
    //  break.
    @HostBinding('class.btn-sm') @Input() smallButton = false;
    @HostBinding('class.btn') isButton = true;

    // Icons
    faCircleNotch = faCircleNotch;
}
