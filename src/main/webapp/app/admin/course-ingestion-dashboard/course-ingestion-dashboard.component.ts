import { ChangeDetectionStrategy, Component, DestroyRef, OnInit, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { TumUiTableDirective } from 'app/shared-ui/tum-ui/table-directive/tum-ui-table.directive';
import { TumUiPanelComponent } from 'app/shared-ui/tum-ui/panel/tum-ui-panel.component';
import { TumUiButtonComponent } from 'app/shared-ui/tum-ui/button/tum-ui-button.component';
import { TumUiMessageComponent } from 'app/shared-ui/tum-ui/message/tum-ui-message.component';
import { TumUiInputNumberComponent } from 'app/shared-ui/tum-ui/input-number/tum-ui-input-number.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { faDatabase, faMagnifyingGlass, faSync } from '@fortawesome/free-solid-svg-icons';
import { CourseIngestionDashboardService } from './course-ingestion-dashboard.service';
import { CourseIndexDrift, IndexOverview, TypeDrift } from './course-ingestion-dashboard.model';

@Component({
    selector: 'jhi-course-ingestion-dashboard',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DecimalPipe,
        FormsModule,
        TumUiTableDirective,
        TumUiPanelComponent,
        TumUiButtonComponent,
        TumUiMessageComponent,
        TumUiInputNumberComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
    ],
    templateUrl: './course-ingestion-dashboard.component.html',
})
export class CourseIngestionDashboardComponent implements OnInit {
    private dashboardService = inject(CourseIngestionDashboardService);
    private destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;
    protected readonly faDatabase = faDatabase;
    protected readonly faMagnifyingGlass = faMagnifyingGlass;

    readonly overview = signal<IndexOverview | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);

    readonly courseId = signal<number | undefined>(undefined);
    readonly drift = signal<CourseIndexDrift | undefined>(undefined);
    readonly driftLoading = signal(false);
    readonly driftError = signal(false);

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

    protected loadDrift(): void {
        const id = this.courseId();
        if (id === undefined) {
            return;
        }
        this.driftLoading.set(true);
        this.driftError.set(false);
        this.dashboardService
            .getCourseDrift(id)
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe({
                next: (data) => {
                    this.drift.set(data);
                    this.driftLoading.set(false);
                },
                error: () => {
                    this.driftLoading.set(false);
                    this.driftError.set(true);
                },
            });
    }

    protected driftStatus(type: TypeDrift): 'complete' | 'incomplete' | 'unknown' {
        if (type.expected === null) {
            return 'unknown';
        }
        return type.present >= type.expected ? 'complete' : 'incomplete';
    }
}
