import { Component, input } from '@angular/core';
import { faCheck, faCopy } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-copy-to-clipboard-button',
    imports: [FaIconComponent, CdkCopyToClipboard, ArtemisTranslatePipe, NgbTooltip],
    templateUrl: './copy-to-clipboard-button.component.html',
    styleUrl: './copy-to-clipboard-button.component.scss',
})
export class CopyToClipboardButtonComponent {
    protected readonly faCopy = faCopy;
    protected readonly faCheck = faCheck;

    readonly valueToCopyToClipboard = input.required<string>();

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
