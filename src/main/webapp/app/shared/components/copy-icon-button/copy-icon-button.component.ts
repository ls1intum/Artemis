import { Component, Input } from '@angular/core';
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
