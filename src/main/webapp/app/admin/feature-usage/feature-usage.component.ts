import { ChangeDetectionStrategy, Component, OnInit, computed, inject, signal } from '@angular/core';
import { toSignal } from '@angular/core/rxjs-interop';
import { FormsModule } from '@angular/forms';
import { Subscription, map } from 'rxjs';

import { faEnvelope, faSearch, faSpinner } from '@fortawesome/free-solid-svg-icons';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

import {
    TumAetUiButtonDirective,
    TumAetUiCardComponent,
    TumAetUiIconFieldComponent,
    TumAetUiInputDirective,
    TumAetUiLineChartComponent,
    TumAetUiLineChartConfig,
    TumAetUiMessageComponent,
    TumAetUiSelectButtonComponent,
    TumAetUiSelectComponent,
    TumAetUiTabComponent,
    TumAetUiTabListComponent,
    TumAetUiTabsComponent,
    TumAetUiTagComponent,
    TumAetUiToggleSwitchComponent,
} from '@tumaet/ui-angular';

import { TranslateService } from '@ngx-translate/core';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { AdminTitleBarTitleDirective } from 'app/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/admin/shared/admin-title-bar-actions.directive';
import { AlertService } from 'app/foundation/service/alert.service';
import { GraphColors } from 'app/exercise/shared/entities/statistics.model';
import { multiSeriesLineChart } from 'app/shared-ui/chart/tum-aet-ui-chart-adapters';

import { FeatureUsageService } from './feature-usage.service';
import {
    FEATURE_USAGE_CALLER_ROLES,
    FEATURE_USAGE_WINDOWS_IN_DAYS,
    FeatureAdoption,
    FeatureInteraction,
    FeatureUsageEndpoint,
    FeatureUsageOverview,
    FeatureUsageStatus,
    FeatureUsageTrendPoint,
    PRODUCT_AREAS,
    UserFeatureUsage,
} from './feature-usage.model';
import { areaTranslationKey, dailySeries, featureTranslationKey, groupAdoptionByFeature, groupEndpointsByFeature } from './feature-usage.util';
import { FeatureUsageTreeComponent, FeatureUsageTrendRequest } from './feature-usage-tree/feature-usage-tree.component';
import { FeatureUsageAttentionComponent } from './feature-usage-attention/feature-usage-attention.component';
import { FeatureUsageAdoptionComponent } from './feature-usage-adoption/feature-usage-adoption.component';
import { FeatureUsageEndpointsComponent } from './feature-usage-endpoints/feature-usage-endpoints.component';

const ALL_AREAS = '';

const ALL_ROLES = '';

/** Tab order: the feature tree first, then the list a decision starts from, then adoption, then the technical view. */
export const TAB_FEATURES = 0;
export const TAB_ATTENTION = 1;
export const TAB_ADOPTION = 2;
export const TAB_ENDPOINTS = 3;

/** What the trend card is currently charting. */
interface TrendTarget {
    /** The feature constant, or the endpoint identifier. */
    title: string;
    isFeature: boolean;
}

/**
 * Admin page for the built-in feature usage analysis.
 *
 * The page is organised around the features users know, grouped into product areas, with the modules and controllers
 * behind them one level further down. Its headline separates use, meaning actions and views, from the automatic calls the
 * client makes on its own: counted together, a status probe on a busy page made features nobody used look like the most
 * popular ones.
 */
@Component({
    selector: 'jhi-feature-usage',
    templateUrl: './feature-usage.component.html',
    changeDetection: ChangeDetectionStrategy.OnPush,
    imports: [
        FormsModule,
        FaIconComponent,
        TranslateDirective,
        ArtemisTranslatePipe,
        ArtemisDatePipe,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        TumAetUiButtonDirective,
        TumAetUiCardComponent,
        TumAetUiIconFieldComponent,
        TumAetUiInputDirective,
        TumAetUiLineChartComponent,
        TumAetUiMessageComponent,
        TumAetUiSelectButtonComponent,
        TumAetUiSelectComponent,
        TumAetUiTabsComponent,
        TumAetUiTabListComponent,
        TumAetUiTabComponent,
        TumAetUiTagComponent,
        TumAetUiToggleSwitchComponent,
        FeatureUsageTreeComponent,
        FeatureUsageAttentionComponent,
        FeatureUsageAdoptionComponent,
        FeatureUsageEndpointsComponent,
    ],
})
export class FeatureUsageComponent implements OnInit {
    private readonly featureUsageService = inject(FeatureUsageService);
    private readonly alertService = inject(AlertService);
    private readonly translateService = inject(TranslateService);

    protected readonly faSearch = faSearch;
    protected readonly faSpinner = faSpinner;
    protected readonly faEnvelope = faEnvelope;
    protected readonly TAB_FEATURES = TAB_FEATURES;
    protected readonly TAB_ATTENTION = TAB_ATTENTION;
    protected readonly TAB_ADOPTION = TAB_ADOPTION;
    protected readonly TAB_ENDPOINTS = TAB_ENDPOINTS;
    protected readonly featureTranslationKey = featureTranslationKey;

    protected readonly windowOptions = FEATURE_USAGE_WINDOWS_IN_DAYS.map((days) => ({ label: `artemisApp.featureUsage.window.days${days}`, value: days }));

    readonly loading = signal<boolean>(false);
    readonly sendingDigest = signal<boolean>(false);
    readonly overview = signal<FeatureUsageOverview | undefined>(undefined);
    readonly adoption = signal<FeatureAdoption[] | undefined>(undefined);
    readonly selectedWindow = signal<number>(30);
    readonly activeTab = signal<number>(TAB_FEATURES);
    readonly searchTerm = signal<string>('');
    readonly selectedArea = signal<string>(ALL_AREAS);
    readonly selectedCallerRole = signal<string>(ALL_ROLES);
    /**
     * Features this deployment does not offer are hidden by default. Their zero usage is not a decision, and listing all of
     * them next to the offered ones would bury the rows that are.
     */
    readonly showNotAvailable = signal<boolean>(false);

    readonly trendTarget = signal<TrendTarget | undefined>(undefined);
    readonly trendPoints = signal<FeatureUsageTrendPoint[] | undefined>(undefined);

    /**
     * The window and the role can be changed, and a chart opened, faster than the server answers. Each new request cancels
     * the one it supersedes, because a slower earlier response arriving last would otherwise overwrite the page with data
     * for a filter or a feature the controls no longer show.
     */
    private overviewSubscription?: Subscription;

    private trendSubscription?: Subscription;

    readonly features = computed<UserFeatureUsage[]>(() => this.overview()?.features ?? []);

    readonly endpoints = computed<FeatureUsageEndpoint[]>(() => this.overview()?.endpoints ?? []);

    readonly knownFeatures = computed<ReadonlySet<string>>(() => new Set(this.features().map((feature) => feature.feature)));

    readonly endpointsByFeature = computed(() => groupEndpointsByFeature(this.endpoints(), this.features()));

    readonly adoptionByFeature = computed(() => groupAdoptionByFeature(this.adoption() ?? []));

    /** Use means actions and views; automatic and system calls are reported next to it but never counted as use. */
    readonly useCount = computed<number>(() => (this.overview()?.actionCount ?? 0) + (this.overview()?.viewCount ?? 0));

    /**
     * The current language, so the labels below are translated again when it changes. They are resolved eagerly rather
     * than through a pipe, because the select renders `optionLabel` verbatim.
     */
    private readonly language = toSignal(this.translateService.onLangChange.pipe(map((event) => event.lang)), { initialValue: this.translateService.getCurrentLang() ?? '' });

    protected readonly allModulesLabel = computed(() => this.translated('artemisApp.featureUsage.allModules'));

    readonly areaOptions = computed(() => [
        { label: this.translated('artemisApp.featureUsage.allAreas'), value: ALL_AREAS },
        ...PRODUCT_AREAS.map((area) => ({ label: this.translated(areaTranslationKey(area)), value: area })),
    ]);

    readonly callerRoleOptions = computed(() => [
        { label: this.translated('artemisApp.featureUsage.allRoles'), value: ALL_ROLES },
        ...FEATURE_USAGE_CALLER_ROLES.map((role) => ({ label: role, value: role })),
    ]);

    /** The features the tree and the attention list show, after the area, search and availability filters. */
    readonly visibleFeatures = computed<UserFeatureUsage[]>(() => {
        const area = this.selectedArea();
        const term = this.searchTerm().trim().toLowerCase();
        const showNotAvailable = this.showNotAvailable();
        const endpointsByFeature = this.endpointsByFeature();
        return this.features().filter(
            (feature) =>
                (showNotAvailable || feature.status !== FeatureUsageStatus.NOT_AVAILABLE) &&
                (area === ALL_AREAS || feature.area === area) &&
                (!term || this.featureMatches(feature, endpointsByFeature.get(feature.feature) ?? [], term)),
        );
    });

    /** The endpoints the technical view shows, after the area and search filters. */
    readonly visibleEndpoints = computed<FeatureUsageEndpoint[]>(() => {
        const area = this.selectedArea();
        const term = this.searchTerm().trim().toLowerCase();
        const areaOf = new Map(this.features().map((feature) => [feature.feature, feature.area]));
        return this.endpoints().filter(
            (endpoint) =>
                (area === ALL_AREAS || (endpoint.featureLabel !== undefined && areaOf.get(endpoint.featureLabel) === area)) &&
                (!term || this.endpointMatches(endpoint, term) || (!!endpoint.featureLabel && this.featureNameMatches(endpoint.featureLabel, term))),
        );
    });

    readonly visibleAdoption = computed<FeatureAdoption[]>(() => {
        const area = this.selectedArea();
        const term = this.searchTerm().trim().toLowerCase();
        const areaOf = new Map(this.features().map((feature) => [feature.feature, feature.area]));
        return (this.adoption() ?? []).filter(
            (entry) =>
                (area === ALL_AREAS || (entry.feature !== undefined && areaOf.get(entry.feature) === area)) &&
                (!term || entry.key.toLowerCase().includes(term) || entry.module.toLowerCase().includes(term) || (!!entry.feature && this.featureNameMatches(entry.feature, term))),
        );
    });

    readonly roleDistribution = computed(() => this.overview()?.roleDistribution ?? []);

    readonly trendChartData = computed(() => {
        const points = this.trendPoints();
        const overview = this.overview();
        if (!points || !overview) {
            return undefined;
        }
        const series = (interactions: FeatureInteraction[]) => dailySeries(points, overview.from, overview.days, interactions);
        return multiSeriesLineChart(
            [
                { name: this.translateService.instant('artemisApp.featureUsage.column.actions'), series: series([FeatureInteraction.ACTION]) },
                { name: this.translateService.instant('artemisApp.featureUsage.column.views'), series: series([FeatureInteraction.VIEW]) },
                { name: this.translateService.instant('artemisApp.featureUsage.column.automatic'), series: series([FeatureInteraction.AUTOMATIC]) },
                { name: this.translateService.instant('artemisApp.featureUsage.column.system'), series: series([FeatureInteraction.SYSTEM]) },
            ],
            [GraphColors.DARK_BLUE, GraphColors.GREEN, GraphColors.GREY, GraphColors.LIGHT_GREY],
        );
    });

    /** Not a computed: it depends on nothing, so recomputing it would only pretend to be reactive. */
    readonly trendChartConfig: TumAetUiLineChartConfig = { yAxis: { min: 0 }, legend: true };

    ngOnInit(): void {
        this.load();
    }

    onWindowChanged(days: number): void {
        this.selectedWindow.set(days);
        this.closeTrend();
        this.load();
    }

    onCallerRoleChanged(callerRole: string): void {
        this.selectedCallerRole.set(callerRole);
        this.closeTrend();
        this.load();
    }

    /** Charts a feature summed over its endpoints, or a single endpoint, split into actions, views and automatic calls. */
    showTrend(request: FeatureUsageTrendRequest): void {
        this.trendSubscription?.unsubscribe();
        this.trendPoints.set(undefined);
        const callerRole = this.selectedCallerRole() === ALL_ROLES ? undefined : this.selectedCallerRole();
        let trend;
        if ('feature' in request) {
            this.trendTarget.set({ title: request.feature.feature, isFeature: true });
            trend = this.featureUsageService.getFeatureTrend(request.feature.feature, this.selectedWindow(), callerRole);
        } else {
            this.trendTarget.set({ title: request.endpoint.identifier, isFeature: false });
            trend = this.featureUsageService.getEndpointTrend([request.endpoint.featureId], this.selectedWindow(), callerRole);
        }
        this.trendSubscription = trend.subscribe({
            next: (points) => this.trendPoints.set(points),
            error: (error) => {
                // Without closing, the panel keeps spinning forever: nothing else ever sets trendPoints.
                this.alertService.error(error.message);
                this.closeTrend();
            },
        });
    }

    closeTrend(): void {
        this.trendSubscription?.unsubscribe();
        this.trendTarget.set(undefined);
        this.trendPoints.set(undefined);
    }

    /**
     * Sends the weekly digest email on demand. The scheduled job needs the scheduling profile and a configured recipient, so
     * this is how an administrator finds out whether it will actually arrive.
     */
    sendDigestEmail(): void {
        this.sendingDigest.set(true);
        this.featureUsageService.sendDigestEmail().subscribe({
            next: () => {
                this.sendingDigest.set(false);
                this.alertService.success('artemisApp.featureUsage.digestSent');
            },
            error: () => {
                this.sendingDigest.set(false);
                this.alertService.error('artemisApp.featureUsage.digestFailed');
            },
        });
    }

    private load(): void {
        this.overviewSubscription?.unsubscribe();
        // Discarded rather than left on screen. The window and role signals have already changed by the time this runs, so
        // keeping the previous report would show the old selection's numbers under the new controls with nothing marking
        // them as stale, and would leave them there indefinitely if the request fails.
        this.overview.set(undefined);
        this.loading.set(true);
        const callerRole = this.selectedCallerRole();
        this.overviewSubscription = this.featureUsageService.getOverview(this.selectedWindow(), callerRole === ALL_ROLES ? undefined : callerRole).subscribe({
            next: (overview) => {
                this.overview.set(overview);
                this.loading.set(false);
            },
            error: (error) => {
                this.loading.set(false);
                this.alertService.error(error.message);
            },
        });
        if (!this.adoption()) {
            this.featureUsageService.getAdoption().subscribe({
                next: (adoption) => this.adoption.set(adoption),
                error: (error) => this.alertService.error(error.message),
            });
        }
    }

    /**
     * A feature is found by its name and description, as users know it, by its catalogue constant, as developers write it,
     * and by anything behind it: a module, a controller or a path.
     */
    private featureMatches(feature: UserFeatureUsage, endpoints: FeatureUsageEndpoint[], term: string): boolean {
        return (
            this.featureNameMatches(feature.feature, term) ||
            this.translateService.instant(featureTranslationKey(feature.feature, 'description')).toLowerCase().includes(term) ||
            (feature.modules ?? []).some((module) => module.toLowerCase().includes(term)) ||
            endpoints.some((endpoint) => this.endpointMatches(endpoint, term))
        );
    }

    /** Translates a key in a way that makes the calling computed depend on the current language. */
    private translated(key: string): string {
        this.language();
        return this.translateService.instant(key);
    }

    private featureNameMatches(feature: string, term: string): boolean {
        return feature.toLowerCase().includes(term) || this.translateService.instant(featureTranslationKey(feature, 'name')).toLowerCase().includes(term);
    }

    private endpointMatches(endpoint: FeatureUsageEndpoint, term: string): boolean {
        return endpoint.identifier.toLowerCase().includes(term) || endpoint.module.toLowerCase().includes(term) || !!endpoint.resource?.toLowerCase().includes(term);
    }
}
