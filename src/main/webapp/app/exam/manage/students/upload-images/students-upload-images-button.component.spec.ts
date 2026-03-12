import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { StudentsUploadImagesButtonComponent } from 'app/exam/manage/students/upload-images/students-upload-images-button.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockModule, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { TranslateModule } from '@ngx-translate/core';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { By } from '@angular/platform-browser';
import { NgbModal, NgbModalRef, NgbModule } from '@ng-bootstrap/ng-bootstrap';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';

describe('StudentsUploadImagesButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<StudentsUploadImagesButtonComponent>;
    let comp: StudentsUploadImagesButtonComponent;
    let modalService: NgbModal;
    const testExam: Exam = { id: 1, title: 'Test Exam', course: { id: 1 } };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [MockModule(NgbModule), TranslateModule.forRoot(), StudentsUploadImagesButtonComponent, MockComponent(ButtonComponent)],
            providers: [MockProvider(AlertService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentsUploadImagesButtonComponent);
                comp = fixture.componentInstance;
                modalService = TestBed.inject(NgbModal);
                fixture.componentRef.setInput('courseId', 1);
                fixture.componentRef.setInput('exam', testExam);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize and set modal inputs correctly', () => {
        const mockModalRef = {
            componentInstance: { courseId: undefined as any, exam: undefined as any },
            result: new Promise((resolve) => resolve(true)),
        };
        const modalServiceOpenStub = vi.spyOn(modalService, 'open').mockReturnValue(mockModalRef as NgbModalRef);

        comp.openUploadImagesDialog(new MouseEvent('click'));

        const openUploadImagesDialogButton = fixture.debugElement.query(By.css('jhi-button'));
        expect(openUploadImagesDialogButton).not.toBeNull();
        expect(modalServiceOpenStub).toHaveBeenCalledOnce();
        expect(mockModalRef.componentInstance.courseId()!).toBe(1);
        expect(mockModalRef.componentInstance.exam()!).toBe(testExam);
    });
});
