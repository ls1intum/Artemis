/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedAnswerDeleteDialogComponent } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer-delete-dialog.component';
import { ShortAnswerSubmittedAnswerService } from 'app/entities/short-answer-submitted-answer/short-answer-submitted-answer.service';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedAnswer Management Delete Component', () => {
        let comp: ShortAnswerSubmittedAnswerDeleteDialogComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedAnswerDeleteDialogComponent>;
        let service: ShortAnswerSubmittedAnswerService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedAnswerDeleteDialogComponent]
            })
                .overrideTemplate(ShortAnswerSubmittedAnswerDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSubmittedAnswerDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSubmittedAnswerService);
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
