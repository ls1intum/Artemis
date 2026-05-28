import { Component, Input } from '@angular/core';
import type { LinkDetail } from 'app/ui/detail-overview-list/detail.model';
import { NoDataComponent } from 'app/ui/components/no-data/no-data-component';
import { RouterModule } from '@angular/router';

@Component({
    selector: 'jhi-link-detail',
    templateUrl: 'link-detail.component.html',
    imports: [NoDataComponent, RouterModule],
})
export class LinkDetailComponent {
    @Input() detail: LinkDetail;
}
