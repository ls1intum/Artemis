/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { StatisticCounterDeleteDialogComponent } from 'app/entities/quiz-statistic-counter/statistic-counter-delete-dialog.component';
import { StatisticCounterService } from 'app/entities/quiz-statistic-counter/statistic-counter.service';

describe('Component Tests', () => {
    describe('QuizStatisticCounter Management Delete Component', () => {
        let comp: StatisticCounterDeleteDialogComponent;
        let fixture: ComponentFixture<StatisticCounterDeleteDialogComponent>;
        let service: StatisticCounterService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [StatisticCounterDeleteDialogComponent]
            })
                .overrideTemplate(StatisticCounterDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(StatisticCounterDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(StatisticCounterService);
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
