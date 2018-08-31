/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { TextSubmissionDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/text-submission/text-submission-delete-dialog.component';
import { TextSubmissionService } from '../../../../../../main/webapp/app/entities/text-submission/text-submission.service';

describe('Component Tests', () => {

    describe('TextSubmission Management Delete Component', () => {
        let comp: TextSubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<TextSubmissionDeleteDialogComponent>;
        let service: TextSubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [TextSubmissionDeleteDialogComponent],
                providers: [
                    TextSubmissionService
                ]
            })
            .overrideTemplate(TextSubmissionDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(TextSubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(TextSubmissionService);
            mockEventManager = fixture.debugElement.injector.get(JhiEventManager);
            mockActiveModal = fixture.debugElement.injector.get(NgbActiveModal);
        });

        describe('confirmDelete', () => {
            it('Should call delete service on confirmDelete',
                inject([],
                    fakeAsync(() => {
                        // GIVEN
                        spyOn(service, 'delete').and.returnValue(Observable.of({}));

                        // WHEN
                        comp.confirmDelete(123);
                        tick();

                        // THEN
                        expect(service.delete).toHaveBeenCalledWith(123);
                        expect(mockActiveModal.dismissSpy).toHaveBeenCalled();
                        expect(mockEventManager.broadcastSpy).toHaveBeenCalled();
                    })
                )
            );
        });
    });

});
