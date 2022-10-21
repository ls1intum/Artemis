import { HttpClientTestingModule } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { JvmMemoryComponent } from 'app/admin/metrics/blocks/jvm-memory/jvm-memory.component';
import { JvmThreadsComponent } from 'app/admin/metrics/blocks/jvm-threads/jvm-threads.component';
import { MetricsCacheComponent } from 'app/admin/metrics/blocks/metrics-cache/metrics-cache.component';
import { MetricsDatasourceComponent } from 'app/admin/metrics/blocks/metrics-datasource/metrics-datasource.component';
import { MetricsEndpointsRequestsComponent } from 'app/admin/metrics/blocks/metrics-endpoints-requests/metrics-endpoints-requests.component';
import { MetricsGarbageCollectorComponent } from 'app/admin/metrics/blocks/metrics-garbagecollector/metrics-garbagecollector.component';
import { MetricsRequestComponent } from 'app/admin/metrics/blocks/metrics-request/metrics-request.component';
import { MetricsSystemComponent } from 'app/admin/metrics/blocks/metrics-system/metrics-system.component';
import { MetricsComponent } from 'app/admin/metrics/metrics.component';
import { Metrics } from 'app/admin/metrics/metrics.model';
import { MetricsService } from 'app/admin/metrics/metrics.service';
import { MockComponent } from 'ng-mocks';
import { of } from 'rxjs';

describe('MetricsComponent', () => {
    let comp: MetricsComponent;
    let fixture: ComponentFixture<MetricsComponent>;
    let service: MetricsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
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
                service = TestBed.inject(MetricsService);
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
});
