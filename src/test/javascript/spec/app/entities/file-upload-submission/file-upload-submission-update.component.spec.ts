/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable, of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadSubmissionUpdateComponent } from 'app/entities/file-upload-submission/file-upload-submission-update.component';
import { FileUploadSubmissionService } from 'app/entities/file-upload-submission/file-upload-submission.service';
import { FileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

describe('Component Tests', () => {
    describe('FileUploadSubmission Management Update Component', () => {
        let comp: FileUploadSubmissionUpdateComponent;
        let fixture: ComponentFixture<FileUploadSubmissionUpdateComponent>;
        let service: FileUploadSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadSubmissionUpdateComponent]
            })
                .overrideTemplate(FileUploadSubmissionUpdateComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(FileUploadSubmissionUpdateComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadSubmissionService);
        });

        describe('save', () => {
            it(
                'Should call update service on save for existing entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new FileUploadSubmission(123);
                    spyOn(service, 'update').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.fileUploadSubmission = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.update).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );

            it(
                'Should call create service on save for new entity',
                fakeAsync(() => {
                    // GIVEN
                    const entity = new FileUploadSubmission();
                    spyOn(service, 'create').and.returnValue(of(new HttpResponse({ body: entity })));
                    comp.fileUploadSubmission = entity;
                    // WHEN
                    comp.save();
                    tick(); // simulate async

                    // THEN
                    expect(service.create).toHaveBeenCalledWith(entity);
                    expect(comp.isSaving).toEqual(false);
                })
            );
        });
    });
});
