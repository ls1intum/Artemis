/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable, of } from 'rxjs';
import { JhiEventManager } from 'ng-jhipster';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadExerciseDeleteDialogComponent } from 'app/entities/file-upload-exercise/file-upload-exercise-delete-dialog.component';
import { FileUploadExerciseService } from 'app/entities/file-upload-exercise/file-upload-exercise.service';

describe('Component Tests', () => {
    describe('FileUploadExercise Management Delete Component', () => {
        let comp: FileUploadExerciseDeleteDialogComponent;
        let fixture: ComponentFixture<FileUploadExerciseDeleteDialogComponent>;
        let service: FileUploadExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadExerciseDeleteDialogComponent]
            })
                .overrideTemplate(FileUploadExerciseDeleteDialogComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(FileUploadExerciseDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadExerciseService);
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
