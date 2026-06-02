import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/foundation/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';
import { Subject } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('ExamLiveAnnouncementCreateButtonComponent', () => {
    setupTestBed({ zoneless: true });

    let component: ExamLiveAnnouncementCreateButtonComponent;
    let fixture: ComponentFixture<ExamLiveAnnouncementCreateButtonComponent>;
    let mockDialogService: DialogService;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: DialogService, useValue: { open: vi.fn() } },
                { provide: AlertService, useValue: { closeAll: vi.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveAnnouncementCreateButtonComponent);
        component = fixture.componentInstance;
        mockDialogService = TestBed.inject(DialogService);
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
        expect(component.announcementCreationAllowed).toBe(expectedAnnouncementAllowed);
    });

    it('should open dialog when button is clicked', () => {
        const dialogSpy = vi.spyOn(mockDialogService, 'open').mockReturnValue({ onClose: new Subject<any>() } as unknown as DynamicDialogRef);
        const button = fixture.debugElement.query(By.css('.btn-warning'));
        button.triggerEventHandler('click', new MouseEvent('click'));

        expect(mockAlertService.closeAll).toHaveBeenCalled();
        expect(dialogSpy).toHaveBeenCalledOnce();
        const config = dialogSpy.mock.calls[0][1];
        const data = config?.data as { examId?: number; courseId?: number } | undefined;
        expect(data?.examId).toBe(1);
        expect(data?.courseId).toBe(2);
    });

    it('should not open dialog when announcementCreationAllowed is false', () => {
        const examInFuture = {
            id: 1,
            visibleDate: dayjs().add(1, 'day'),
            course: { id: 2 },
        } as Exam;
        fixture.componentRef.setInput('exam', examInFuture);
        fixture.detectChanges();

        const button = fixture.debugElement.query(By.css('.btn-warning'));
        expect(button.properties.disabled).toBe(true);
        button.nativeElement.click();

        expect(mockAlertService.closeAll).not.toHaveBeenCalled();
        expect(mockDialogService.open).not.toHaveBeenCalled();
    });
});
