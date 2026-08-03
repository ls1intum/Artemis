import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { TumUiPanelComponent } from 'app/shared-ui/tum-ui/panel/tum-ui-panel.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faDatabase, faSync } from '@fortawesome/free-solid-svg-icons';
import { CourseIngestionDashboardService } from './course-ingestion-dashboard.service';
import { IndexOverview } from './course-ingestion-dashboard.model';

@Component({
    selector: 'jhi-course-ingestion-dashboard',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [DecimalPipe, TumUiTableDirective, TumUiPanelComponent, TumUiButtonComponent, TumUiMessageComponent, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    templateUrl: './course-ingestion-dashboard.component.html',
})
export class CourseIngestionDashboardComponent implements OnInit {
    private dashboardService = inject(CourseIngestionDashboardService);
    private destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;
    protected readonly faDatabase = faDatabase;

    readonly overview = signal<IndexOverview | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);

    ngOnInit(): void {
        this.loadOverview();
    }

    refresh(): void {
        this.loadOverview();
    }

    private loadOverview(): void {
        this.loading.set(true);
        this.error.set(false);
        this.dashboardService
            .getOverview()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.overview.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.loading.set(false);
                    this.error.set(true);
                },
            });
    }
}
