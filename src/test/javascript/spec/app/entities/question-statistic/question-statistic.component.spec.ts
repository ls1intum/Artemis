/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuestionStatisticComponent } from 'app/entities/quiz-question-statistic/question-statistic.component';
import { QuestionStatisticService } from 'app/entities/quiz-question-statistic/quiz-question-statistic.service';
import { QuestionStatistic } from 'app/shared/model/question-statistic.model';

describe('Component Tests', () => {
    describe('QuestionStatistic Management Component', () => {
        let comp: QuestionStatisticComponent;
        let fixture: ComponentFixture<QuestionStatisticComponent>;
        let service: QuestionStatisticService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuestionStatisticComponent],
                providers: []
            })
                .overrideTemplate(QuestionStatisticComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuestionStatisticComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuestionStatisticService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new QuestionStatistic(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.questionStatistics[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
