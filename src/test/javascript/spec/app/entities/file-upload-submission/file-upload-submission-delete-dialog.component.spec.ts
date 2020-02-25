/* tslint:disable max-line-length */
import { ComponentFixture, fakeAsync, inject, TestBed, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadSubmissionDeleteDialogComponent } from 'app/exercises/file-upload-submission/file-upload-submission-delete-dialog.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';

describe('Component Tests', () => {
    describe('FileUploadSubmission Management Delete Component', () => {
        let comp: FileUploadSubmissionDeleteDialogComponent;
        let fixture: ComponentFixture<FileUploadSubmissionDeleteDialogComponent>;
        let service: FileUploadSubmissionService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadSubmissionDeleteDialogComponent],
            })
                .overrideTemplate(FileUploadSubmissionDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(FileUploadSubmissionDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadSubmissionService);
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
                }),
            ));
        });
    });
});
