import { Component, OnInit, inject, signal } from '@angular/core';
import { combineLatest } from 'rxjs';
import { faSync } from '@fortawesome/free-solid-svg-icons';
import { FormsModule } from '@angular/forms';

import { MetricsService } from './metrics.service';
import { Metrics, NodeInfo, Thread } from 'app/core/admin/metrics/metrics.model';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JvmMemoryComponent } from './blocks/jvm-memory/jvm-memory.component';
import { JvmThreadsComponent } from './blocks/jvm-threads/jvm-threads.component';
import { MetricsSystemComponent } from './blocks/metrics-system/metrics-system.component';
import { MetricsGarbageCollectorComponent } from './blocks/metrics-garbagecollector/metrics-garbagecollector.component';
import { MetricsRequestComponent } from './blocks/metrics-request/metrics-request.component';
import { MetricsEndpointsRequestsComponent } from './blocks/metrics-endpoints-requests/metrics-endpoints-requests.component';
import { MetricsCacheComponent } from './blocks/metrics-cache/metrics-cache.component';
import { MetricsDatasourceComponent } from './blocks/metrics-datasource/metrics-datasource.component';
import { AdminTitleBarTitleDirective } from 'app/core/admin/shared/admin-title-bar-title.directive';
import { AdminTitleBarActionsDirective } from 'app/core/admin/shared/admin-title-bar-actions.directive';
import { SelectModule } from 'primeng/select';

interface NodeOption {
    label: string;
    value: string;
}

@Component({
    selector: 'jhi-metrics',
    templateUrl: './metrics.component.html',
    styleUrl: './metrics.component.scss',
    imports: [
        TranslateDirective,
        FaIconComponent,
        JvmMemoryComponent,
        JvmThreadsComponent,
        MetricsSystemComponent,
        MetricsGarbageCollectorComponent,
        MetricsRequestComponent,
        MetricsEndpointsRequestsComponent,
        MetricsCacheComponent,
        MetricsDatasourceComponent,
        AdminTitleBarTitleDirective,
        AdminTitleBarActionsDirective,
        SelectModule,
        FormsModule,
    ],
})
export class MetricsComponent implements OnInit {
    private readonly metricsService = inject(MetricsService);

    /** Current metrics data */
    readonly metrics = signal<Metrics | undefined>(undefined);

    /** Thread dump data */
    readonly threads = signal<Thread[]>([]);

    /** Whether metrics are currently being updated */
    readonly updatingMetrics = signal(true);

    /** Available cluster nodes for the dropdown */
    readonly nodeOptions = signal<NodeOption[]>([]);

    /** Currently selected node ID ('all' for aggregated view) */
    selectedNodeId = 'all';

    /** Icons */
    protected readonly faSync = faSync;

    /**
     * Loads available nodes and fetches metrics on init
     */
    ngOnInit() {
        this.loadNodes();
        this.refresh();
    }

    /**
     * Loads the list of available cluster nodes for the dropdown
     */
    loadNodes(): void {
        this.metricsService.getAvailableNodes().subscribe({
            next: (nodes: NodeInfo[]) => {
                const options: NodeOption[] = [{ label: 'All Nodes (Aggregated)', value: 'all' }];
                nodes.forEach((node, index) => {
                    options.push({ label: `Node ${index + 1} (${node.label})`, value: node.nodeId });
                });
                this.nodeOptions.set(options);
            },
            error: () => {
                this.nodeOptions.set([{ label: 'All Nodes', value: 'all' }]);
            },
        });
    }

    /**
     * Called when the node dropdown selection changes
     */
    onNodeChange(): void {
        this.refresh();
    }

    /**
     * Refreshes the metrics in-place without removing DOM content.
     * Sets updatingMetrics only on initial load (when metrics is undefined).
     */
    refresh(): void {
        const isInitialLoad = !this.metrics();
        if (isInitialLoad) {
            this.updatingMetrics.set(true);
        }
        const nodeId = this.selectedNodeId !== 'all' ? this.selectedNodeId : undefined;
        combineLatest([this.metricsService.getMetrics(nodeId), this.metricsService.threadDump()]).subscribe(([metrics, threadDump]) => {
            this.metrics.set(metrics);
            this.threads.set(threadDump.threads);
            this.updatingMetrics.set(false);
        });
    }

    /**
     * Smoothly scrolls to a section by its element ID
     */
    scrollToSection(sectionId: string): void {
        const element = document.getElementById(sectionId);
        if (element) {
            element.scrollIntoView({ behavior: 'smooth', block: 'start' });
        }
    }

    /**
     * Checks if the metric with the key {@param key} exists
     * @param key string identifier of a metric
     */
    metricsKeyExists(key: keyof Metrics): boolean {
        return Boolean(this.metrics()?.[key]);
    }

    /**
     * Checks if the metric with the key {@param key} exists and is not empty
     * @param key key string identifier of a metric
     */
    metricsKeyExistsAndObjectNotEmpty(key: keyof Metrics): boolean {
        const m = this.metrics();
        return Boolean(m?.[key] && JSON.stringify(m[key]) !== '{}');
    }
}
