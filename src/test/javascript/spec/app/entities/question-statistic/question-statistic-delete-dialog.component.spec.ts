/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuestionStatisticDeleteDialogComponent } from 'app/entities/quiz-question-statistic/question-statistic-delete-dialog.component';
import { QuestionStatisticService } from 'app/entities/quiz-question-statistic/quiz-question-statistic.service';

describe('Component Tests', () => {
    describe('QuestionStatistic Management Delete Component', () => {
        let comp: QuestionStatisticDeleteDialogComponent;
        let fixture: ComponentFixture<QuestionStatisticDeleteDialogComponent>;
        let service: QuestionStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuestionStatisticDeleteDialogComponent]
            })
                .overrideTemplate(QuestionStatisticDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuestionStatisticDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuestionStatisticService);
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
