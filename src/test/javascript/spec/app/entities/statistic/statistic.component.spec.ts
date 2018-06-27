/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { StatisticComponent } from '../../../../../../main/webapp/app/entities/statistic/statistic.component';
import { StatisticService } from '../../../../../../main/webapp/app/entities/statistic/statistic.service';
import { Statistic } from '../../../../../../main/webapp/app/entities/statistic/statistic.model';

describe('Component Tests', () => {

    describe('Statistic Management Component', () => {
        let comp: StatisticComponent;
        let fixture: ComponentFixture<StatisticComponent>;
        let service: StatisticService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [StatisticComponent],
                providers: [
                    StatisticService
                ]
            })
            .overrideTemplate(StatisticComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(StatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new Statistic(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.statistics[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
