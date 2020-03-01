/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadSubmissionComponent } from 'app/exercises/file-upload-submission/file-upload-submission.component';
import { FileUploadSubmissionService } from 'app/exercises/file-upload/participate/file-upload-submission.service';
import { FileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

describe('Component Tests', () => {
    describe('FileUploadSubmission Management Component', () => {
        let comp: FileUploadSubmissionComponent;
        let fixture: ComponentFixture<FileUploadSubmissionComponent>;
        let service: FileUploadSubmissionService;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadSubmissionComponent],
                providers: [],
            })
                .overrideTemplate(FileUploadSubmissionComponent, '')
                .compileComponents();

            fixture = TestBed.createComponent(FileUploadSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadSubmissionService);
        });

        it('Should call load all on init', () => {
            // GIVEN
            const headers = new HttpHeaders().append('link', 'link;link');
            spyOn(service, 'query').and.returnValue(
                of(
                    new HttpResponse({
                        body: [new FileUploadSubmission(123)],
                        headers,
                    }),
                ),
            );

            // WHEN
            comp.ngOnInit();

            // THEN
            expect(service.query).toHaveBeenCalled();
            expect(comp.fileUploadSubmissions[0]).toEqual(jasmine.objectContaining({ id: 123 }));
        });
    });
});
