import { Component, inject, input } from '@angular/core';
import { SidebarEventService } from '../service/sidebar-event.service';
import { ActivatedRoute, Router, RouterLink, RouterLinkActive } from '@angular/router';
import { Location } from '@angular/common';
import { SidebarCardItemComponent } from '../sidebar-card-item/sidebar-card-item.component';
import { SidebarCardElement, SidebarTypes } from 'app/foundation/types/sidebar';

@Component({
    selector: 'jhi-large-sidebar-card',
    templateUrl: './sidebar-card-large.component.html',
    styleUrls: ['./sidebar-card-large.component.scss'],
    imports: [RouterLink, RouterLinkActive, SidebarCardItemComponent],
})
export class SidebarCardLargeComponent {
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private location = inject(Location);

    readonly sidebarItem = input.required<SidebarCardElement>();
    readonly sidebarType = input<SidebarTypes>();
    readonly itemSelected = input<boolean>();
    /** Key used for grouping or categorizing sidebar items */
    readonly groupKey = input<string>();

    emitStoreAndRefresh(itemId: number | string) {
        this.sidebarEventService.emitSidebarCardEvent(itemId);
        this.refreshChildComponent();
    }

    refreshChildComponent(): void {
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            if (this.itemSelected()) {
                this.router.navigate(['./' + this.sidebarItem()?.id], { relativeTo: this.route });
            } else {
                this.router.navigate([this.location.path(), this.sidebarItem()?.id], { replaceUrl: true });
            }
        });
    }
}
