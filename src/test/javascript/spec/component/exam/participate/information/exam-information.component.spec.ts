import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { StudentExamWorkingTimeComponent } from 'app/exam/shared/student-exam-working-time.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs/esm';
import { MockComponent, MockPipe } from 'ng-mocks';

let fixture: ComponentFixture<ExamInformationComponent>;
let component: ExamInformationComponent;

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

describe('ExamInformationComponent', () => {
    beforeEach(() => {
        exam = { id: 1, title: 'ExamForTesting', startDate, endDate, testExam: false } as Exam;
        studentExam = { id: 1, exam, user, workingTime: 60, submitted: true } as StudentExam;

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [
                ExamInformationComponent,
                MockComponent(StudentExamWorkingTimeComponent),
                MockPipe(ArtemisTranslatePipe),
                MockPipe(ArtemisDatePipe),
                MockPipe(ArtemisDurationFromSecondsPipe),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamInformationComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('should initialize', () => {
        component.exam = exam;
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate).toEqual(exam.endDate);
    });

    it('should return undefined if the exam is not set', () => {
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate).toBeUndefined();
    });

    it('should return the start date plus the working time as the student exam end date', () => {
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.examEndDate?.isSame(dayjs(exam.startDate).add(studentExam.workingTime!, 'seconds'))).toBeTrue();
    });

    it('should detect if the end date is on another day', () => {
        component.exam = exam;
        exam.endDate = dayjs(exam.startDate).add(2, 'days');
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeTrue();
    });

    it('should detect if the working time extends to another day', () => {
        component.exam = exam;
        component.studentExam = studentExam;
        studentExam.workingTime = 24 * 60 * 60;
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeTrue();
    });

    it('should return false for exams that only last one day', () => {
        component.exam = exam;
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeFalse();

        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(fixture).toBeDefined();
        expect(component.isExamOverMultipleDays).toBeFalse();
    });

    it('should detect an TestExam and set the currentDate correctly', () => {
        exam.testExam = true;
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(component.isTestExam).toBeTrue();
        expect(component.currentDate).toBeDefined();
        const acceptanceRange = dayjs().subtract(20, 's');
        expect(component.currentDate!.isBetween(acceptanceRange, dayjs())).toBeTrue();
    });

    it('should detect an RealExam and not set the currentDate', () => {
        component.exam = exam;
        fixture.detectChanges();
        expect(component.isTestExam).toBeFalse();
        expect(component.currentDate).toBeUndefined();
    });
});
