/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerQuestionStatisticDeleteDialogComponent } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic-delete-dialog.component';
import { ShortAnswerQuestionStatisticService } from 'app/entities/short-answer-question-statistic/short-answer-question-statistic.service';

describe('Component Tests', () => {
    describe('ShortAnswerQuestionStatistic Management Delete Component', () => {
        let comp: ShortAnswerQuestionStatisticDeleteDialogComponent;
        let fixture: ComponentFixture<ShortAnswerQuestionStatisticDeleteDialogComponent>;
        let service: ShortAnswerQuestionStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerQuestionStatisticDeleteDialogComponent]
            })
                .overrideTemplate(ShortAnswerQuestionStatisticDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerQuestionStatisticDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerQuestionStatisticService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete', inject(
                [],
                fakeAsync(() => {
                    // GIVEN
                    spyOn(service, 'delete').and.returnValue(of({}));

                    // WHEN
                    comp.confirmDelete(123);
                    tick();

                    // THEN
                    expect(service.delete).toHaveBeenCalledWith(123);
                    expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                    expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                })
            ));
        });
    });
});
