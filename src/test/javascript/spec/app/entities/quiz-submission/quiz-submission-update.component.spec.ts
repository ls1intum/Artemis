/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizSubmissionUpdateComponent } from 'app/entities/quiz-submission/quiz-submission-update.component';
import { QuizSubmissionService } from 'app/entities/quiz-submission/quiz-submission.service';
import { QuizSubmission } from 'app/shared/model/quiz-submission.model';

describe('Component Tests', () => {
    describe('QuizSubmission Management Update Component', () => {
        let comp: QuizSubmissionUpdateComponent;
        let fixture: ComponentFixture<QuizSubmissionUpdateComponent>;
        let service: QuizSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizSubmissionUpdateComponent]
            })
                .overrideTemplate(QuizSubmissionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(QuizSubmissionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizSubmissionService);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity', fakeAsync(() => {
                // GIVEN
                const entity = new QuizSubmission(123);
                spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.quizSubmission = entity;
                // WHEN
                comp.save();
                tick(); // simulate async

                // THEN
                expect(service.update).toHaveBeenCalledWith(entity);
                expect(comp.isSaving).toEqual(false);
            }));

            it('Should call create service on save for new entity', fakeAsync(() => {
                // GIVEN
                const entity = new QuizSubmission();
                spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                comp.quizSubmission = entity;
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
