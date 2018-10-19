/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerCounterUpdateComponent } from 'app/entities/answer-counter/answer-counter-update.component';
import { AnswerCounterService } from 'app/entities/answer-counter/answer-counter.service';
import { AnswerCounter } from 'app/shared/model/answer-counter.model';

describe('Component Tests', () => {
    describe('AnswerCounter Management Update Component', () => {
        let comp: AnswerCounterUpdateComponent;
        let fixture: ComponentFixture<AnswerCounterUpdateComponent>;
        let service: AnswerCounterService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerCounterUpdateComponent]
            })
                .overrideTemplate(AnswerCounterUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(AnswerCounterUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerCounterService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new AnswerCounter(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.answerCounter = entity;
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
                    const entity = new AnswerCounter();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.answerCounter = entity;
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
