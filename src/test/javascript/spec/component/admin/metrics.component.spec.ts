import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';

import { ArtemisTestModule } from '../../test.module';
import { MetricsComponent } from 'app/admin/metrics/metrics.component';
import { MetricsService } from 'app/admin/metrics/metrics.service';

describe('MetricsComponent', () => {
    let comp: MetricsComponent;
    let fixture: ComponentFixture<MetricsComponent>;
    let service: MetricsService;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [MetricsComponent],
        })
            .overrideTemplate(MetricsComponent, '')
            .compileComponents();
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(MetricsComponent);
        comp = fixture.componentInstance;
        service = fixture.debugElement.injector.get(MetricsService);
    });

    describe('refresh', () => {
        it('should call refresh on init', () => {
            // GIVEN
            jest.spyOn(service, 'getMetrics').mockReturnValue(of());

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.getMetrics).toHaveBeenCalled();
        });
    });
});
