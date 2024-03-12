import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject, throwError } from 'rxjs';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { AccountService } from 'app/core/auth/account.service';
import { By } from '@angular/platform-browser';
import { MockComponent } from 'ng-mocks';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MarkdownEditorComponent } from 'app/shared/markdown-editor/markdown-editor.component';
import { ExamLiveEventComponent } from 'app/exam/shared/events/exam-live-event.component';
import { ExamWideAnnouncementEvent } from 'app/exam/participate/exam-participation-live-events.service';

describe('ExamLiveAnnouncementCreateModalComponent', () => {
    let component: ExamLiveAnnouncementCreateModalComponent;
    let fixture: ComponentFixture<ExamLiveAnnouncementCreateModalComponent>;
    let mockActiveModal: NgbActiveModal;
    let mockExamManagementService: ExamManagementService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            declarations: [ExamLiveAnnouncementCreateModalComponent, MockComponent(FaIconComponent), MockComponent(MarkdownEditorComponent), MockComponent(ExamLiveEventComponent)],
            providers: [
                { provide: NgbActiveModal, useValue: { dismiss: jest.fn() } },
                { provide: ExamManagementService, useValue: { createAnnouncement: jest.fn() } },
                { provide: AccountService, useValue: { userIdentity: { name: 'John Doe' } } },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveAnnouncementCreateModalComponent);
        component = fixture.componentInstance;
        mockActiveModal = TestBed.inject(NgbActiveModal);
        mockExamManagementService = TestBed.inject(ExamManagementService);
    });

    it('should initialize component with default properties', () => {
        fixture.detectChanges();
        expect(component.status).toBe('not_submitted');
    });

    it('should update text content and announcement when textContentChanged() is called', () => {
        component.textContentChanged('new text');
        expect(component.textContent).toBe('new text');
        expect(component.announcement?.text).toBe('new text');
    });

    it('should dismiss the modal when clear() is called', () => {
        component.clear();
        expect(mockActiveModal.dismiss).toHaveBeenCalledWith('cancel');
    });

    it('should handle successful announcement submission', () => {
        const testingSubject = new Subject<ExamWideAnnouncementEvent>();
        jest.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(testingSubject.asObservable());
        component.submitAnnouncement();
        expect(component.status).toBe('submitting');
        fixture.detectChanges();

        let contentSpan = fixture.debugElement.query(By.css('h2 > span'));
        expect(contentSpan).toBeTruthy();
        expect(contentSpan.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.announcementCreate.sending');
        expect(mockExamManagementService.createAnnouncement).toHaveBeenCalled();

        testingSubject.next({ id: 1, text: 'new announcement' } as any as ExamWideAnnouncementEvent);
        fixture.detectChanges();
        expect(component.status).toBe('submitted');

        contentSpan = fixture.debugElement.query(By.css('h2 > span'));
        expect(contentSpan).toBeTruthy();
        expect(contentSpan.nativeElement.getAttribute('jhiTranslate')).toBe('artemisApp.examManagement.announcementCreate.sent');
    });

    it('should handle failed announcement submission', () => {
        jest.spyOn(mockExamManagementService, 'createAnnouncement').mockReturnValue(throwError('Error'));
        component.submitAnnouncement();
        expect(component.status).toBe('not_submitted');
        expect(mockExamManagementService.createAnnouncement).toHaveBeenCalled();
    });
});
