/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { QuizPointStatisticDeleteDialogComponent } from 'app/entities/quiz-point-statistic/quiz-point-statistic-delete-dialog.component';
import { QuizPointStatisticService } from 'app/entities/quiz-point-statistic/quiz-point-statistic.service';

describe('Component Tests', () => {
    describe('QuizPointStatistic Management Delete Component', () => {
        let comp: QuizPointStatisticDeleteDialogComponent;
        let fixture: ComponentFixture<QuizPointStatisticDeleteDialogComponent>;
        let service: QuizPointStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [QuizPointStatisticDeleteDialogComponent]
            })
                .overrideTemplate(QuizPointStatisticDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(QuizPointStatisticDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(QuizPointStatisticService);
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
