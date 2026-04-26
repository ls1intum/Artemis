import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { ExamStartInformationComponent } from 'app/exam/overview/exam-start-information/exam-start-information.component';
import { InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { StudentExamWorkingTimeComponent } from 'app/exam/overview/student-exam-working-time/student-exam-working-time.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';

import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideRouter } from '@angular/router';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

let fixture: ComponentFixture<ExamStartInformationComponent>;
let component: ExamStartInformationComponent;

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

describe('ExamStartInformationComponent', () => {
    setupTestBed({ zoneless: true });

    beforeEach(() => {
        exam = { id: 1, title: 'ExamForTesting', examMaxPoints: 10, startDate, endDate, testExam: false } as Exam;
        studentExam = { id: 1, exam, user, workingTime: 60, submitted: true } as StudentExam;

        return TestBed.configureTestingModule({
            declarations: [
                ExamStartInformationComponent,
                MockComponent(StudentExamWorkingTimeComponent),
                MockComponent(InformationBoxComponent),
                MockDirective(TranslateDirective),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisDurationFromSecondsPipe),
            ],
            providers: [provideRouter([])],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamStartInformationComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        vi.resetAllMocks();
    });

    it('should initialize with the correct start date', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.startDate).toEqual(exam.startDate);
    });

    it('should return undefined if the exam is not set', () => {
        const examNoStartDate = { ...exam, startDate: undefined } as Exam;
        fixture.componentRef.setInput('exam', examNoStartDate);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.startDate).toBeUndefined();
    });

    it('should initialize total points of the exam correctly', () => {
        const examWithPoints = { ...exam, examMaxPoints: 120 } as Exam;
        fixture.componentRef.setInput('exam', examWithPoints);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.totalPoints).toBe(120);
    });

    it('should give total working time in minutes', () => {
        const examWithWorkingTime = { ...exam, workingTime: 60 * 60 * 2 } as Exam;
        fixture.componentRef.setInput('exam', examWithWorkingTime);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.totalWorkingTimeInMinutes).toBe(120);
    });

    it('should initialize module number of the exam correctly', () => {
        const examWithModule = { ...exam, moduleNumber: 'IN18000' } as Exam;
        fixture.componentRef.setInput('exam', examWithModule);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.moduleNumber).toBe('IN18000');
    });

    it('should initialize course name of the exam correctly', () => {
        const examWithCourse = { ...exam, courseName: 'Software Engineering' } as Exam;
        fixture.componentRef.setInput('exam', examWithCourse);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.courseName).toBe('Software Engineering');
    });

    it('should initialize examiner of the exam correctly', () => {
        const examWithExaminer = { ...exam, examiner: 'Prof. Dr. Stephan Krusche' } as Exam;
        fixture.componentRef.setInput('exam', examWithExaminer);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.examiner).toBe('Prof. Dr. Stephan Krusche');
    });

    it('should initialize number of exercises of the exam correctly', () => {
        const examWithExercises = { ...exam, numberOfExercisesInExam: 10 } as Exam;
        fixture.componentRef.setInput('exam', examWithExercises);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.numberOfExercisesInExam).toBe(10);
    });

    it('should initialize examined student of the exam correctly', () => {
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.examinedStudent).toBe('Test User');
    });

    it('should initialize start date of the exam correctly', () => {
        const examStartDate = dayjs('2022-02-06 02:00:00');
        fixture.componentRef.setInput('exam', exam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.startDate).toStrictEqual(examStartDate);
    });

    it('should initialize start date of the test exam correctly', () => {
        const examStartDate = dayjs('2022-02-06 02:00:00');
        const testExam = { ...exam, testExam: true } as Exam;
        fixture.componentRef.setInput('exam', testExam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.startDate).toStrictEqual(examStartDate);
    });

    it('should initialize end date of the test exam correctly', () => {
        const examEndDate = dayjs('2022-02-06 02:00:00').add(1, 'hours');
        const testExam = { ...exam, testExam: true } as Exam;
        fixture.componentRef.setInput('exam', testExam);
        fixture.componentRef.setInput('studentExam', studentExam);
        fixture.changeDetectorRef.detectChanges();
        expect(component.endDate).toStrictEqual(examEndDate);
    });

    it('should create all information boxes if all information of the exam are set', () => {
        const exam1 = {
            id: 1,
            title: 'ExamForTesting',
            examMaxPoints: 10,
            startDate,
            endDate,
            workingTime: 60 * 60,
            moduleNumber: 'CIT530000',
            courseName: 'Test Course',
            examiner: 'Test User Examiner',
            numberOfExercisesInExam: 5,
            gracePeriod: 30,
        } as Exam;

        const studentExam1 = { id: 1, exam, user } as StudentExam;

        const informationBoxStub = vi.spyOn(component, 'buildInformationBox');
        const informationBoxDataStub = vi.spyOn(component, 'prepareInformationBoxData');
        fixture.componentRef.setInput('exam', exam1);
        fixture.componentRef.setInput('studentExam', studentExam1);
        fixture.changeDetectorRef.detectChanges();
        expect(informationBoxStub).toHaveBeenCalledTimes(8);
        expect(informationBoxDataStub).toHaveBeenCalledOnce();
    });
});
