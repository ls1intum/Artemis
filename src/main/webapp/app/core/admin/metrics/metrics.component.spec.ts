/**
 * Vitest tests for MetricsComponent.
 */
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { of, throwError } from 'rxjs';

import { MetricsComponent } from 'app/core/admin/metrics/metrics.component';
import { MetricsService } from 'app/core/admin/metrics/metrics.service';
import { Metrics, NodeInfo, ThreadDump } from 'app/core/admin/metrics/metrics.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';

describe('MetricsComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: MetricsComponent;
    let fixture: ComponentFixture<MetricsComponent>;
    let service: MetricsService;

    const mockMetrics = { jvm: {}, processMetrics: {}, garbageCollector: {}, 'http.server.requests': {}, cache: {}, services: {}, databases: {} } as unknown as Metrics;
    const mockThreadDump = { threads: [{ threadName: 'main', threadState: 'RUNNABLE' }] } as unknown as ThreadDump;
    const mockNodes: NodeInfo[] = [
        { nodeId: 'node-1', label: '192.168.1.1:8080' },
        { nodeId: 'node-2', label: '192.168.1.2:8080' },
    ];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [MetricsComponent],
            providers: [provideHttpClient(), provideHttpClientTesting(), { provide: AccountService, useClass: MockAccountService }],
        })
            .overrideComponent(MetricsComponent, { set: { template: '<div></div>', styleUrl: undefined, styleUrls: [] } })
            .compileComponents();

        fixture = TestBed.createComponent(MetricsComponent);
        comp = fixture.componentInstance;
        service = TestBed.inject(MetricsService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('initialization', () => {
        it('should call refresh and loadNodes on init', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));
            vi.spyOn(service, 'getAvailableNodes').mockReturnValue(of(mockNodes));

            comp.ngOnInit();

            expect(service.getMetrics).toHaveBeenCalledOnce();
            expect(service.threadDump).toHaveBeenCalledOnce();
            expect(service.getAvailableNodes).toHaveBeenCalledOnce();
        });

        it('should load metrics and thread dump on init', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));
            vi.spyOn(service, 'getAvailableNodes').mockReturnValue(of([]));

            expect(comp.updatingMetrics()).toBe(true);

            comp.ngOnInit();

            expect(comp.updatingMetrics()).toBe(false);
            expect(comp.metrics()).toEqual(mockMetrics);
            expect(comp.threads()).toEqual(mockThreadDump.threads);
        });
    });

    describe('node selection', () => {
        it('should populate node options from available nodes', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));
            vi.spyOn(service, 'getAvailableNodes').mockReturnValue(of(mockNodes));

            comp.ngOnInit();

            const options = comp.nodeOptions();
            expect(options).toHaveLength(3); // "All Nodes" + 2 nodes
            expect(options[0].value).toBe('all');
            expect(options[0].label).toBe('All Nodes (Aggregated)');
            expect(options[1].value).toBe('node-1');
            expect(options[1].label).toContain('192.168.1.1:8080');
            expect(options[2].value).toBe('node-2');
        });

        it('should fall back to single "All Nodes" option when node list fails', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));
            vi.spyOn(service, 'getAvailableNodes').mockReturnValue(throwError(() => new Error('Network error')));

            comp.ngOnInit();

            const options = comp.nodeOptions();
            expect(options).toHaveLength(1);
            expect(options[0].value).toBe('all');
        });

        it('should pass undefined nodeId when "all" is selected', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));
            vi.spyOn(service, 'getAvailableNodes').mockReturnValue(of([]));

            comp.selectedNodeId = 'all';
            comp.refresh();

            expect(service.getMetrics).toHaveBeenCalledWith(undefined);
        });

        it('should pass specific nodeId when a node is selected', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));
            vi.spyOn(service, 'getAvailableNodes').mockReturnValue(of([]));

            comp.selectedNodeId = 'node-1';
            comp.refresh();

            expect(service.getMetrics).toHaveBeenCalledWith('node-1');
        });

        it('should refresh when node selection changes', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));

            const refreshSpy = vi.spyOn(comp, 'refresh');
            comp.onNodeChange();

            expect(refreshSpy).toHaveBeenCalledOnce();
        });
    });

    describe('refresh behavior', () => {
        it('should set updatingMetrics to true only on initial load', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));

            // Initial load: updatingMetrics should be true before data arrives
            expect(comp.metrics()).toBeUndefined();
            comp.refresh();
            // After initial load completes, updatingMetrics should be false
            expect(comp.updatingMetrics()).toBe(false);

            // Subsequent refresh: updatingMetrics should NOT be set to true (prevents flickering)
            comp.refresh();
            expect(comp.updatingMetrics()).toBe(false);
        });

        it('should update metrics in-place without clearing them on refresh', () => {
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics));
            vi.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump));

            comp.refresh();
            expect(comp.metrics()).toEqual(mockMetrics);

            // Second refresh with different data
            const updatedMetrics = { ...mockMetrics, jvm: { Heap: { committed: 100, max: 200, used: 50 } } } as unknown as Metrics;
            vi.spyOn(service, 'getMetrics').mockReturnValue(of(updatedMetrics));

            comp.refresh();
            expect(comp.metrics()).toEqual(updatedMetrics);
        });
    });

    describe('scrollToSection', () => {
        it('should scroll to element when it exists', () => {
            const mockElement = { scrollIntoView: vi.fn() };
            vi.spyOn(document, 'getElementById').mockReturnValue(mockElement as unknown as HTMLElement);

            comp.scrollToSection('jvm-metrics');

            expect(document.getElementById).toHaveBeenCalledWith('jvm-metrics');
            expect(mockElement.scrollIntoView).toHaveBeenCalledWith({ behavior: 'smooth', block: 'start' });
        });

        it('should not throw when element does not exist', () => {
            vi.spyOn(document, 'getElementById').mockReturnValue(null);

            expect(() => comp.scrollToSection('nonexistent')).not.toThrow();
        });
    });

    describe('metricsKeyExists', () => {
        it('should return false for undefined key', () => {
            comp.metrics.set({} as Metrics);
            expect(comp.metricsKeyExists('cache')).toBe(false);
        });

        it('should return false for undefined value', () => {
            comp.metrics.set({ cache: undefined } as unknown as Metrics);
            expect(comp.metricsKeyExists('cache')).toBe(false);
        });

        it('should return true for defined value', () => {
            comp.metrics.set({ cache: {} } as unknown as Metrics);
            expect(comp.metricsKeyExists('cache')).toBe(true);
        });
    });

    describe('metricsKeyExistsAndObjectNotEmpty', () => {
        it('should return false for undefined value', () => {
            comp.metrics.set({ cache: undefined } as unknown as Metrics);
            expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBe(false);
        });

        it('should return false for empty object', () => {
            comp.metrics.set({ cache: {} } as unknown as Metrics);
            expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBe(false);
        });

        it('should return true for non-empty object', () => {
            comp.metrics.set({ cache: { randomKey: {} } } as unknown as Metrics);
            expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBe(true);
        });
    });
});
