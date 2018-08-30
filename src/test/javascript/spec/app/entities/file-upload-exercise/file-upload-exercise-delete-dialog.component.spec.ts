/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async, inject, fakeAsync, tick } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Observable } from 'rxjs/Observable';
import { JhiEventManager } from 'ng-jhipster';

import { ArTEMiSTestModule } from '../../../test.module';
import { FileUploadExerciseDeleteDialogComponent } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise-delete-dialog.component';
import { FileUploadExerciseService } from '../../../../../../main/webapp/app/entities/file-upload-exercise/file-upload-exercise.service';

describe('Component Tests', () => {

    describe('FileUploadExercise Management Delete Component', () => {
        let comp: FileUploadExerciseDeleteDialogComponent;
        let fixture: ComponentFixture<FileUploadExerciseDeleteDialogComponent>;
        let service: FileUploadExerciseService;
        let mockEventManager: any;
        let mockActiveModal: any;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FileUploadExerciseDeleteDialogComponent],
                providers: [
                    FileUploadExerciseService
                ]
            })
            .overrideTemplate(FileUploadExerciseDeleteDialogComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(FileUploadExerciseDeleteDialogComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadExerciseService);
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
