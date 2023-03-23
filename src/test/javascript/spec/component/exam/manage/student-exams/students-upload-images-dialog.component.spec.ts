import { HttpClient, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { Course } from 'app/entities/course.model';
import { Exam } from 'app/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { Router } from '@angular/router';
import { of } from 'rxjs';

describe('StudentsUploadImagesDialogComponent', () => {
    let fixture: ComponentFixture<StudentsUploadImagesDialogComponent>;
    let component: StudentsUploadImagesDialogComponent;
    let examManagementService: ExamManagementService;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    let ngbModal: NgbActiveModal;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [FormsModule],
            declarations: [
                StudentsUploadImagesDialogComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(FaIconComponent),
                MockComponent(HelpIconComponent),
            ],
            providers: [
                MockProvider(NgbActiveModal),
                MockProvider(AlertService),
                MockProvider(ExamManagementService),
                MockProvider(HttpClient),
                MockProvider(TranslateService),
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                MockProvider(Router),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsUploadImagesDialogComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);

                component.courseId = course.id!;
                component.exam = exam;

                ngbModal = TestBed.get(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should reset dialog when selecting pdf file', async () => {
        component.notFoundUsers = { numberOfUsersNotFound: 1, numberOfImagesSaved: 10, listOfExamUserRegistrationNumbers: ['12345678'] };
        component.hasParsed = true;

        const event = { target: { files: [{ file: new File([''], 'testFile.pdf', { type: 'application/pdf' }), fileName: 'testFile' }] } };
        await component.onPDFFileSelect(event);

        expect(component.notFoundUsers).toBeUndefined();
    });

    it('should call the function to cancel the dialog', () => {
        const spyModalClose = jest.spyOn(ngbModal, 'dismiss');
        component.clear();
        expect(spyModalClose).toHaveBeenCalledOnce();
    });

    it('should call the function onFinish and then close the dialog', () => {
        const spyModalClose = jest.spyOn(ngbModal, 'close');
        component.onFinish();
        expect(spyModalClose).toHaveBeenCalledOnce();
    });

    it('should upload and save images correctly', () => {
        fixture.detectChanges();
        const response: any = {
            numberOfUsersNotFound: 1,
            numberOfImagesSaved: 10,
            listOfExamUserRegistrationNumbers: ['12345678'],
        };
        const examServiceStub = jest.spyOn(examManagementService, 'saveImages').mockReturnValue(of(new HttpResponse({ body: response })));
        component.parsePDFFile();

        expect(examServiceStub).toHaveBeenCalledOnce();
        expect(component.isParsing).toBeFalse();
        expect(component.hasParsed).toBeTrue();
        expect(component.notFoundUsers).toBeDefined();
        expect(component.notFoundUsers?.numberOfUsersNotFound).toBe(1);
    });
});
