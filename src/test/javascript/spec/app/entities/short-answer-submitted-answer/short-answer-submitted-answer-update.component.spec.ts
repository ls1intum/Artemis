/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedAnswerUpdateComponent } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer-update.component';
import { ShortAnswerSubmittedAnswerService } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.service';
import { ShortAnswerSubmittedAnswer } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.model';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedAnswer Management Update Component', () => {
        let comp: ShortAnswerSubmittedAnswerUpdateComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedAnswerUpdateComponent>;
        let service: ShortAnswerSubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedAnswerUpdateComponent]
            })
                .overrideTemplate(ShortAnswerSubmittedAnswerUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(ShortAnswerSubmittedAnswerUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSubmittedAnswerService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSubmittedAnswer(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSubmittedAnswer = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new ShortAnswerSubmittedAnswer();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.shortAnswerSubmittedAnswer = entity;
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
