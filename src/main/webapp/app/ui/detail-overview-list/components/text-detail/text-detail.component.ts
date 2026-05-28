import { Component, Input } from '@angular/core';
import type { TextDetail } from 'app/ui/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/ui/components/no-data/no-data-component';

@Component({
    selector: 'jhi-text-detail',
    templateUrl: 'text-detail.component.html',
    imports: [NoDataComponent],
})
export class TextDetailComponent {
    @Input() detail: TextDetail;
}
