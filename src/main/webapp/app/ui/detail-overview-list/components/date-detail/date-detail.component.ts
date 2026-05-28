import { Component, Input } from '@angular/core';
import { NoDataComponent } from 'app/ui/components/no-data/no-data-component';
import { DateDetail } from 'app/ui/detail-overview-list/detail.model';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-date-detail',
    templateUrl: 'date-detail.component.html',
    imports: [NoDataComponent, ArtemisDatePipe],
})
export class DateDetailComponent {
    @Input() detail: DateDetail;
}
