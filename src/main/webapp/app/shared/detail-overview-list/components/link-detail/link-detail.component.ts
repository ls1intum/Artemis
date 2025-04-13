import { Component, Input } from '@angular/core';
import type { LinkDetail } from 'app/shared/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/components/no-data/no-data-component';
import { RouterModule } from '@angular/router';

@Component({
    selector: 'jhi-link-detail',
    templateUrl: 'link-detail.component.html',
    imports: [NoDataComponent, RouterModule],
})
export class LinkDetailComponent {
    @Input() detail: LinkDetail;
}
