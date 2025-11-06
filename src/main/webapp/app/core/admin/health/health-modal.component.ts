import { Component, inject } from '@angular/core';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { HealthDetails, HealthKey } from 'app/core/admin/health/health.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { KeyValuePipe } from '@angular/common';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CommonModule } from '@angular/common';

@Component({
    selector: 'jhi-health-modal',
    templateUrl: './health-modal.component.html',
    imports: [TranslateDirective, KeyValuePipe, ArtemisTranslatePipe, CommonModule],
})
export class HealthModalComponent {
    private activeModal = inject(NgbActiveModal);

    health?: { key: HealthKey; value: HealthDetails };

    readableValue(value: any): string {
        if (this.health?.key === 'diskSpace') {
            // Should display storage space in a human-readable unit
            const val = value / 1073741824;
            if (val > 1) {
                return `${val.toFixed(2)} GB`;
            }
            return `${(value / 1048576).toFixed(2)} MB`;
        }

        if (typeof value === 'object') {
            return JSON.stringify(value);
        }
        return String(value);
    }

    dismiss(): void {
        this.activeModal.dismiss();
    }
}
