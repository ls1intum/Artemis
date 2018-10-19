/* tslint:disable max-line-length */
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';

import { ArTeMiSTestModule } from '../../../test.module';
import { FileUploadSubmissionDetailComponent } from 'app/entities/file-upload-submission/file-upload-submission-detail.component';
import { FileUploadSubmission } from 'app/shared/model/file-upload-submission.model';

describe('Component Tests', () => {
    describe('FileUploadSubmission Management Detail Component', () => {
        let comp: FileUploadSubmissionDetailComponent;
        let fixture: ComponentFixture<FileUploadSubmissionDetailComponent>;
        const route = ({ data: of({ fileUploadSubmission: new FileUploadSubmission(123) }) } as any) as ActivatedRoute;

        beforeEach(() => {
            TestBed.configureTestingModule({
                imports: [ArTeMiSTestModule],
                declarations: [FileUploadSubmissionDetailComponent],
                providers: [{ provide: ActivatedRoute, useValue: route }]
            })
                .overrideTemplate(FileUploadSubmissionDetailComponent, '')
                .compileComponents();
            fixture = TestBed.createComponent(FileUploadSubmissionDetailComponent);
            comp = fixture.componentInstance;
        });

        describe('OnInit', () => {
            it('Should call load all on init', () => {
                // GIVEN

                // WHEN
                comp.ngOnInit();

                // THEN
                expect(comp.fileUploadSubmission).toEqual(jasmine.objectContaining({ id: 123 }));
            });
        });
    });
});
