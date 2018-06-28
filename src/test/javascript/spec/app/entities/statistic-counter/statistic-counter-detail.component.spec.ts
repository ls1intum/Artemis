/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { StatisticCounterDetailComponent } from '../../../../../../main/webapp/app/entities/statistic-counter/statistic-counter-detail.component';
import { StatisticCounterService } from '../../../../../../main/webapp/app/entities/statistic-counter/statistic-counter.service';
import { StatisticCounter } from '../../../../../../main/webapp/app/entities/statistic-counter/statistic-counter.model';

describe('Component Tests', () => {

    describe('StatisticCounter Management Detail Component', () => {
        let comp: StatisticCounterDetailComponent;
        let fixture: ComponentFixture<StatisticCounterDetailComponent>;
        let service: StatisticCounterService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [StatisticCounterDetailComponent],
                providers: [
                    StatisticCounterService
                ]
            })
            .overrideTemplate(StatisticCounterDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(StatisticCounterDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticCounterService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new StatisticCounter(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.statisticCounter).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
