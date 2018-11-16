/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticCounterComponent } from 'app/entities/statistic-counter/statistic-counter.component';
import { StatisticCounterService } from 'app/entities/statistic-counter/statistic-counter.service';
import { StatisticCounter } from 'app/shared/model/statistic-counter.model';

describe('Component Tests', () => {
    describe('StatisticCounter Management Component', () => {
        let comp: StatisticCounterComponent;
        let fixture: ComponentFixture<StatisticCounterComponent>;
        let service: StatisticCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticCounterComponent],
                providers: []
            })
                .overrideTemplate(StatisticCounterComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(StatisticCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticCounterService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new StatisticCounter(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.statisticCounters[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
