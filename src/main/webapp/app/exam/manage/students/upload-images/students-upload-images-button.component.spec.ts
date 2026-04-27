import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { StudentsUploadImagesButtonComponent } from 'app/exam/manage/students/upload-images/students-upload-images-button.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { By } from '@angular/platform-browser';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { Subject } from 'rxjs';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { StudentsUploadImagesDialogComponent } from 'app/exam/manage/students/upload-images/students-upload-images-dialog.component';

describe('StudentsUploadImagesButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<StudentsUploadImagesButtonComponent>;
    let comp: StudentsUploadImagesButtonComponent;
    let dialogService: DialogService;
    const testExam: Exam = { id: 1, title: 'Test Exam', course: { id: 1 } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TranslateModule.forRoot(), StudentsUploadImagesButtonComponent, MockComponent(ButtonComponent)],
            providers: [MockProvider(AlertService), MockProvider(DialogService), { provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsUploadImagesButtonComponent);
                comp = fixture.componentInstance;
                dialogService = TestBed.inject(DialogService);
                fixture.componentRef.setInput('courseId', 1);
                fixture.componentRef.setInput('exam', testExam);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and pass dialog data correctly', () => {
        const onCloseSubject = new Subject<any>();
        const mockDialogRef = {
            onClose: onCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;
        const dialogServiceOpenSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);

        comp.openUploadImagesDialog(new MouseEvent('click'));

        const openUploadImagesDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openUploadImagesDialogButton).not.toBeNull();
        expect(dialogServiceOpenSpy).toHaveBeenCalledOnce();
        expect(dialogServiceOpenSpy.mock.calls[0][0]).toBe(StudentsUploadImagesDialogComponent);
        const config = dialogServiceOpenSpy.mock.calls[0][1];
        expect(config?.data?.courseId).toBe(1);
        expect(config?.data?.exam).toBe(testExam);
    });

    it('should emit uploadDone when dialog closes with finished result', () => {
        const onCloseSubject = new Subject<any>();
        const mockDialogRef = {
            onClose: onCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;
        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const uploadDoneSpy = vi.fn();
        comp.uploadDone.subscribe(uploadDoneSpy);

        comp.openUploadImagesDialog(new MouseEvent('click'));
        onCloseSubject.next('finished');

        expect(uploadDoneSpy).toHaveBeenCalledOnce();
    });

    it('should not emit uploadDone when dialog closes without finished result', () => {
        const onCloseSubject = new Subject<any>();
        const mockDialogRef = {
            onClose: onCloseSubject.asObservable(),
            close: vi.fn(),
        } as unknown as DynamicDialogRef;
        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        const uploadDoneSpy = vi.fn();
        comp.uploadDone.subscribe(uploadDoneSpy);

        comp.openUploadImagesDialog(new MouseEvent('click'));
        onCloseSubject.next(undefined);

        expect(uploadDoneSpy).not.toHaveBeenCalled();
    });
});
