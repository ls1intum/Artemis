/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionUpdateComponent } from 'app/entities/multiple-choice-question/multiple-choice-question-update.component';
import { MultipleChoiceQuestionService } from 'app/entities/multiple-choice-question/multiple-choice-question.service';
import { MultipleChoiceQuestion } from 'app/shared/model/multiple-choice-question.model';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestion Management Update Component', () => {
        let comp: MultipleChoiceQuestionUpdateComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionUpdateComponent>;
        let service: MultipleChoiceQuestionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionUpdateComponent]
            })
                .overrideTemplate(MultipleChoiceQuestionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(MultipleChoiceQuestionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new MultipleChoiceQuestion(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.multipleChoiceQuestion = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new MultipleChoiceQuestion();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.multipleChoiceQuestion = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
