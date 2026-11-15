import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
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

describe('ExamLiveAnnouncementCreateModalComponent', () => {
    let component: ExamLiveAnnouncementCreateModalComponent;
    let fixture: ComponentFixture<ExamLiveAnnouncementCreateModalComponent>;
    let mockExamManagementService: ExamManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveAnnouncementCreateModalComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('examId', 1);
        fixture.componentRef.setInput('courseId', 2);
        global.ResizeObserver = MockResizeObserver as any;
        mockExamManagementService = TestBed.inject(ExamManagementService);
        vi.spyOn(mockExamManagementService, 'getAnnouncements').mockReturnValue(of([]));
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize component with the given ids', () => {
        fixture.detectChanges();
        expect(component.status()).toBe('not_submitted');
        expect(component.examId()).toBe(1);
        expect(component.courseId()).toBe(2);
        expect(component.visible()).toBe(true);
    });

    it('should replace history after sending and show the sent event only once', () => {
        const past = { id: 5, text: 'Earlier announcement' } as ExamWideAnnouncementEvent;
        const sent = { id: 6, text: 'New announcement' } as ExamWideAnnouncementEvent;
        vi.mocked(mockExamManagementService.getAnnouncements)
            .mockReturnValueOnce(of([past]))
            .mockReturnValueOnce(of([sent, past]));
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(of(sent));
        fixture.detectChanges();
        expect(component.pastAnnouncements()).toEqual([past]);
        component.submitAnnouncement();
        fixture.detectChanges();
        expect(component.pastAnnouncements()).toEqual([sent, past]);
        expect(fixture.debugElement.queryAll(By.css('jhi-exam-live-event'))).toHaveLength(2);
    });

    it.each(['success', 'error'])('should ignore an outdated history %s after the refreshed history arrives', (outcome) => {
        const initial = new Subject<ExamWideAnnouncementEvent[]>();
        const refresh = new Subject<ExamWideAnnouncementEvent[]>();
        const sent = { id: 6, text: 'Sent announcement' } as ExamWideAnnouncementEvent;
        vi.mocked(mockExamManagementService.getAnnouncements).mockReturnValueOnce(initial).mockReturnValueOnce(refresh);
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(of(sent));
        fixture.detectChanges();
        component.submitAnnouncement();
        refresh.next([sent]);
        if (outcome === 'success') {
            initial.next([]);
        } else {
            initial.error(new Error('Old failure'));
        }
        expect(component.pastAnnouncements()).toEqual([sent]);
        expect(component.historyLoadingFailed()).toBe(false);
    });

    it('should show an empty history message', () => {
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.examManagement.announcementCreate.historyEmpty"]'))).toBeTruthy();
    });

    it('should show a history error and retain the sent event when refreshing fails', () => {
        const sent = { id: 6, text: 'Sent announcement' } as ExamWideAnnouncementEvent;
        vi.mocked(mockExamManagementService.getAnnouncements)
            .mockReturnValueOnce(of([]))
            .mockReturnValueOnce(throwError(() => new Error('Refresh failed')));
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(of(sent));
        fixture.detectChanges();
        component.submitAnnouncement();
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.css('[jhiTranslate="artemisApp.examManagement.announcementCreate.historyFailed"]'))).toBeTruthy();
        const event = fixture.debugElement.query(By.css('jhi-exam-live-event'));
        expect(event.componentInstance.event()).toEqual(sent);
    });

    it('should update text content and announcement when textContentChanged() is called', () => {
        component.textContentChanged('new text');
        expect(component.textContent()).toBe('new text');
        expect(component.announcement()?.text).toBe('new text');
    });

    it('should close the dialog when clear() is called', () => {
        component.clear();
        expect(component.visible()).toBe(false);
    });

    it('should handle successful announcement submission', () => {
        const testingSubject = new Subject<ExamWideAnnouncementEvent>();
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(testingSubject.asObservable());
        fixture.detectChanges();
        component.submitAnnouncement();
        expect(component.status()).toBe('submitting');
        fixture.changeDetectorRef.detectChanges();

        let contentSpan = fixture.debugElement.query(By.css('h2 > span'));
        expect(contentSpan).toBeTruthy();
        expect(contentSpan.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.announcementCreate.sending');
        expect(mockExamManagementService.createAnnouncement).toHaveBeenCalled();

        testingSubject.next({ id: 1, text: 'new announcement' } as any as ExamWideAnnouncementEvent);
        fixture.changeDetectorRef.detectChanges();
        expect(component.status()).toBe('submitted');

        contentSpan = fixture.debugElement.query(By.css('h2 > span'));
        expect(contentSpan).toBeTruthy();
        expect(contentSpan.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.announcementCreate.sent');
    });

    it('should handle failed announcement submission', () => {
        vi.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(throwError(() => new Error('Error')));
        fixture.detectChanges();
        component.submitAnnouncement();
        expect(component.status()).toBe('not_submitted');
        expect(mockExamManagementService.createAnnouncement).toHaveBeenCalled();
    });
});
