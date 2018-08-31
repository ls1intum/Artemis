/* tslint:disable max-line-length */
import { ComponentFixture, TestBed, async } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Observable } from 'rxjs/Observable';

import { ArTEMiSTestModule } from '../../../test.module';
import { FileUploadSubmissionDetailComponent } from '../../../../../../main/webapp/app/entities/file-upload-submission/file-upload-submission-detail.component';
import { FileUploadSubmissionService } from '../../../../../../main/webapp/app/entities/file-upload-submission/file-upload-submission.service';
import { FileUploadSubmission } from '../../../../../../main/webapp/app/entities/file-upload-submission/file-upload-submission.model';

describe('Component Tests', () => {

    describe('FileUploadSubmission Management Detail Component', () => {
        let comp: FileUploadSubmissionDetailComponent;
        let fixture: ComponentFixture<FileUploadSubmissionDetailComponent>;
        let service: FileUploadSubmissionService;

        beforeEach(async(() => {
            TestBed.configureTestingModule({
                imports: [ArTEMiSTestModule],
                declarations: [FileUploadSubmissionDetailComponent],
                providers: [
                    FileUploadSubmissionService
                ]
            })
            .overrideTemplate(FileUploadSubmissionDetailComponent, '')
            .compileComponents();
        }));

        beforeEach(() => {
            fixture = TestBed.createComponent(FileUploadSubmissionDetailComponent);
            comp = fixture.componentInstance;
            service = fixture.debugElement.injector.get(FileUploadSubmissionService);
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                spyOn(service, 'find').and.returnValue(Observable.of(new HttpResponse({
                    body: new FileUploadSubmission(123)
                })));

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(service.find).toHaveBeenCalledWith(123);
                expect(comp.fileUploadSubmission).toEqual(jasmine.objectContaining({id: 123}));
            });
        });
    });

});
