/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionDialogComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question-dialog.component';
import { MultipleChoiceQuestionService } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.service';
import { MultipleChoiceQuestion } from '../../../../../../main/webapp/app/entities/multiple-choice-question/multiple-choice-question.model';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestion Management Dialog Component', () => {
        let comp: MultipleChoiceQuestionDialogComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionDialogComponent>;
        let service: MultipleChoiceQuestionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionDialogComponent],
                providers: [
                    MultipleChoiceQuestionService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new MultipleChoiceQuestion(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.multipleChoiceQuestion = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'multipleChoiceQuestionListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new MultipleChoiceQuestion();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.multipleChoiceQuestion = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'multipleChoiceQuestionListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
