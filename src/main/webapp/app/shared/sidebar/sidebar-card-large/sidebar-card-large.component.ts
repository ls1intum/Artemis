import { Component, Input, inject } from '@angular/core';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { SidebarEventService } from '../sidebar-event.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';

@Component({
    selector: 'jhi-large-sidebar-card',
    templateUrl: './sidebar-card-large.component.html',
    styleUrls: ['./sidebar-card-large.component.scss'],
})
export class SidebarCardLargeComponent {
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private location = inject(Location);

    @Input({ required: true }) sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;

    emitStoreAndRefresh(itemId: number | string) {
        this.sidebarEventService.emitSidebarCardEvent(itemId);
        this.refreshChildComponent();
    }

    refreshChildComponent(): void {
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route.firstChild }).then(() => {
            if (this.itemSelected) {
                this.router.navigate(['./' + this.sidebarItem?.id], { relativeTo: this.route });
            } else {
                this.router.navigate([this.location.path(), this.sidebarItem?.id], { replaceUrl: true });
            }
        });
    }
}
