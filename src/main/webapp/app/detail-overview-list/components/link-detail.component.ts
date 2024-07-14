import { Component, Input } from '@angular/core';
import type { LinkDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';
import { RouterModule } from '@angular/router';

@Component({
    selector: 'jhi-link-detail',
    template: `
        <dd id="detail-value-{{ detail.title }}">
            @if (detail.data.text) {
                @if (detail.data.routerLink?.length) {
                    <a [routerLink]="detail.data.routerLink" [queryParams]="detail.data.queryParams">
                        {{ detail.data.text }}
                    </a>
                } @else if (detail.data.href) {
                    <a href="{{ detail.data.href }}">
                        {{ detail.data.text }}
                    </a>
                } @else {
                    <span>{{ detail.data.text }}</span>
                }
            } @else {
                <jhi-no-data />
            }
        </dd>
    `,
    standalone: true,
    imports: [NoDataComponent, RouterModule],
})
export class LinkDetailComponent {
    @Input() detail: LinkDetail;
}
