/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { StatisticCounterComponent } from '../../../../../../main/webapp/app/entities/statistic-counter/statistic-counter.component';
import { StatisticCounterService } from '../../../../../../main/webapp/app/entities/statistic-counter/statistic-counter.service';
import { StatisticCounter } from '../../../../../../main/webapp/app/entities/statistic-counter/statistic-counter.model';

describe('Component Tests', () => {

    describe('StatisticCounter Management Component', () => {
        let comp: StatisticCounterComponent;
        let fixture: ComponentFixture<StatisticCounterComponent>;
        let service: StatisticCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [StatisticCounterComponent],
                providers: [
                    StatisticCounterService
                ]
            })
            .overrideTemplate(StatisticCounterComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(StatisticCounterComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new StatisticCounter(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.statisticCounters[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
