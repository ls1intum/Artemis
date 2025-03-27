import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamGeneralInformationComponent } from 'app/exam/overview/general-information/exam-general-information.component';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from '../../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { input } from '@angular/core';

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
        jest.resetAllMocks();
    });

    it('should initialize', () => {
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input({} as StudentExam);
        });
        fixture.detectChanges();
        component.ngOnChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate).toEqual(exam.endDate);
    });

    it('should return the start date plus the working time as the student exam end date', () => {
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input(studentExam);
        });
        fixture.detectChanges();
        component.ngOnChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate?.isSame(dayjs(exam.startDate).add(studentExam.workingTime!, 'seconds'))).toBeTrue();
    });

    it('should detect if the end date is on another day', () => {
        exam.endDate = dayjs(exam.startDate).add(2, 'days');
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input({} as StudentExam);
        });
        fixture.detectChanges();
        component.ngOnChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeTrue();
    });

    it('should detect if the working time extends to another day', () => {
        studentExam.workingTime = 24 * 60 * 60;
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input(studentExam);
        });
        fixture.detectChanges();
        component.ngOnChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeTrue();
    });

    it('should return false for exams that only last one day', () => {
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input(studentExam);
        });
        fixture.detectChanges();
        component.ngOnChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeFalse();

        fixture.detectChanges();
        component.ngOnChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeFalse();
    });

    it('should detect an TestExam and set the currentDate correctly', () => {
        exam.testExam = true;
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input(studentExam);
        });
        const minimumNowRange = dayjs();
        fixture.detectChanges();
        component.ngOnChanges();
        const maximumNowRange = dayjs();
        expect(component.isTestExam).toBeTrue();
        expect(component.currentDate).toBeDefined();
        // test execution could slow down the check
        expect(component.currentDate!.isBetween(minimumNowRange, maximumNowRange, 's', '[]')).toBeTrue();
    });

    it('should detect an RealExam and not set the currentDate', () => {
        TestBed.runInInjectionContext(() => {
            component.exam = input(exam);
            component.studentExam = input(studentExam);
        });
        fixture.detectChanges();
        component.ngOnChanges();
        expect(component.isTestExam).toBeFalse();
        expect(component.currentDate).toBeUndefined();
    });
});
