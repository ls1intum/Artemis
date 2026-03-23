import { Component, input } from '@angular/core';
import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { faCircleNotch } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'button[jhi-posting-button]',
    templateUrl: './posting-button.component.html',
    imports: [FaIconComponent],
    host: {
        class: 'btn',
        '[class.btn-outline-primary]': 'outlined()',
        '[class.btn-sm]': 'smallButton()',
    },
})
export class PostingButtonComponent {
    buttonIcon = input<IconProp>();
    buttonLabel = input<string>();
    buttonLoading = input(false);
    hideLabelMobile = input(true);
    outlined = input(false);
    smallButton = input(false);

    // Icons
    faCircleNotch = faCircleNotch;
}
