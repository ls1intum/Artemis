import { Component, input } from '@angular/core';
import { faCopy } from '@fortawesome/free-solid-svg-icons';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';

@Component({
    selector: 'jhi-copy-to-clipboard-button',
    imports: [CdkCopyToClipboard],
    templateUrl: './copy-to-clipboard-button.component.html',
    styleUrl: './copy-to-clipboard-button.component.scss',
})
export class CopyToClipboardButtonComponent {
    protected readonly faCopy = faCopy;

    valueToCopyToClipboard = input.required<string>();

    wasCopied = false;

    /**
     * set wasCopied for 3 seconds on success
     */
    onCopyFinished(successful: boolean) {
        if (successful) {
            this.wasCopied = true;
            setTimeout(() => {
                this.wasCopied = false;
            }, 3000);
        }
    }
}
