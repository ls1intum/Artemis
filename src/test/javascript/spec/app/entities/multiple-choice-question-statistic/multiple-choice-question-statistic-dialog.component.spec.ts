/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticDialogComponent } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-dialog.component';
import { MultipleChoiceQuestionStatisticService } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.service';
import { MultipleChoiceQuestionStatistic } from '../../../../../../main/webapp/app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.model';

describe('Component Tests', () => {

    describe('MultipleChoiceQuestionStatistic Management Dialog Component', () => {
        let comp: MultipleChoiceQuestionStatisticDialogComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticDialogComponent>;
        let service: MultipleChoiceQuestionStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticDialogComponent],
                providers: [
                    MultipleChoiceQuestionStatisticService
                ]
            })
            .overrideTemplate(MultipleChoiceQuestionStatisticDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionStatisticService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new MultipleChoiceQuestionStatistic(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.multipleChoiceQuestionStatistic = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'multipleChoiceQuestionStatisticListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new MultipleChoiceQuestionStatistic();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.multipleChoiceQuestionStatistic = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'multipleChoiceQuestionStatisticListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
