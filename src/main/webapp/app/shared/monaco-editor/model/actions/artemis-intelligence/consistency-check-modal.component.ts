import { Component, inject, signal } from '@angular/core';
import { DomSanitizer, SafeHtml } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { htmlForMarkdown } from 'app/shared/util/markdown.conversion.util';

@Component({
    selector: 'consistency-check-modal',
    templateUrl: './consistency-check-modal.component.html',
    imports: [ArtemisTranslatePipe],
})
export class ConsistencyCheckModalComponent {
    private activeModal = inject(NgbActiveModal);
    private sanitizer = inject(DomSanitizer);

    isLoading = signal(true);
    renderedMarkdown = signal<SafeHtml>('');

    setResponse(response: string) {
        const renderedResponse = htmlForMarkdown(response);
        this.renderedMarkdown.set(this.sanitizer.bypassSecurityTrustHtml(renderedResponse));
        this.isLoading.set(false);
    }

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
