/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionStatisticComponent } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.component';
import { ShortAnswerQuestionStatisticService } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.service';
import { ShortAnswerQuestionStatistic } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.model';

describe('Component Tests', () => {
    describe('ShortAnswerQuestionStatistic Management Component', () => {
        let comp: ShortAnswerQuestionStatisticComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionStatisticComponent>;
        let service: ShortAnswerQuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionStatisticComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerQuestionStatisticComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerQuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerQuestionStatisticService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerQuestionStatistic(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerQuestionStatistics[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
