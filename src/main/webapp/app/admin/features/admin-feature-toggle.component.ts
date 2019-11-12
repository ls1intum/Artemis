import { ResultService } from 'app/entities/result';
import { RepositoryService } from 'app/entities/repository';
import { Component, OnInit } from '@angular/core';
import { ActiveFeatureToggles, FeatureToggle, FeatureToggleService } from 'app/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';

type FeatureToggleState = {
    index: number;
    name: FeatureToggle;
    isActive: boolean;
};

@Component({
    selector: 'jhi-feature-toggles',
    template: `
        <ngx-datatable class="bootstrap" [headerHeight]="50" [limit]="20" [columnMode]="'force'" [footerHeight]="50" [rowHeight]="'auto'" [rows]="availableToggles">
            <ngx-datatable-column name="index" prop="index">
                <ng-template ngx-datatable-cell-template let-value="value">
                    {{ value }}
                </ng-template>
            </ngx-datatable-column>
            <ngx-datatable-column name="name" prop="name">
                <ng-template ngx-datatable-cell-template let-value="value">
                    {{ value }}
                </ng-template>
            </ngx-datatable-column>
            <ngx-datatable-column name="isActive" prop="isActive">
                <ng-template ngx-datatable-cell-template let-row="row" let-value="value">
                    <input type="checkbox" [checked]="value" (change)="onFeatureToggle($event, row)" />
                </ng-template>
            </ngx-datatable-column>
        </ngx-datatable>
    `,
})
export class AdminFeatureToggleComponent implements OnInit {
    private availableToggles: FeatureToggleState[] = [];

    constructor(private featureToggleService: FeatureToggleService) {}

    ngOnInit(): void {
        this.featureToggleService
            .getFeatureToggles()
            .pipe(
                tap(activeToggles => {
                    this.availableToggles = Object.values(FeatureToggle).map((name, index) => ({ name, index, isActive: activeToggles.includes(name) }));
                }),
            )
            .subscribe();
    }

    onFeatureToggle(event: any, row: FeatureToggleState) {
        this.featureToggleService.setFeatureToggleState(row.name, !row.isActive).subscribe();
    }
}
