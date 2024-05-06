import { Component, Input } from '@angular/core';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { Subscription } from 'rxjs';
import { SidebarEventService } from '../sidebar-event.service';
import { ActivatedRoute, Router } from '@angular/router';
@Component({
    selector: 'jhi-sidebar-card',
    templateUrl: './sidebar-card.component.html',
    styleUrls: ['./sidebar-card.component.scss'],
})
export class SidebarCardComponent {
    DifficultyLevel = DifficultyLevel;
    @Input() sidebarItem: SidebarCardElement;
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
    }

    forceReload(): void {
        this.router.navigate(['../'], { skipLocationChange: true, relativeTo: this.route }).then(() => {
            this.itemSelected
                ? this.router.navigate(['../' + this.sidebarItem?.id], { relativeTo: this.route })
                : this.router.navigate(['./' + this.sidebarItem?.id], { relativeTo: this.route });
        });
    }
}
