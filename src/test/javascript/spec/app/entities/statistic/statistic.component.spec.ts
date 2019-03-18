/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticComponent } from 'app/entities/quiz-statistic/statistic.component';
import { StatisticService } from 'app/entities/quiz-statistic/statistic.service';
import { Statistic } from 'app/shared/model/statistic.model';

describe('Component Tests', () => {
    describe('QuizStatistic Management Component', () => {
        let comp: StatisticComponent;
        let fixture: ComponentFixture<StatisticComponent>;
        let service: StatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticComponent],
                providers: []
            })
                .overrideTemplate(StatisticComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(StatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new Statistic(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.statistics[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
