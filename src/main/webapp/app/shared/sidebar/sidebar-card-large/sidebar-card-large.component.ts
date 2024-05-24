import { Component, Input } from '@angular/core';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { Subscription } from 'rxjs';
import { SidebarEventService } from '../sidebar-event.service';
import { ActivatedRoute, Router } from '@angular/router';
@Component({
    selector: 'jhi-large-sidebar-card',
    templateUrl: './sidebar-card-large.component.html',
    styleUrls: ['./sidebar-card-large.component.scss'],
})
export class SidebarCardLargeComponent {
    DifficultyLevel = DifficultyLevel;
    @Input({ required: true }) sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;

    isSelected: boolean = false;

    paramSubscription?: Subscription;
    noItemSelected: boolean = false;

    constructor(
        private sidebarEventService: SidebarEventService,
        private router: Router,
        private route: ActivatedRoute,
    ) {}

    emitStoreLastSelectedItem(itemId: number | string) {
        this.sidebarEventService.emitSidebarCardEvent(itemId);
        this.forceReload();
    }

    forceReload(): void {
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route }).then(() => {
            this.itemSelected
                ? this.router.navigate(['../' + this.sidebarItem.id], { relativeTo: this.route })
                : this.router.navigate(['./' + this.sidebarItem.id], { relativeTo: this.route });
        });
    }
}
