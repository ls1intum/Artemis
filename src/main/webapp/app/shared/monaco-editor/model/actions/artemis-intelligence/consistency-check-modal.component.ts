import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

@Component({
    selector: 'consistency-check-modal',
    templateUrl: './consistency-check-modal.component.html',
    imports: [ArtemisTranslatePipe],
})
export class ConsistencyCheckModalComponent {
    private activeModal = inject(NgbActiveModal);
    response: string;

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
