/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { Observable } from 'rxjs/Observable';
import { HttpHeaders, HttpResponse } from '@angular/common/http';

import { ArTEMiSTestModule } from '../../../test.module';
import { FileUploadSubmissionComponent } from '../../../../../../main/webapp/app/entities/file-upload-submission/file-upload-submission.component';
import { FileUploadSubmissionService } from '../../../../../../main/webapp/app/entities/file-upload-submission/file-upload-submission.service';
import { FileUploadSubmission } from '../../../../../../main/webapp/app/entities/file-upload-submission/file-upload-submission.model';

describe('Component Tests', () => {

    describe('FileUploadSubmission Management Component', () => {
        let comp: FileUploadSubmissionComponent;
        let fixture: ComponentFixture<FileUploadSubmissionComponent>;
        let service: FileUploadSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FileUploadSubmissionComponent],
                providers: [
                    FileUploadSubmissionService
                ]
            })
            .overrideTemplate(FileUploadSubmissionComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(FileUploadSubmissionComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN
                const headers = new HttpHeaders().append('link', 'link;link');
                spyOn(service, 'query').and.returnValue(Observable.of(new HttpResponse({
                    body: [new FileUploadSubmission(123)],
                    headers
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.query).toHaveBeenCalled();
                expect(comp.fileUploadSubmissions[0]).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
