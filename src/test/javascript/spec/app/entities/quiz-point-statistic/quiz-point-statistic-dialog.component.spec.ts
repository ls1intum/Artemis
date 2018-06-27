/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { QuizPointStatisticDialogComponent } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic-dialog.component';
import { QuizPointStatisticService } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.service';
import { QuizPointStatistic } from '../../../../../../main/webapp/app/entities/quiz-point-statistic/quiz-point-statistic.model';
import { QuizExerciseService } from '../../../../../../main/webapp/app/entities/quiz-exercise';

describe('Component Tests', () => {

    describe('QuizPointStatistic Management Dialog Component', () => {
        let comp: QuizPointStatisticDialogComponent;
        let fixture: ComponentFixture<QuizPointStatisticDialogComponent>;
        let service: QuizPointStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [QuizPointStatisticDialogComponent],
                providers: [
                    QuizExerciseService,
                    QuizPointStatisticService
                ]
            })
            .overrideTemplate(QuizPointStatisticDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(QuizPointStatisticDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizPointStatisticService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('save', () => {
            it('Should call update service on save for existing entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new QuizPointStatistic(123);
                        spyOn(service, 'update').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.quizPointStatistic = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.update).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'quizPointStatisticListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );

            it('Should call create service on save for new entity',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        const entity = new QuizPointStatistic();
                        spyOn(service, 'create').and.returnValue(Observable.of(new HttpResponse({body: entity})));
                        comp.quizPointStatistic = entity;
                        // WHEN
                        comp.save();
                        tick(); // simulate async

                        // THEN
                        expect(service.create).toHaveBeenCalledWith(entity);
                        expect(comp.isSaving).toEqual(false);
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalledWith({ name: 'quizPointStatisticListModification', content: 'OK'});
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
