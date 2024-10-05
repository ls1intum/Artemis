import { Component, EventEmitter, Input, Output, inject } from '@angular/core';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { SidebarEventService } from '../sidebar-event.service';
import { ActivatedRoute, Router } from '@angular/router';
import { Location } from '@angular/common';

@Component({
    selector: 'jhi-small-sidebar-card',
    templateUrl: './sidebar-card-small.component.html',
    styleUrls: ['./sidebar-card-small.component.scss'],
})
export class SidebarCardSmallComponent {
    private sidebarEventService = inject(SidebarEventService);
    private router = inject(Router);
    private route = inject(ActivatedRoute);
    private location = inject(Location);

    DifficultyLevel = DifficultyLevel;
    @Output() onUpdateSidebar = new EventEmitter<void>();
    @Input({ required: true }) sidebarItem: SidebarCardElement;
    @Input() sidebarType?: SidebarTypes;
    @Input() itemSelected?: boolean;

    emitStoreAndRefresh(itemId: number | string) {
        this.sidebarEventService.emitSidebarCardEvent(itemId);
        if (this.sidebarType !== 'conversation') {
            this.refreshChildComponent();
        }
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
