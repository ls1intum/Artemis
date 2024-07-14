import { Component, Input } from '@angular/core';
import { NoDataComponent } from 'app/shared/no-data-component';
import { DateDetail } from 'app/detail-overview-list/detail.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-date-detail',
    template: `
        <dd id="detail-value-{{ detail.title }}">
            @if (detail.data.date) {
                {{ detail.data.date | artemisDate }}
            } @else {
                <jhi-no-data />
            }
        </dd>
    `,
    standalone: true,
    imports: [NoDataComponent, ArtemisSharedModule],
})
export class DateDetailComponent {
    @Input() detail: DateDetail;
}
