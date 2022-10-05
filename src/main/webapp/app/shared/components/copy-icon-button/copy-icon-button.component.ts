import { Component, Input } from '@angular/core';
import { faCopy } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-copy-icon-button',
    templateUrl: './copy-icon-button.component.html',
})
export class CopyIconButtonComponent {
    @Input() onCopyFinished: Function;
    @Input() onCopyFinishedKey: string;
    @Input() copyText: string;
    @Input() wasCopied: boolean;

    // Icons
    faCopy = faCopy;
}
