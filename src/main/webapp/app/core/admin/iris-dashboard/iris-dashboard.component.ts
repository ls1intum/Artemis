import { Component, DestroyRef, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { DecimalPipe, JsonPipe } from '@angular/common';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { PanelModule } from 'primeng/panel';
import { SelectModule } from 'primeng/select';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { IrisDashboardService } from './iris-dashboard.service';
import { IrisDashboardBreakdownEntry, IrisDashboardConfig, IrisDashboardOverview, IrisDashboardTimeSpan } from './iris-dashboard.model';
import { faRobot, faSync } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Subscription } from 'rxjs';

@Component({
    selector: 'jhi-iris-dashboard',
    standalone: true,
    imports: [DecimalPipe, JsonPipe, CardModule, TableModule, TabsModule, PanelModule, SelectModule, FormsModule, TranslateDirective, ArtemisTranslatePipe, FaIconComponent],
    templateUrl: './iris-dashboard.component.html',
    styleUrls: ['./iris-dashboard.component.scss'],
})
export class IrisDashboardComponent implements OnInit, OnDestroy {
    private dashboardService = inject(IrisDashboardService);
    private translateService = inject(TranslateService);
    private destroyRef = inject(DestroyRef);

    protected readonly faSync = faSync;
    protected readonly faRobot = faRobot;

    readonly overview = signal<IrisDashboardOverview | undefined>(undefined);
    readonly config = signal<IrisDashboardConfig | undefined>(undefined);
    readonly loading = signal(true);
    readonly error = signal(false);
    readonly activeBreakdownTab = signal(0);

    readonly chatModeBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);
    readonly courseBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);
    readonly modelBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);

    timeSpanOptions: { label: string; value: IrisDashboardTimeSpan; days: number }[] = [];

    readonly selectedTimeSpan = signal<{ label: string; value: IrisDashboardTimeSpan; days: number }>({ label: 'Month', value: 'MONTH', days: 30 });

    private readonly refreshTrigger = signal(0);

    readonly dateRange = computed(() => {
        this.refreshTrigger();
        const days = this.selectedTimeSpan().days;
        const to = new Date();
        const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
        return { from: from.toISOString(), to: to.toISOString() };
    });

    private dataSubscription?: Subscription;

    ngOnInit(): void {
        this.timeSpanOptions = [
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.day'), value: 'DAY', days: 1 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.week'), value: 'WEEK', days: 7 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.month'), value: 'MONTH', days: 30 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.quarter'), value: 'QUARTER', days: 90 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.year'), value: 'YEAR', days: 365 },
        ];
        this.selectedTimeSpan.set(this.timeSpanOptions[2]);
        this.loadData();
        this.dashboardService
            .getConfig()
            .pipe(takeUntilDestroyed(this.destroyRef))
            .subscribe((cfg) => this.config.set(cfg));
    }

    ngOnDestroy(): void {
        this.dataSubscription?.unsubscribe();
    }

    onTimeSpanChange(newValue: (typeof this.timeSpanOptions)[0]): void {
        this.selectedTimeSpan.set(newValue);
        this.refreshTrigger.update((v) => v + 1);
        this.loadData();
    }

    refresh(): void {
        this.refreshTrigger.update((v) => v + 1);
        this.loadData();
    }

    private loadData(): void {
        this.dataSubscription?.unsubscribe();
        this.loading.set(true);
        this.error.set(false);
        this.overview.set(undefined);
        this.chatModeBreakdown.set([]);
        this.courseBreakdown.set([]);
        this.modelBreakdown.set([]);
        const { from, to } = this.dateRange();

        const sub = new Subscription();

        sub.add(
            this.dashboardService.getOverview(from, to).subscribe({
                next: (data) => {
                    this.overview.set(data);
                    this.loading.set(false);
                },
                error: () => {
                    this.loading.set(false);
                    this.error.set(true);
                },
            }),
        );

        sub.add(this.dashboardService.getBreakdown(from, to, 'CHAT_MODE').subscribe({ next: (data) => this.chatModeBreakdown.set(data), error: (_err) => {} }));
        sub.add(this.dashboardService.getBreakdown(from, to, 'COURSE').subscribe({ next: (data) => this.courseBreakdown.set(data), error: (_err) => {} }));
        sub.add(this.dashboardService.getBreakdown(from, to, 'MODEL').subscribe({ next: (data) => this.modelBreakdown.set(data), error: (_err) => {} }));

        this.dataSubscription = sub;
    }
}
