import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/shared/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { faBullhorn } from '@fortawesome/free-solid-svg-icons';
import dayjs from 'dayjs/esm';
import { By } from '@angular/platform-browser';
import { ExamLiveAnnouncementCreateButtonComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-announcement-dialog/exam-live-announcement-create-button.component';
import { of } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

describe('ExamLiveAnnouncementCreateButtonComponent', () => {
    let component: ExamLiveAnnouncementCreateButtonComponent;
    let fixture: ComponentFixture<ExamLiveAnnouncementCreateButtonComponent>;
    let mockModalService: NgbModal;
    let mockAlertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: NgbModal, useValue: { open: jest.fn() } },
                { provide: AlertService, useValue: { closeAll: jest.fn() } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamLiveAnnouncementCreateButtonComponent);
        component = fixture.componentInstance;
        mockModalService = TestBed.inject(NgbModal);
        mockAlertService = TestBed.inject(AlertService);

        const exam = {
            id: 1,
            visibleDate: dayjs().subtract(1, 'day'),
            course: { id: 2 },
        } as Exam;
        fixture.componentRef.setInput('exam', exam);
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
        jest.spyOn(mockModalService, 'open').mockReturnValue({ componentInstance: {}, result: of() } as any);
        const button = fixture.debugElement.query(By.css('.btn-warning'));
        button.triggerEventHandler('click', new MouseEvent('click'));

        expect(mockAlertService.closeAll).toHaveBeenCalled();
        expect(mockModalService.open).toHaveBeenCalled();
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
        expect(button.properties.disabled).toBeTrue();
        button.nativeElement.click();

        expect(mockAlertService.closeAll).not.toHaveBeenCalled();
        expect(mockModalService.open).not.toHaveBeenCalled();
    });
});
