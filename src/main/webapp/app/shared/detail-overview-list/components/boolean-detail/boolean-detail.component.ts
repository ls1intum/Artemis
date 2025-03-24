import { Component, Input } from '@angular/core';
import type { BooleanDetail } from 'app/shared/detail-overview-list/detail.model';
import { ChecklistCheckComponent } from 'app/shared/components/checklist-check/checklist-check.component';

@Component({
    selector: 'jhi-boolean-detail',
    templateUrl: 'boolean-detail.component.html',
    imports: [ChecklistCheckComponent],
})
export class BooleanDetailComponent {
    @Input() detail: BooleanDetail;
}
