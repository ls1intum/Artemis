import { Component, Input } from '@angular/core';
import { TextDetail } from 'app/detail-overview-list/detail.model';

@Component({
    selector: 'jhi-text-detail',
    templateUrl: 'text-detail.component.html',
    standalone: true,
})
export class TextDetailComponent {
    @Input() detail: TextDetail;
}
