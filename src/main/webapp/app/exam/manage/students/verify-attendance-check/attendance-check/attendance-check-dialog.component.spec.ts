import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AttendanceCheckDialogComponent } from './attendance-check-dialog.component';
import { MockProvider } from 'ng-mocks';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { HttpResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/shared/service/alert.service';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamUserAttendanceCheckDTO } from 'app/exam/shared/entities/exam-users-attendance-check-dto.model';

describe('AttendanceCheckDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let component: AttendanceCheckDialogComponent;
    let fixture: ComponentFixture<AttendanceCheckDialogComponent>;

    let alertService: AlertService;
    let examManagementService: ExamManagementService;

    const courseId = 1;
    const exam: Exam = {
        id: 2,
        endDate: dayjs().add(1, 'hour'),
    } as Exam;

    const examUserAttendanceCheck: ExamUserAttendanceCheckDTO = {
        id: 1,
        login: 'login',
        firstName: 'firstName',
        lastName: 'lastName',
        registrationNumber: '12345678',
        didCheckImage: false,
        didCheckLogin: false,
        didCheckName: false,
        didCheckRegistrationNumber: false,
        studentImagePath: 'studentImagePath',
    } as ExamUserAttendanceCheckDTO;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [AttendanceCheckDialogComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(AlertService), MockProvider(ExamManagementService)],
        }).compileComponents();

        fixture = TestBed.createComponent(AttendanceCheckDialogComponent);
        component = fixture.componentInstance;

        alertService = TestBed.inject(AlertService);
        examManagementService = TestBed.inject(ExamManagementService);

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('exam', exam);
        component.examUserAttendanceCheck = examUserAttendanceCheck;

        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should open dialog and clone exam user attendance check', () => {
        component.openDialog(examUserAttendanceCheck);

        expect(component.dialogVisible()).toBeTrue();
        expect(component.examUserAttendanceCheck).toEqual(examUserAttendanceCheck);
        expect(component.examUserAttendanceCheck).not.toBe(examUserAttendanceCheck);
    });

    it('should emit save event and close dialog on save success', () => {
        const emitSpy = vi.spyOn(component.save, 'emit');

        component.openDialog(examUserAttendanceCheck);
        component.onSaveSuccess();

        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(component.examUserAttendanceCheck);
        expect(component.dialogVisible()).toBeFalse();
    });

    it('should show alert on save error', () => {
        const alertSpy = vi.spyOn(alertService, 'error');

        component.onSaveError();

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(alertSpy).toHaveBeenCalledWith('artemisApp.exam.examUsers.attendanceCheckSaveError');
    });

    it('should update exam user and emit save on saveChanges', () => {
        const updatedExamUserAttendanceCheck = {
            didCheckImage: true,
            didCheckLogin: true,
            didCheckName: true,
            didCheckRegistrationNumber: true,
        } as ExamUserAttendanceCheckDTO;

        const expectedPayload = { ...examUserAttendanceCheck };
        const updateExamUserSpy = vi.spyOn(examManagementService, 'updateExamUser').mockReturnValue(of(new HttpResponse({ body: updatedExamUserAttendanceCheck })));
        const saveSuccessSpy = vi.spyOn(component, 'onSaveSuccess');

        component.openDialog(examUserAttendanceCheck);
        component.saveChanges();

        expect(updateExamUserSpy).toHaveBeenCalledOnce();
        expect(updateExamUserSpy).toHaveBeenCalledWith(courseId, exam.id, expectedPayload);

        expect(component.examUserAttendanceCheck).toEqual({
            ...examUserAttendanceCheck,
            ...updatedExamUserAttendanceCheck,
        });
        expect(saveSuccessSpy).toHaveBeenCalledOnce();
    });

    it('should handle error on saveChanges', () => {
        vi.spyOn(examManagementService, 'updateExamUser').mockReturnValue(throwError(() => new Error('Save failed')));
        const saveErrorSpy = vi.spyOn(component, 'onSaveError');

        component.openDialog(examUserAttendanceCheck);
        component.saveChanges();

        expect(saveErrorSpy).toHaveBeenCalledOnce();
    });

    it('should return false if exam end date is missing', () => {
        fixture.componentRef.setInput('exam', { id: 2 } as Exam);
        fixture.detectChanges();

        expect(component['isBeforeExamEndPlusTwoHours']()).toBeFalse();
    });

    it('should return true if before exam end plus two hours', () => {
        fixture.componentRef.setInput('exam', {
            id: 2,
            endDate: dayjs().add(1, 'hour'),
        } as Exam);
        fixture.detectChanges();

        expect(component['isBeforeExamEndPlusTwoHours']()).toBeTrue();
    });

    it('should return false if after exam end plus two hours', () => {
        fixture.componentRef.setInput('exam', {
            id: 2,
            endDate: dayjs().subtract(3, 'hour'),
        } as Exam);
        fixture.detectChanges();

        expect(component['isBeforeExamEndPlusTwoHours']()).toBeFalse();
    });

    it('should return trimmed full name', () => {
        component.examUserAttendanceCheck = {
            ...examUserAttendanceCheck,
            firstName: ' ' + examUserAttendanceCheck.firstName,
            lastName: examUserAttendanceCheck.lastName + ' ',
        };
        expect(component['getExamUserFullName']()).toBe('firstName lastName');
    });
});
