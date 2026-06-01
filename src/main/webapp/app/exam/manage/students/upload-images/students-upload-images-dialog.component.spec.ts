import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/foundation/service/alert.service';
import { TranslateService } from '@ngx-translate/core';
import { Router } from '@angular/router';
import { Subject, of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('StudentsUploadImagesDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<StudentsUploadImagesDialogComponent>;
    let component: StudentsUploadImagesDialogComponent;
    let examManagementService: ExamManagementService;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let dialogRef: DynamicDialogRef;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [
                FaIconComponent,
                FormsModule,
                StudentsUploadImagesDialogComponent,
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
            ],
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DynamicDialogConfig, useValue: { data: { courseId: course.id, exam } } },
                MockProvider(AlertService),
                MockProvider(ExamManagementService),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(SessionStorageService),
                MockProvider(LocalStorageService),
                MockProvider(Router),
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(StudentsUploadImagesDialogComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should read courseId and exam from dialog config', () => {
        expect(component.courseId()).toBe(course.id);
        expect(component.exam()).toBe(exam);
    });

    it('should reset dialog when selecting pdf file', async () => {
        component.notFoundUsers.set({ numberOfUsersNotFound: 1, numberOfImagesSaved: 10 });
        component.hasParsed.set(true);

        const event = { target: { files: [{ file: new File([''], 'testFile.pdf', { type: 'application/pdf' }), fileName: 'testFile' }] } };
        await component.onPDFFileSelect(event);

        expect(component.notFoundUsers()).toBeUndefined();
    });

    it('should call the function to cancel the dialog', () => {
        component.clear();
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
    });

    it('should call the function onFinish and then close the dialog with finished result', () => {
        component.onFinish();
        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith('finished');
    });

    it('should upload and save images correctly', () => {
        const response: any = {
            numberOfUsersNotFound: 1,
            numberOfImagesSaved: 10,
            listOfExamUserRegistrationNumbers: ['12345678'],
        };
        const examServiceStub = vi.spyOn(examManagementService, 'saveImages').mockReturnValue(of(new HttpResponse({ body: response })));
        component.parsePDFFile();

        expect(examServiceStub).toHaveBeenCalledOnce();
        expect(component.isParsing()).toBe(false);
        expect(component.hasParsed()).toBe(true);
        expect(component.notFoundUsers()).toBeDefined();
        expect(component.notFoundUsers()?.numberOfUsersNotFound).toBe(1);
    });
});
