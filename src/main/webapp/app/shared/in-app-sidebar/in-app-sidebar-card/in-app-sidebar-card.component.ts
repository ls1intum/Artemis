import { Component, Input, OnChanges } from '@angular/core';
import { ActivatedRoute, Router } from '@angular/router';
import { DifficultyLevel } from 'app/entities/exercise.model';
import { Subscription } from 'rxjs';
@Component({
    selector: 'jhi-in-app-sidebar-card',
    templateUrl: './in-app-sidebar-card.component.html',
    styleUrls: ['./in-app-sidebar-card.component.scss'],
})
export class InAppSidebarCardComponent implements OnChanges {
    DifficultyLevel = DifficultyLevel;
    @Input()
    entityItem?: any;
    @Input()
    routeParams?: any;

    isSelected: boolean = false;
    exerciseId: string;

    paramSubscription?: Subscription;
    noExerciseSelected: boolean = false;

    constructor(
        private router: Router,
        private route: ActivatedRoute,
    ) {}

    ngOnChanges(): void {
        if (!Object.keys(this.routeParams).length) {
            const lastSelectedExercise = this.getLastSelectedExercise();

            if (lastSelectedExercise) {
                this.isSelected = Number(lastSelectedExercise) === this.entityItem.id;
                this.router.navigate([lastSelectedExercise], { relativeTo: this.route });
            } else {
                this.noExerciseSelected = true;
            }
        } else {
            this.exerciseId = this.routeParams.exerciseId;
            this.isSelected = Number(this.exerciseId) === this.entityItem.id;
        }
    }

    storeLastSelectedExercise(exerciseId: number) {
        sessionStorage.setItem('sidebar.lastSelectedExercise', JSON.stringify(exerciseId));
    }

    getLastSelectedExercise(): string | null {
        return sessionStorage.getItem('sidebar.lastSelectedExercise');
    }
}
