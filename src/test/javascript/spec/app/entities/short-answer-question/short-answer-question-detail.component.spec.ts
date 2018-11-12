/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionDetailComponent } from 'app/entities/short-answer-question/short-answer-question-detail.component';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question/short-answer-question.model';

describe('Component Tests', () => {
    describe('ShortAnswerQuestion Management Detail Component', () => {
        let comp: ShortAnswerQuestionDetailComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionDetailComponent>;
        const route = ({ data: of({ shortAnswerQuestion: new ShortAnswerQuestion(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(ShortAnswerQuestionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerQuestionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.shortAnswerQuestion).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
