import { Component, Input } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { Subscription } from 'rxjs';
@Component({
    selector: 'jhi-sidebar-card',
    templateUrl: './sidebar-card.component.html',
    styleUrls: ['./sidebar-card.component.scss'],
})
export class SidebarCardComponent {
    DifficultyLevel = DifficultyLevel;
    @Input() sidebarItem: SidebarCardElement;
    @Input() routeParams: Params = [];
    @Input() sidebarType?: SidebarTypes;
    @Input() storageId?: string = '';

    isSelected: boolean = false;

    paramSubscription?: Subscription;
    noItemSelected: boolean = false;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
    ) {}

    storeLastSelectedItem(itemId: number | string) {
        sessionStorage.setItem('sidebar.lastSelectedItem.' + this.storageId, JSON.stringify(itemId));
    }
}
