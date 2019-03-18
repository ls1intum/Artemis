/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuestionStatisticDialogComponent } from '../../../../../../main/webapp/app/entities/quiz-question-statistic/question-statistic-dialog.component';
import { QuizQuestionStatisticService } from '../../../../../../main/webapp/app/entities/quiz-question-statistic/quiz-question-statistic.service';
import { QuizQuestionStatistic } from '../../../../../../main/webapp/app/entities/quiz-question-statistic/quiz-question-statistic.model';
import { QuestionService } from '../../../../../../main/webapp/app/entities/quiz-question';

describe('Component Tests', () => {

    describe('QuestionStatistic Management Dialog Component', () => {
        let comp: QuestionStatisticDialogComponent;
        let fixture: ComponentFixture<QuestionStatisticDialogComponent>;
        let service: QuizQuestionStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuestionStatisticDialogComponent],
                providers: [
                    QuestionService,
                    QuizQuestionStatisticService
                ]
            })
            .overrideTemplate(QuestionStatisticDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuestionStatisticDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizQuestionStatisticService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new QuizQuestionStatistic(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.questionStatistic = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'questionStatisticListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new QuizQuestionStatistic();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.questionStatistic = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'questionStatisticListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
