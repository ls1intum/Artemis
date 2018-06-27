/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { AnswerCounterDialogComponent } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter-dialog.component';
import { AnswerCounterService } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.service';
import { AnswerCounter } from '../../../../../../main/webapp/app/entities/answer-counter/answer-counter.model';
import { AnswerOptionService } from '../../../../../../main/webapp/app/entities/answer-option';
import { MultipleChoiceQuestionStatisticService } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic';

describe('Component Tests', () => {

    describe('AnswerCounter Management Dialog Component', () => {
        let comp: AnswerCounterDialogComponent;
        let fixture: ComponentFixture<AnswerCounterDialogComponent>;
        let service: AnswerCounterService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [AnswerCounterDialogComponent],
                providers: [
                    AnswerOptionService,
                    MultipleChoiceQuestionStatisticService,
                    AnswerCounterService
                ]
            })
            .overrideTemplate(AnswerCounterDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(AnswerCounterDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerCounterService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new AnswerCounter(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.answerCounter = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'answerCounterListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new AnswerCounter();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.answerCounter = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'answerCounterListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
