import { Component, Input } from '@angular/core';
import type { TextDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';

@Component({
    selector: 'jhi-text-detail',
    templateUrl: 'text-detail.component.html',
    standalone: true,
    imports: [NoDataComponent],
})
export class TextDetailComponent {
    @Input() detail: TextDetail;
}
