import { ComponentFixture, TestBed } from '@angular/core/testing';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamStartInformationComponent } from 'app/exam/participate/exam-start-information/exam-start-information.component';
import { InformationBoxComponent } from 'app/shared/information-box/information-box.component';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time/student-exam-working-time.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { ArtemisSharedModule } from 'app/shared/shared.module';
import { ArtemisSharedComponentModule } from 'app/shared/components/shared-component.module';
import { ArtemisExamSharedModule } from 'app/exam/shared/exam-shared.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { provideRouter } from '@angular/router';

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
    beforeEach(() => {
        exam = { id: 1, title: 'ExamForTesting', examMaxPoints: 10, startDate, endDate, testExam: false } as Exam;
        studentExam = { id: 1, exam, user, workingTime: 60, submitted: true } as StudentExam;

        return TestBed.configureTestingModule({
            imports: [ArtemisSharedModule, ArtemisSharedComponentModule, ArtemisExamSharedModule],
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
        jest.resetAllMocks();
    });

    it('should initialize with the correct start date', () => {
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.startDate).toEqual(exam.startDate);
    });

    it('should return undefined if the exam is not set', () => {
        exam.startDate = undefined;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.startDate).toBeUndefined();
    });

    it('should initialize total points of the exam correctly', () => {
        exam.examMaxPoints = 120;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.totalPoints).toBe(120);
    });

    it('should give total working time in minutes', () => {
        exam.workingTime = 60 * 60 * 2;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.totalWorkingTimeInMinutes).toBe(120);
    });

    it('should initialize module number of the exam correctly', () => {
        exam.moduleNumber = 'IN18000';
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.moduleNumber).toBe('IN18000');
    });

    it('should initialize course name of the exam correctly', () => {
        exam.courseName = 'Software Engineering';
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.courseName).toBe('Software Engineering');
    });

    it('should initialize examiner of the exam correctly', () => {
        exam.examiner = 'Prof. Dr. Stephan Krusche';
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.examiner).toBe('Prof. Dr. Stephan Krusche');
    });

    it('should initialize number of exercises of the exam correctly', () => {
        exam.numberOfExercisesInExam = 10;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.numberOfExercisesInExam).toBe(10);
    });

    it('should initialize examined student of the exam correctly', () => {
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.examinedStudent).toBe('Test User');
    });

    it('should initialize start date of the exam correctly', () => {
        const examStartDate = dayjs('2022-02-06 02:00:00');
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.startDate).toStrictEqual(examStartDate);
    });

    it('should initialize start date of the test exam correctly', () => {
        const examStartDate = dayjs('2022-02-06 02:00:00');
        exam.testExam = true;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.startDate).toStrictEqual(examStartDate);
    });

    it('should initialize end date of the test exam correctly', () => {
        const examEndDate = dayjs('2022-02-06 02:00:00').add(1, 'hours');
        exam.testExam = true;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
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

        component.exam = exam1;
        component.studentExam = studentExam1;
        const informationBoxStub = jest.spyOn(component, 'buildInformationBox');
        const informationBoxDataStub = jest.spyOn(component, 'prepareInformationBoxData');
        fixture.detectChanges();
        expect(informationBoxStub).toHaveBeenCalledTimes(8);
        expect(informationBoxDataStub).toHaveBeenCalledOnce();
    });
});
