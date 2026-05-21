import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { By } from '@angular/platform-browser';
import { ExamWideAnnouncementEvent } from 'app/exam/overview/services/exam-participation-live-events.service';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamLiveAnnouncementCreateModalComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamLiveAnnouncementCreateModalComponent;
    let fixture: ComponentFixture<ExamLiveAnnouncementCreateModalComponent>;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let dialogRef: DynamicDialogRef;
    let mockExamManagementService: ExamManagementService;

    beforeEach(async () => {
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            providers: [
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DynamicDialogConfig, useValue: { data: { examId: 1, courseId: 2 } } },
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveAnnouncementCreateModalComponent);
        component = fixture.componentInstance;
        global.ResizeObserver = MockResizeObserver as any;
        mockExamManagementService = TestBed.inject(ExamManagementService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component with default properties from dialog config', () => {
        fixture.detectChanges();
        expect(component.status).toBe('not_submitted');
        expect(component.examId).toBe(1);
        expect(component.courseId).toBe(2);
    });

    it('should update text content and announcement when textContentChanged() is called', () => {
        component.textContentChanged('new text');
        expect(component.textContent).toBe('new text');
        expect(component.announcement?.text).toBe('new text');
    });

    it('should close the dialog when clear() is called', () => {
        component.clear();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith('cancel');
    });

    it('should handle successful announcement submission', () => {
        const testingSubject = new Subject<ExamWideAnnouncementEvent>();
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(testingSubject.asObservable());
        fixture.detectChanges();
        component.submitAnnouncement();
        expect(component.status).toBe('submitting');
        fixture.changeDetectorRef.detectChanges();

        let contentSpan = fixture.debugElement.query(By.css('h2 > span'));
        expect(contentSpan).toBeTruthy();
        expect(contentSpan.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.announcementCreate.sending');
        expect(mockExamManagementService.createAnnouncement).toHaveBeenCalled();

        testingSubject.next({ id: 1, text: 'new announcement' } as any as ExamWideAnnouncementEvent);
        fixture.changeDetectorRef.detectChanges();
        expect(component.status).toBe('submitted');

        contentSpan = fixture.debugElement.query(By.css('h2 > span'));
        expect(contentSpan).toBeTruthy();
        expect(contentSpan.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.announcementCreate.sent');
    });

    it('should handle failed announcement submission', () => {
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(throwError(() => new Error('Error')));
        fixture.detectChanges();
        component.submitAnnouncement();
        expect(component.status).toBe('not_submitted');
        expect(mockExamManagementService.createAnnouncement).toHaveBeenCalled();
    });
});
