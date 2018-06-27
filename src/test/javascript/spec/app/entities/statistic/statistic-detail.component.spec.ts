/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { StatisticDetailComponent } from '../../../../../../main/webapp/app/entities/statistic/statistic-detail.component';
import { StatisticService } from '../../../../../../main/webapp/app/entities/statistic/statistic.service';
import { Statistic } from '../../../../../../main/webapp/app/entities/statistic/statistic.model';

describe('Component Tests', () => {

    describe('Statistic Management Detail Component', () => {
        let comp: StatisticDetailComponent;
        let fixture: ComponentFixture<StatisticDetailComponent>;
        let service: StatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [StatisticDetailComponent],
                providers: [
                    StatisticService
                ]
            })
            .overrideTemplate(StatisticDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(StatisticDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new Statistic(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.statistic).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
