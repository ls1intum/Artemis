/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Observable, of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionComponent } from 'app/entities/short-answer-question/short-answer-question.component';
import { ShortAnswerQuestionService } from 'app/entities/short-answer-question/short-answer-question.service';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question/short-answer-question.model';

describe('Component Tests', () => {
    describe('ShortAnswerQuestion Management Component', () => {
        let comp: ShortAnswerQuestionComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionComponent>;
        let service: ShortAnswerQuestionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionComponent],
                providers: []
            })
                .overrideTemplate(ShortAnswerQuestionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerQuestionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerQuestionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new ShortAnswerQuestion(123)],
                        headers
                    })
                )
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.shortAnswerQuestions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
