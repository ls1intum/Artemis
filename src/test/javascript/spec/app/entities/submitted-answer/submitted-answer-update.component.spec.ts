/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerUpdateComponent } from 'app/entities/submitted-answer/submitted-answer-update.component';
import { SubmittedAnswerService } from 'app/entities/submitted-answer/submitted-answer.service';
import { SubmittedAnswer } from 'app/shared/model/submitted-answer.model';

describe('Component Tests', () => {
    describe('SubmittedAnswer Management Update Component', () => {
        let comp: SubmittedAnswerUpdateComponent;
        let fixture: ComponentFixture<SubmittedAnswerUpdateComponent>;
        let service: SubmittedAnswerService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerUpdateComponent]
            })
                .overrideTemplate(SubmittedAnswerUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(SubmittedAnswerUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmittedAnswerService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new SubmittedAnswer(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.submittedAnswer = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new SubmittedAnswer();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.submittedAnswer = entity;
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
