import { Component, Input } from '@angular/core';
import { NoDataComponent } from 'app/shared/no-data-component';
import { DateDetail } from 'app/detail-overview-list/detail.model';
import { ArtemisSharedModule } from 'app/shared/shared.module';

@Component({
    selector: 'jhi-date-detail',
    templateUrl: 'date-detail.component.html',
    standalone: true,
    imports: [NoDataComponent, ArtemisSharedModule],
})
export class DateDetailComponent {
    @Input() detail: DateDetail;
}
