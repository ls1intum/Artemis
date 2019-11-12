import { ResultService } from 'app/entities/result';
import { RepositoryService } from 'app/entities/repository';
import { Component, OnInit } from '@angular/core';
import { ActiveFeatures, FeatureToggleService } from 'app/layouts/feature-toggle/feature-toggle.service';
import { tap } from 'rxjs/operators';

@Component({
    selector: 'jhi-feature-toggles',
    template: `
        <ng-container *ngIf="availableToggles">
            <ngx-datatable
                class="bootstrap"
                [headerHeight]="50"
                [limit]="20"
                [columnMode]="'force'"
                [footerHeight]="50"
                [rowHeight]="'auto'"
                [rows]="availableToggles"
                [sorts]="[{ prop: 'testName', dir: 'asc' }]"
            >
                <ngx-datatable-column name="Id" prop="id">
                    <ng-template ngx-datatable-cell-template let-value="value">
                        {{ value }}
                    </ng-template>
                </ngx-datatable-column>
            </ngx-datatable>
        </ng-container>
    `,
})
export class AdminFeatureToggleComponent implements OnInit {
    private availableToggles: ActiveFeatures = [];

    constructor(private featureToggleService: FeatureToggleService) {}

    ngOnInit(): void {
        this.featureToggleService
            .getFeatureToggles()
            .pipe(tap(availableToggles => (this.availableToggles = availableToggles)))
            .subscribe();
    }
}
