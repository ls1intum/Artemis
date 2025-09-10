import { Component, input, signal } from '@angular/core';
import { faCopy } from '@fortawesome/free-regular-svg-icons';
import { faCheck } from '@fortawesome/free-solid-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-copy-to-clipboard-button',
    templateUrl: './copy-to-clipboard-button.component.html',
    imports: [NgbTooltip, CdkCopyToClipboard, FaIconComponent, ArtemisTranslatePipe],
})
export class CopyToClipboardButtonComponent {
    protected readonly faCopy = faCopy;
    protected readonly faCheck = faCheck;

    copyText = input.required<string>();
    class = input<string>('');

    wasCopied = signal<boolean>(false);

    /**
     * set wasCopied for 2 seconds
     */
    onCopyFinished = (): void => {
        this.wasCopied.set(true);
        setTimeout(() => {
            this.wasCopied.set(false);
        }, 2000);
    };
}
