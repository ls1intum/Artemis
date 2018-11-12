/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionStatisticDetailComponent } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic-detail.component';
import { ShortAnswerQuestionStatistic } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.model';

describe('Component Tests', () => {
    describe('ShortAnswerQuestionStatistic Management Detail Component', () => {
        let comp: ShortAnswerQuestionStatisticDetailComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionStatisticDetailComponent>;
        const route = ({ data: of({ shortAnswerQuestionStatistic: new ShortAnswerQuestionStatistic(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionStatisticDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerQuestionStatisticDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerQuestionStatisticDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerQuestionStatistic).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
