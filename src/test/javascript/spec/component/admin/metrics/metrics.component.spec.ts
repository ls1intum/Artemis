import { ComponentFixture, TestBed } from '@angular/core/testing';
import { JvmMemoryComponent } from 'app/admin/metrics/blocks/jvm-memory/jvm-memory.component';
import { JvmThreadsComponent } from 'app/admin/metrics/blocks/jvm-threads/jvm-threads.component';
import { MetricsCacheComponent } from 'app/admin/metrics/blocks/metrics-cache/metrics-cache.component';
import { MetricsDatasourceComponent } from 'app/admin/metrics/blocks/metrics-datasource/metrics-datasource.component';
import { MetricsEndpointsRequestsComponent } from 'app/admin/metrics/blocks/metrics-endpoints-requests/metrics-endpoints-requests.component';
import { MetricsGarbageCollectorComponent } from 'app/admin/metrics/blocks/metrics-garbagecollector/metrics-garbagecollector.component';
import { MetricsRequestComponent } from 'app/admin/metrics/blocks/metrics-request/metrics-request.component';
import { MetricsSystemComponent } from 'app/admin/metrics/blocks/metrics-system/metrics-system.component';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';
import { MetricsComponent } from 'app/admin/metrics/metrics.component';
import { MetricsService } from 'app/admin/metrics/metrics.service';
import { Metrics, ThreadDump } from 'app/admin/metrics/metrics.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockComponent } from 'ng-mocks';

describe('MetricsComponent', () => {
    let comp: MetricsComponent;
    let fixture: ComponentFixture<MetricsComponent>;
    let service: MetricsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [provideHttpClientTesting()],
            declarations: [
                MetricsComponent,
                MockComponent(FaIconComponent),
                MockComponent(JvmMemoryComponent),
                MockComponent(JvmThreadsComponent),
                MockComponent(MetricsSystemComponent),
                MockComponent(MetricsGarbageCollectorComponent),
                MockComponent(MetricsRequestComponent),
                MockComponent(MetricsEndpointsRequestsComponent),
                MockComponent(MetricsCacheComponent),
                MockComponent(MetricsCacheComponent),
                MockComponent(MetricsDatasourceComponent),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MetricsComponent);
                comp = fixture.componentInstance;
                service = fixture.debugElement.injector.get(MetricsService);
            });
    });

    describe('refresh', () => {
        it('should call refresh on init', () => {
            // GIVEN
            jest.spyOn(service, 'getMetrics').mockReturnValue(of({} as Metrics));

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.getMetrics).toHaveBeenCalledOnce();
        });
    });

    it('should call refresh on init', () => {
        const mockMetrics = {};
        const mockThreadDump = { threads: [] };
        jest.spyOn(service, 'getMetrics').mockReturnValue(of(mockMetrics as Metrics));
        jest.spyOn(service, 'threadDump').mockReturnValue(of(mockThreadDump as ThreadDump));
        expect(comp.updatingMetrics).toBeTrue();
        comp.ngOnInit();
        expect(service.getMetrics).toHaveBeenCalledOnce();
        expect(service.threadDump).toHaveBeenCalledOnce();
        expect(comp.updatingMetrics).toBeFalse();
        expect(comp.metrics).toEqual(mockMetrics);
        expect(comp.threads).toEqual(mockThreadDump.threads);
    });

    it('metricsKeyExists method should work correctly', () => {
        comp.metrics = {} as any as Metrics;
        expect(comp.metricsKeyExists('cache')).toBeFalse();

        comp.metrics = {
            cache: undefined,
        } as any as Metrics;
        expect(comp.metricsKeyExists('cache')).toBeFalse();

        comp.metrics = {
            cache: {},
        } as any as Metrics;
        expect(comp.metricsKeyExists('cache')).toBeTrue();
    });

    it('metricsKeyExistsAndObjectNotEmpty method should work correctly', () => {
        comp.metrics = {
            cache: undefined,
        } as any as Metrics;
        expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBeFalse();

        comp.metrics = {
            cache: {},
        } as any as Metrics;
        expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBeFalse();

        comp.metrics = {
            cache: { randomKey: {} },
        } as any as Metrics;
        expect(comp.metricsKeyExistsAndObjectNotEmpty('cache')).toBeTrue();
    });
});
