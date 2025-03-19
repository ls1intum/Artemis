import { Component, Input } from '@angular/core';
import { NoDataComponent } from 'app/shared/no-data-component';
import { DateDetail } from 'app/shared/detail-overview-list/detail.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';

@Component({
    selector: 'jhi-date-detail',
    templateUrl: 'date-detail.component.html',
    imports: [NoDataComponent, ArtemisDatePipe],
})
export class DateDetailComponent {
    @Input() detail: DateDetail;
}
