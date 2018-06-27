/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { AnswerOptionDialogComponent } from '../../../../../../main/webapp/app/entities/answer-option/answer-option-dialog.component';
import { AnswerOptionService } from '../../../../../../main/webapp/app/entities/answer-option/answer-option.service';
import { AnswerOption } from '../../../../../../main/webapp/app/entities/answer-option/answer-option.model';
import { MultipleChoiceQuestionService } from '../../../../../../main/webapp/app/entities/multiple-choice-question';

describe('Component Tests', () => {

    describe('AnswerOption Management Dialog Component', () => {
        let comp: AnswerOptionDialogComponent;
        let fixture: ComponentFixture<AnswerOptionDialogComponent>;
        let service: AnswerOptionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [AnswerOptionDialogComponent],
                providers: [
                    MultipleChoiceQuestionService,
                    AnswerOptionService
                ]
            })
            .overrideTemplate(AnswerOptionDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(AnswerOptionDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerOptionService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new AnswerOption(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.answerOption = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'answerOptionListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new AnswerOption();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.answerOption = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'answerOptionListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
