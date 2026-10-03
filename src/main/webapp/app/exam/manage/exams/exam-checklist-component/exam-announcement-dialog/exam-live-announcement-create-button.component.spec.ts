import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';
import { ExamLiveAnnouncementCreateModalComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-modal.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

describe('ExamLiveAnnouncementCreateButtonComponent', () => {
    let component: ExamLiveAnnouncementCreateButtonComponent;
    let fixture: ComponentFixture<ExamLiveAnnouncementCreateButtonComponent>;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: AlertService, useValue: { closeAll: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveAnnouncementCreateButtonComponent);
        component = fixture.componentInstance;
        mockAlertService = TestBed.inject(AlertService);

        const exam = {
            id: 1,
            visibleDate: dayjs().subtract(1, 'day'),
            course: { id: 2 },
        } as Exam;
        fixture.componentRef.setInput('exam', exam);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it.each([
        [dayjs().subtract(1, 'day'), true],
        [dayjs().add(1, 'day'), false],
    ])('should initialize component properties with visibleDate', (visibleDate, expectedAnnouncementAllowed) => {
        component.exam().visibleDate = visibleDate;

        fixture.detectChanges();

        expect(component.faBullhorn).toEqual(faBullhorn);
        expect(component.announcementCreationAllowed()).toBe(expectedAnnouncementAllowed);
    });

    it('should open the dialog with the exam and course ids when the button is clicked', () => {
        fixture.detectChanges();
        expect(fixture.debugElement.query(By.directive(ExamLiveAnnouncementCreateModalComponent))).toBeNull();

        fixture.debugElement.query(By.css('[data-testid="announcement-create-button"]')).triggerEventHandler('click', new MouseEvent('click'));
        fixture.detectChanges();

        expect(mockAlertService.closeAll).toHaveBeenCalled();
        expect(component.dialogVisible()).toBe(true);
        const modal = fixture.debugElement.query(By.directive(ExamLiveAnnouncementCreateModalComponent));
        expect(modal).toBeTruthy();
        const modalInstance = modal.componentInstance as ExamLiveAnnouncementCreateModalComponent;
        expect(modalInstance.examId()).toBe(1);
        expect(modalInstance.courseId()).toBe(2);
    });

    it('should remove the dialog again once it is closed', () => {
        fixture.detectChanges();
        fixture.debugElement.query(By.css('[data-testid="announcement-create-button"]')).triggerEventHandler('click', new MouseEvent('click'));
        fixture.detectChanges();

        fixture.debugElement.query(By.directive(ExamLiveAnnouncementCreateModalComponent)).componentInstance.clear();
        fixture.detectChanges();

        expect(component.dialogVisible()).toBe(false);
        expect(fixture.debugElement.query(By.directive(ExamLiveAnnouncementCreateModalComponent))).toBeNull();
    });

    it('should not open dialog when announcementCreationAllowed is false', () => {
        const examInFuture = {
            id: 1,
            visibleDate: dayjs().add(1, 'day'),
            course: { id: 2 },
        } as Exam;
        fixture.componentRef.setInput('exam', examInFuture);
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('[data-testid="announcement-create-button"]'));
        expect(button.nativeElement.disabled).toBe(true);
        button.nativeElement.click();
        fixture.detectChanges();

        expect(mockAlertService.closeAll).not.toHaveBeenCalled();
        expect(component.dialogVisible()).toBe(false);
    });
});
