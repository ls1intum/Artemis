/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { MultipleChoiceQuestionStatisticDeleteDialogComponent } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic-delete-dialog.component';
import { MultipleChoiceQuestionStatisticService } from 'app/entities/multiple-choice-question-statistic/multiple-choice-question-statistic.service';

describe('Component Tests', () => {
    describe('MultipleChoiceQuestionStatistic Management Delete Component', () => {
        let comp: MultipleChoiceQuestionStatisticDeleteDialogComponent;
        let fixture: ComponentFixture<MultipleChoiceQuestionStatisticDeleteDialogComponent>;
        let service: MultipleChoiceQuestionStatisticService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [MultipleChoiceQuestionStatisticDeleteDialogComponent]
            })
                .overrideTemplate(MultipleChoiceQuestionStatisticDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(MultipleChoiceQuestionStatisticDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(MultipleChoiceQuestionStatisticService);
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
