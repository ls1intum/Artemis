import { Component, Input } from '@angular/core';
import type { LinkDetail } from 'app/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/shared/no-data-component';
import { RouterModule } from '@angular/router';

@Component({
    selector: 'jhi-link-detail',
    templateUrl: 'link-detail.component.html',
    standalone: true,
    imports: [NoDataComponent, RouterModule],
})
export class LinkDetailComponent {
    @Input() detail: LinkDetail;
}
