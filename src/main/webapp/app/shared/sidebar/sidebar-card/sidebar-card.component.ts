import { Component, Input, OnChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { SidebarTypes } from 'app/types/sidebar';
import { Subscription } from 'rxjs';
@Component({
    selector: 'jhi-sidebar-card',
    templateUrl: './sidebar-card.component.html',
    styleUrls: ['./sidebar-card.component.scss'],
})
export class SidebarCardComponent implements OnChanges {
    DifficultyLevel = DifficultyLevel;
    @Input()
    entityItem?: any;
    @Input()
    routeParams?: any;
    @Input() sidebarType?: SidebarTypes;
    @Input() storageId?: string = '';

    isSelected: boolean = false;
    itemId: string;

    paramSubscription?: Subscription;
    noItemSelected: boolean = false;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
    ) {}

    ngOnChanges(): void {
        //Double Check if I need this
        if (!Object.keys(this.routeParams).length) {
            const lastSelectedExercise = this.getLastSelectedExercise();

            if (lastSelectedExercise) {
                this.isSelected = Number(lastSelectedExercise) === this.entityItem.id;
                this.router.navigate([lastSelectedExercise], { relativeTo: this.route });
            } else {
                this.noItemSelected = true;
            }
        } else {
            this.itemId = this.routeParams.exerciseId;
            this.isSelected = Number(this.itemId) === this.entityItem.id;
        }
    }

    storeLastSelectedItem(exerciseId: number) {
        sessionStorage.setItem('sidebar.lastSelectedItem.' + this.storageId, JSON.stringify(exerciseId));
    }

    getLastSelectedExercise(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedItem.' + this.storageId);
    }
}
