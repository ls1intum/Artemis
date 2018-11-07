/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { ShortAnswerSubmittedTextDeleteDialogComponent } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text-delete-dialog.component';
import { ShortAnswerSubmittedTextService } from 'app/entities/short-answer-submitted-text/short-answer-submitted-text.service';

describe('Component Tests', () => {
    describe('ShortAnswerSubmittedText Management Delete Component', () => {
        let comp: ShortAnswerSubmittedTextDeleteDialogComponent;
        let fixture: ComponentFixture<ShortAnswerSubmittedTextDeleteDialogComponent>;
        let service: ShortAnswerSubmittedTextService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [ShortAnswerSubmittedTextDeleteDialogComponent]
            })
                .overrideTemplate(ShortAnswerSubmittedTextDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(ShortAnswerSubmittedTextDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(ShortAnswerSubmittedTextService);
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
