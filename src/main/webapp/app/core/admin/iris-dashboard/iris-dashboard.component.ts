import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe, JsonPipe } from '@angular/common';
import { CardModule } from 'primeng/card';
import { TableModule } from 'primeng/table';
import { TabsModule } from 'primeng/tabs';
import { PanelModule } from 'primeng/panel';
import { SelectModule } from 'primeng/select';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { IrisDashboardService } from './iris-dashboard.service';
import { IrisDashboardBreakdownEntry, IrisDashboardConfig, IrisDashboardOverview, IrisDashboardTimeSpan } from './iris-dashboard.model';
import { faRobot, faSync } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

@Component({
    selector: 'jhi-iris-dashboard',
    standalone: true,
    imports: [DecimalPipe, JsonPipe, CardModule, TableModule, TabsModule, PanelModule, SelectModule, FormsModule, TranslateDirective, FaIconComponent],
    templateUrl: './iris-dashboard.component.html',
    styleUrls: ['./iris-dashboard.component.scss'],
})
export class IrisDashboardComponent implements OnInit {
    private dashboardService = inject(IrisDashboardService);

    protected readonly faSync = faSync;
    protected readonly faRobot = faRobot;

    readonly overview = signal<IrisDashboardOverview | undefined>(undefined);
    readonly config = signal<IrisDashboardConfig | undefined>(undefined);
    readonly loading = signal(true);
    readonly activeBreakdownTab = signal(0);

    readonly chatModeBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);
    readonly courseBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);
    readonly modelBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);

    readonly timeSpanOptions = [
        { label: 'Day', value: 'DAY' as IrisDashboardTimeSpan, days: 1 },
        { label: 'Week', value: 'WEEK' as IrisDashboardTimeSpan, days: 7 },
        { label: 'Month', value: 'MONTH' as IrisDashboardTimeSpan, days: 30 },
        { label: 'Quarter', value: 'QUARTER' as IrisDashboardTimeSpan, days: 90 },
        { label: 'Year', value: 'YEAR' as IrisDashboardTimeSpan, days: 365 },
    ];

    readonly selectedTimeSpan = signal(this.timeSpanOptions[2]);

    readonly dateRange = computed(() => {
        const days = this.selectedTimeSpan().days;
        const to = new Date();
        const from = new Date(to.getTime() - days * 24 * 60 * 60 * 1000);
        return { from: from.toISOString(), to: to.toISOString() };
    });

    ngOnInit(): void {
        this.loadData();
        this.dashboardService.getConfig().subscribe((cfg) => this.config.set(cfg));
    }

    onTimeSpanChange(newValue: (typeof this.timeSpanOptions)[0]): void {
        this.selectedTimeSpan.set(newValue);
        this.loadData();
    }

    refresh(): void {
        this.loadData();
    }

    private loadData(): void {
        this.loading.set(true);
        const { from, to } = this.dateRange();

        this.dashboardService.getOverview(from, to).subscribe({
            next: (data) => {
                this.overview.set(data);
                this.loading.set(false);
            },
            error: () => this.loading.set(false),
        });

        this.dashboardService.getBreakdown(from, to, 'CHAT_MODE').subscribe((data) => this.chatModeBreakdown.set(data));
        this.dashboardService.getBreakdown(from, to, 'COURSE').subscribe((data) => this.courseBreakdown.set(data));
        this.dashboardService.getBreakdown(from, to, 'MODEL').subscribe((data) => this.modelBreakdown.set(data));
    }
}
