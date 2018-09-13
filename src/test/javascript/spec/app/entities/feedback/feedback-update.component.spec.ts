/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { FeedbackUpdateComponent } from 'app/entities/feedback/feedback-update.component';
import { FeedbackService } from 'app/entities/feedback/feedback.service';
import { Feedback } from 'app/shared/model/feedback.model';

describe('Component Tests', () => {
    describe('Feedback Management Update Component', () => {
        let comp: FeedbackUpdateComponent;
        let fixture: ComponentFixture<FeedbackUpdateComponent>;
        let service: FeedbackService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FeedbackUpdateComponent]
            })
                .overrideTemplate(FeedbackUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(FeedbackUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FeedbackService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new Feedback(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.feedback = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new Feedback();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.feedback = entity;
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
