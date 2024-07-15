import { Component, Input } from '@angular/core';
import type { BooleanDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    selector: 'jhi-boolean-detail',
    template: `
        <dd id="detail-value-{{ detail.title }}">
            <jhi-checklist-check [checkAttribute]="detail.data.boolean" />
        </dd>
    `,
    standalone: true,
    imports: [NoDataComponent, ArtemisSharedComponentModule],
})
export class BooleanDetailComponent {
    @Input() detail: BooleanDetail;
}
