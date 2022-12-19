import { Component, Input } from '@angular/core';
import { faCopy } from '@fortawesome/free-regular-svg-icons';

@Component({
    selector: 'jhi-copy-icon-button',
    templateUrl: './copy-icon-button.component.html',
})
export class CopyIconButtonComponent {
    @Input() copyText: string;

    wasCopied = false;

    // Icons
    faCopy = faCopy;

    /**
     * set wasCopied for 2 seconds
     */
    onCopyFinished = (): void => {
        this.wasCopied = true;
        setTimeout(() => {
            this.wasCopied = false;
        }, 2000);
    };
}
