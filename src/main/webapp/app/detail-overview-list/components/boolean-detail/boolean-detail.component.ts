import { Component, Input } from '@angular/core';
import type { BooleanDetail } from 'app/detail-overview-list/detail.model';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';

@Component({
    selector: 'jhi-boolean-detail',
    templateUrl: 'boolean-detail.component.html',
    imports: [ArtemisSharedComponentModule],
})
export class BooleanDetailComponent {
    @Input() detail: BooleanDetail;
}
