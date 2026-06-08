import { Component, input } from '@angular/core';
import type { BooleanDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { ChecklistCheckComponent } from 'app/shared-ui/components/checklist-check/checklist-check.component';

@Component({
    selector: 'jhi-boolean-detail',
    templateUrl: 'boolean-detail.component.html',
    imports: [ChecklistCheckComponent],
})
export class BooleanDetailComponent {
    detail = input.required<BooleanDetail>();
}
