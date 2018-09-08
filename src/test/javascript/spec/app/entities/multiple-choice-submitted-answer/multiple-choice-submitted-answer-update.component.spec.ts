/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerUpdateComponent } from 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-update.component';
import { MultipleChoiceSubmittedAnswerService } from 'app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.service';
import { MultipleChoiceSubmittedAnswer } from 'app/shared/model/multiple-choice-submitted-answer.model';

describe('Component Tests', () => {
    describe('MultipleChoiceSubmittedAnswer Management Update Component', () => {
        let comp: MultipleChoiceSubmittedAnswerUpdateComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerUpdateComponent>;
        let service: MultipleChoiceSubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerUpdateComponent]
            })
                .overrideTemplate(MultipleChoiceSubmittedAnswerUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceSubmittedAnswerService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new MultipleChoiceSubmittedAnswer(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.multipleChoiceSubmittedAnswer = entity;
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
                    const entity = new MultipleChoiceSubmittedAnswer();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.multipleChoiceSubmittedAnswer = entity;
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
