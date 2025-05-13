import { Component, input, signal } from '@angular/core';
import { faCopy } from '@fortawesome/free-regular-svg-icons';
import { NgbTooltip } from '@ng-bootstrap/ng-bootstrap';
import { CdkCopyToClipboard } from '@angular/cdk/clipboard';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'jhi-copy-icon-button',
    templateUrl: './copy-icon-button.component.html',
    imports: [NgbTooltip, CdkCopyToClipboard, FaIconComponent, ArtemisTranslatePipe],
})
export class CopyIconButtonComponent {
    protected readonly faCopy = faCopy;

    copyText = input.required<string>();

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
