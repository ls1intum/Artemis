import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { FeatureToggle, FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { NgxDatatableModule } from '@siemens/ngx-datatable';

type FeatureToggleState = {
    index: number;
    name: FeatureToggle;
    isActive: boolean;
};

/**
 * Admin component for managing feature toggles.
 * Allows administrators to enable or disable experimental features at runtime.
 */
@Component({
    selector: 'jhi-feature-toggles',
    template: `
        <ngx-datatable class="bootstrap" [headerHeight]="50" [limit]="20" [columnMode]="'force'" [footerHeight]="50" [rowHeight]="'auto'" [rows]="availableToggles()">
            <ngx-datatable-column name="Name" prop="name">
                <ng-template ngx-datatable-cell-template let-value="value">
                    {{ value }}
                </ng-template>
            </ngx-datatable-column>
            <ngx-datatable-column name="Active" prop="isActive">
                <ng-template ngx-datatable-cell-template let-row="row" let-value="value">
                    <input class="form-check-input" type="checkbox" [checked]="value" (change)="onFeatureToggle($event, row)" />
                </ng-template>
            </ngx-datatable-column>
        </ngx-datatable>
    `,
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [NgxDatatableModule],
})
export class AdminFeatureToggleComponent implements OnInit {
    private readonly featureToggleService = inject(FeatureToggleService);
    private readonly destroyRef = inject(DestroyRef);

    /** Available feature toggles with their current state */
    readonly availableToggles = signal<FeatureToggleState[]>([]);

    ngOnInit(): void {
        this.featureToggleService
            .getFeatureToggles()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((activeToggles) => {
                this.availableToggles.set(Object.values(FeatureToggle).map((name, index) => ({ name, index, isActive: activeToggles.includes(name) })));
            });
    }

    onFeatureToggle(event: any, row: FeatureToggleState) {
        const newState = !row.isActive;
        this.featureToggleService
            .setFeatureToggleState(row.name, newState)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe(() => {
                this.availableToggles.update((toggles) => toggles.map((toggle) => (toggle.name === row.name ? { ...toggle, isActive: newState } : toggle)));
            });
    }
}
