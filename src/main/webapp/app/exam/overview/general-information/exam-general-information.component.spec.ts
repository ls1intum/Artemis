import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/account/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamGeneralInformationComponent } from 'app/exam/overview/general-information/exam-general-information.component';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

let fixture: ComponentFixture<ExamGeneralInformationComponent>;
let component: ExamGeneralInformationComponent;

const user = { id: 1, name: 'Test User' } as User;

const startDate = dayjs('2022-02-06 02:00:00');
const endDate = dayjs(startDate).add(1, 'hours');

let exam = {
    id: 1,
    title: 'Test Exam',
    startDate,
    endDate,
    testExam: false,
} as Exam;

let studentExam = { id: 1, exam, user, workingTime: 60, submitted: true } as StudentExam;

describe('ExamGeneralInformationComponent', () => {
    setupTestBed({ zoneless: true });

    beforeEach(() => {
        exam = { id: 1, title: 'ExamForTesting', startDate, endDate, testExam: false } as Exam;
        studentExam = { id: 1, exam, user, workingTime: 60, submitted: true } as StudentExam;

        return TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamGeneralInformationComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    it('should initialize', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate).toEqual(exam.endDate);
    });

    it('should return undefined if the exam is not set', () => {
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate).toBeUndefined();
    });

    it('should return the start date plus the working time as the student exam end date', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate?.isSame(dayjs(exam.startDate).add(studentExam.workingTime!, 'seconds'))).toBe(true);
    });

    it('should detect if the end date is on another day', () => {
        const examWithMultiDay = { ...exam, endDate: dayjs(exam.startDate).add(2, 'days') } as Exam;
        fixture.componentRef.setInput('exam', examWithMultiDay);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBe(true);
    });

    it('should detect if the working time extends to another day', () => {
        const longStudentExam = { ...studentExam, workingTime: 24 * 60 * 60 } as StudentExam;
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', longStudentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBe(true);
    });

    it('should return false for exams that only last one day', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBe(false);

        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBe(false);
    });

    it('should detect an TestExam and set the currentDate correctly', () => {
        const testExam = { ...exam, testExam: true } as Exam;
        const minimumNowRange = dayjs();
        fixture.componentRef.setInput('exam', testExam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        const maximumNowRange = dayjs();
        expect(component.isTestExam).toBe(true);
        expect(component.currentDate).toBeDefined();
        // test execution could slow down the check
        expect(component.currentDate!.isBetween(minimumNowRange, maximumNowRange, 's', '[]')).toBe(true);
    });

    it('should detect an RealExam and not set the currentDate', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.isTestExam).toBe(false);
        expect(component.currentDate).toBeUndefined();
    });
});
