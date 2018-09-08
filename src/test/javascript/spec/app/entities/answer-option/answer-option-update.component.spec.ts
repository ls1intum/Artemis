/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerOptionUpdateComponent } from 'app/entities/answer-option/answer-option-update.component';
import { AnswerOptionService } from 'app/entities/answer-option/answer-option.service';
import { AnswerOption } from 'app/shared/model/answer-option.model';

describe('Component Tests', () => {
    describe('AnswerOption Management Update Component', () => {
        let comp: AnswerOptionUpdateComponent;
        let fixture: ComponentFixture<AnswerOptionUpdateComponent>;
        let service: AnswerOptionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerOptionUpdateComponent]
            })
                .overrideTemplate(AnswerOptionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(AnswerOptionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerOptionService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new AnswerOption(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.answerOption = entity;
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
                    const entity = new AnswerOption();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.answerOption = entity;
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
