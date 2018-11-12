/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionUpdateComponent } from 'app/entities/short-answer-question/short-answer-question-update.component';
import { ShortAnswerQuestionService } from 'app/entities/short-answer-question/short-answer-question.service';
import { ShortAnswerQuestion } from 'app/entities/short-answer-question/short-answer-question.model';

describe('Component Tests', () => {
    describe('ShortAnswerQuestion Management Update Component', () => {
        let comp: ShortAnswerQuestionUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionUpdateComponent>;
        let service: ShortAnswerQuestionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionUpdateComponent]
            })
                .overrideTemplate(ShortAnswerQuestionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerQuestionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerQuestionService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerQuestion(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerQuestion = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerQuestion();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerQuestion = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.create).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));
        });
    });
});
