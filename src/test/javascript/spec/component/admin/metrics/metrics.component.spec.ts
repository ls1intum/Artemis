import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MetricsComponent } from 'app/admin/metrics/metrics.component';
import { Metrics, ThreadDump } from 'app/admin/metrics/metrics.model';
import { MetricsService } from 'app/admin/metrics/metrics.service';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../../test.module';

describe('MetricsComponent', () => {
    let comp: MetricsComponent;
    let fixture: ComponentFixture<MetricsComponent>;
    let service: MetricsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MetricsComponent],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(MetricsComponent);
                comp = fixture.componentInstance;
                service = fixture.debugElement.injector.get(MetricsService);
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
