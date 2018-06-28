/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceSubmittedAnswerDialogComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer-dialog.component';
import { MultipleChoiceSubmittedAnswerService } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.service';
import { MultipleChoiceSubmittedAnswer } from '../../../../../../main/webapp/app/entities/multiple-choice-submitted-answer/multiple-choice-submitted-answer.model';
import { AnswerOptionService } from '../../../../../../main/webapp/app/entities/answer-option';

describe('Component Tests', () => {

    describe('MultipleChoiceSubmittedAnswer Management Dialog Component', () => {
        let comp: MultipleChoiceSubmittedAnswerDialogComponent;
        let fixture: ComponentFixture<MultipleChoiceSubmittedAnswerDialogComponent>;
        let service: MultipleChoiceSubmittedAnswerService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceSubmittedAnswerDialogComponent],
                providers: [
                    AnswerOptionService,
                    MultipleChoiceSubmittedAnswerService
                ]
            })
            .overrideTemplate(MultipleChoiceSubmittedAnswerDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceSubmittedAnswerDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceSubmittedAnswerService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new MultipleChoiceSubmittedAnswer(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.multipleChoiceSubmittedAnswer = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'multipleChoiceSubmittedAnswerListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new MultipleChoiceSubmittedAnswer();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.multipleChoiceSubmittedAnswer = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'multipleChoiceSubmittedAnswerListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
