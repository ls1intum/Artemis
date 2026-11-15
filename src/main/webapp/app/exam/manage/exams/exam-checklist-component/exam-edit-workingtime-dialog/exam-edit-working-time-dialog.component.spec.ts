import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpResponse } from '@angular/common/http';
import { Subject, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ExamEditWorkingTimeDialogComponent } from 'app/exam/manage/exams/exam-checklist-component/exam-edit-workingtime-dialog/exam-edit-working-time-dialog.component';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('ExamEditWorkingTimeDialogComponent', () => {
    let fixture: ComponentFixture<ExamEditWorkingTimeDialogComponent>;
    let component: ExamEditWorkingTimeDialogComponent;
    let updateWorkingTime: ReturnType<typeof vi.fn>;
    const exam = { id: 1, title: 'Exam', course: { id: 2 }, workingTime: 3600, startDate: dayjs(), endDate: dayjs().add(1, 'hour') } as Exam;

    beforeEach(async () => {
        updateWorkingTime = vi.fn();
        await TestBed.configureTestingModule({
            providers: [
                { provide: ExamManagementService, useValue: { updateWorkingTime } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(ExamEditWorkingTimeDialogComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('exam', exam);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should only accept a working time change that is not zero', () => {
        expect(component.isWorkingTimeChangeValid).toBe(false);
        component.workingTimeSeconds = -60;
        expect(component.isWorkingTimeChangeValid).toBe(true);
    });

    it('should derive the old and new working time from the exam and the change', () => {
        component.workingTimeSeconds = 300;
        expect(component.oldWorkingTime).toBe(3600);
        expect(component.newWorkingTime).toBe(3900);
    });

    it('should not send anything when the change is zero', () => {
        component.confirmUpdateWorkingTime();
        expect(updateWorkingTime).not.toHaveBeenCalled();
    });

    it('should emit the updated exam and close the dialog on success', () => {
        const response = new Subject<HttpResponse<Exam>>();
        updateWorkingTime.mockReturnValue(response.asObservable());
        const emitted: Exam[] = [];
        component.workingTimeUpdated.subscribe((updated) => emitted.push(updated));
        component.workingTimeSeconds = 120;

        component.confirmUpdateWorkingTime();
        expect(component.isLoading()).toBe(true);
        expect(updateWorkingTime).toHaveBeenCalledWith(2, 1, 120);

        const updatedExam = { id: 1, workingTime: 3720 } as Exam;
        response.next(new HttpResponse({ body: updatedExam }));
        expect(component.isLoading()).toBe(false);
        expect(emitted).toEqual([updatedExam]);
        expect(component.visible()).toBe(false);
    });

    it('should stay open and stop loading when the update fails', () => {
        updateWorkingTime.mockReturnValue(throwError(() => new Error('failed')));
        component.workingTimeSeconds = 120;

        component.confirmUpdateWorkingTime();

        expect(component.isLoading()).toBe(false);
        expect(component.visible()).toBe(true);
    });

    it('should close without emitting when cancelled', () => {
        const emitted: Exam[] = [];
        component.workingTimeUpdated.subscribe((updated) => emitted.push(updated));
        component.clear();
        expect(component.visible()).toBe(false);
        expect(emitted).toEqual([]);
    });
});
