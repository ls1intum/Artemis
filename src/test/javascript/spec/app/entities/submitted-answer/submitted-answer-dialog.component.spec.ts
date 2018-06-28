/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { SubmittedAnswerDialogComponent } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer-dialog.component';
import { SubmittedAnswerService } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.service';
import { SubmittedAnswer } from '../../../../../../main/webapp/app/entities/submitted-answer/submitted-answer.model';
import { QuestionService } from '../../../../../../main/webapp/app/entities/question';
import { QuizSubmissionService } from '../../../../../../main/webapp/app/entities/quiz-submission';

describe('Component Tests', () => {

    describe('SubmittedAnswer Management Dialog Component', () => {
        let comp: SubmittedAnswerDialogComponent;
        let fixture: ComponentFixture<SubmittedAnswerDialogComponent>;
        let service: SubmittedAnswerService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [SubmittedAnswerDialogComponent],
                providers: [
                    QuestionService,
                    QuizSubmissionService,
                    SubmittedAnswerService
                ]
            })
            .overrideTemplate(SubmittedAnswerDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(SubmittedAnswerDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(SubmittedAnswerService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new SubmittedAnswer(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.submittedAnswer = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'submittedAnswerListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new SubmittedAnswer();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.submittedAnswer = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'submittedAnswerListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
