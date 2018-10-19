/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { AnswerCounterDeleteDialogComponent } from 'app/entities/answer-counter/answer-counter-delete-dialog.component';
import { AnswerCounterService } from 'app/entities/answer-counter/answer-counter.service';

describe('Component Tests', () => {
    describe('AnswerCounter Management Delete Component', () => {
        let comp: AnswerCounterDeleteDialogComponent;
        let fixture: ComponentFixture<AnswerCounterDeleteDialogComponent>;
        let service: AnswerCounterService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [AnswerCounterDeleteDialogComponent]
            })
                .overrideTemplate(AnswerCounterDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(AnswerCounterDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(AnswerCounterService);
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
