import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
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
import { of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('StudentsUploadImagesDialogComponent', () => {
    let fixture: ComponentFixture<StudentsUploadImagesDialogComponent>;
    let component: StudentsUploadImagesDialogComponent;
    let examManagementService: ExamManagementService;

    const course: Course = { id: 1 };
    const exam: Exam = { course, id: 2, title: 'Exam Title' };

    beforeEach(async () => {
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
        fixture.componentRef.setInput('courseId', course.id);
        fixture.componentRef.setInput('exam', exam);
        examManagementService = TestBed.inject(ExamManagementService);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should start with an empty form every time it is opened', () => {
        component.open();
        fixture.detectChanges();
        component.notFoundUsers.set({ numberOfUsersNotFound: 1, numberOfImagesSaved: 10 });
        component.hasParsed.set(true);

        component.clear();
        fixture.detectChanges();
        component.open();
        fixture.detectChanges();

        expect(component.visible()).toBe(true);
        expect(component.hasParsed()).toBe(false);
        expect(component.notFoundUsers()).toBeUndefined();
    });

    it('should reset dialog when selecting pdf file', async () => {
        component.notFoundUsers.set({ numberOfUsersNotFound: 1, numberOfImagesSaved: 10 });
        component.hasParsed.set(true);

        const event = { target: { files: [{ file: new File([''], 'testFile.pdf', { type: 'application/pdf' }), fileName: 'testFile' }] } } as unknown as Event;
        await component.onPDFFileSelect(event);

        expect(component.notFoundUsers()).toBeUndefined();
    });

    it('should close the dialog without emitting when cancelled', () => {
        const finishedSpy = vi.fn();
        component.finished.subscribe(finishedSpy);
        component.visible.set(true);

        component.clear();

        expect(component.visible()).toBe(false);
        expect(finishedSpy).not.toHaveBeenCalled();
    });

    it('should close the dialog and emit finished on finish', () => {
        const finishedSpy = vi.fn();
        component.finished.subscribe(finishedSpy);
        component.visible.set(true);

        component.onFinish();

        expect(component.visible()).toBe(false);
        expect(finishedSpy).toHaveBeenCalledOnce();
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
