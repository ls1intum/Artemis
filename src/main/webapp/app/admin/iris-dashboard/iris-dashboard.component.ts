import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, computed, inject, signal } from '@angular/core';
import { DecimalPipe, JsonPipe } from '@angular/common';
import {
    TumAetUiButtonComponent,
    TumAetUiMessageComponent,
    TumAetUiPanelComponent,
    TumAetUiSelectComponent,
    TumAetUiTabComponent,
    TumAetUiTabListComponent,
    TumAetUiTableDirective,
    TumAetUiTabsComponent,
} from '@tumaet/ui-angular';
import { FormsModule } from '@angular/forms';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { IrisDashboardService } from './iris-dashboard.service';
import { IrisDashboardAiSelection, IrisDashboardTimeSpan } from './iris-dashboard.model';
import { getConfigResource } from 'app/openapi/api/iris-admin-dashboard-api';
import { IrisDashboardBreakdownEntry } from 'app/openapi/model/iris-dashboard-breakdown-entry';
import { IrisDashboardOverview } from 'app/openapi/model/iris-dashboard-overview';
import { faRobot, faSync } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Subscription } from 'rxjs';
import { IrisKpiCardComponent } from './iris-kpi-card/iris-kpi-card.component';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';

type TimeSpanOption = { label: string; value: IrisDashboardTimeSpan; days: number };
type AiSelectionOption = { label: string; value: IrisDashboardAiSelection };

@Component({
    selector: 'jhi-iris-dashboard',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        DecimalPipe,
        JsonPipe,
        TumAetUiTableDirective,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiPanelComponent,
        TumAetUiSelectComponent,
        TumAetUiButtonComponent,
        TumAetUiMessageComponent,
        FormsModule,
        TranslateDirective,
        ArtemisTranslatePipe,
        FaIconComponent,
        IrisKpiCardComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
    ],
    templateUrl: './iris-dashboard.component.html',
    styleUrls: ['./iris-dashboard.component.scss'],
})
export class IrisDashboardComponent implements OnInit, OnDestroy {
    private dashboardService = inject(IrisDashboardService);
    private translateService = inject(TranslateService);

    protected readonly faSync = faSync;
    protected readonly faRobot = faRobot;

    readonly overview = signal<IrisDashboardOverview | undefined>(undefined);
    private readonly configResource = getConfigResource();
    readonly config = computed(() => (this.configResource.hasValue() ? this.configResource.value() : undefined));
    readonly loading = signal(true);
    readonly error = signal(false);
    readonly activeBreakdownTab = signal(0);

    onBreakdownTabChange(value: number | string | undefined): void {
        if (typeof value === 'number') {
            this.activeBreakdownTab.set(value);
        }
    }

    readonly chatModeBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);
    readonly courseBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);
    readonly modelBreakdown = signal<IrisDashboardBreakdownEntry[]>([]);

    readonly timeSpanOptions = signal<TimeSpanOption[]>([]);

    readonly selectedTimeSpan = signal<TimeSpanOption>({ label: 'Month', value: 'MONTH', days: 30 });

    readonly aiSelectionOptions = signal<AiSelectionOption[]>([]);

    readonly selectedAiSelection = signal<AiSelectionOption>({ label: 'All', value: 'ALL' });

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
        this.timeSpanOptions.set([
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.day'), value: 'DAY', days: 1 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.week'), value: 'WEEK', days: 7 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.month'), value: 'MONTH', days: 30 },
            { label: this.translateService.instant('artemisApp.irisDashboard.timeSpan.quarter'), value: 'QUARTER', days: 90 },
        ]);
        this.selectedTimeSpan.set(this.timeSpanOptions()[2]);
        this.aiSelectionOptions.set([
            { label: this.translateService.instant('artemisApp.irisDashboard.aiSelection.all'), value: 'ALL' },
            { label: this.translateService.instant('artemisApp.irisDashboard.aiSelection.cloud'), value: 'CLOUD' },
            { label: this.translateService.instant('artemisApp.irisDashboard.aiSelection.local'), value: 'LOCAL' },
        ]);
        this.selectedAiSelection.set(this.aiSelectionOptions()[0]);
        this.loadData();
    }

    ngOnDestroy(): void {
        this.dataSubscription?.unsubscribe();
    }

    onTimeSpanChange(newValue: TimeSpanOption): void {
        this.selectedTimeSpan.set(newValue);
        this.refreshTrigger.update((v) => v + 1);
        this.loadData();
    }

    onAiSelectionChange(newValue: AiSelectionOption): void {
        this.selectedAiSelection.set(newValue);
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
        const aiSelection = this.selectedAiSelection().value;

        const sub = new Subscription();

        sub.add(
            this.dashboardService.getOverview(from, to, aiSelection).subscribe({
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

        sub.add(this.dashboardService.getBreakdown(from, to, 'CHAT_MODE', aiSelection).subscribe({ next: (data) => this.chatModeBreakdown.set(data), error: (_err) => {} }));
        sub.add(this.dashboardService.getBreakdown(from, to, 'COURSE', aiSelection).subscribe({ next: (data) => this.courseBreakdown.set(data), error: (_err) => {} }));
        sub.add(this.dashboardService.getBreakdown(from, to, 'MODEL', aiSelection).subscribe({ next: (data) => this.modelBreakdown.set(data), error: (_err) => {} }));

        this.dataSubscription = sub;
    }
}
