import { Component, Input, OnChanges } from '@angular/core';
import { ActivatedRoute, Params, Router } from '@angular/router';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarCardElement, SidebarTypes } from 'app/types/sidebar';
import { Subscription } from 'rxjs';
@Component({
    selector: 'jhi-sidebar-card',
    templateUrl: './sidebar-card.component.html',
    styleUrls: ['./sidebar-card.component.scss'],
})
export class SidebarCardComponent implements OnChanges {
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

    ngOnChanges(): void {
        if (!this.routeParams || !Object.keys(this.routeParams).length) {
            const lastSelectedExercise = this.getLastSelectedExercise();

            if (lastSelectedExercise) {
                this.router.navigate([lastSelectedExercise], { relativeTo: this.route });
            }
        }
    }

    storeLastSelectedItem(itemId: number | string) {
        sessionStorage.setItem('sidebar.lastSelectedItem.' + this.storageId, JSON.stringify(itemId));
    }

    getLastSelectedExercise(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.' + this.storageId);
    }
}
