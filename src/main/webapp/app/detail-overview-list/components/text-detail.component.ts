import { Component, Input } from '@angular/core';
import type { TextDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';

@Component({
    selector: 'jhi-text-detail',
    template: `
        <dd id="detail-value-{{ detail.title }}">
            @if (detail.data.text) {
                {{ detail.data.text }}
            } @else {
                <jhi-no-data />
            }
        </dd>
    `,
    standalone: true,
    imports: [NoDataComponent],
})
export class TextDetailComponent {
    @Input() detail: TextDetail;
}
