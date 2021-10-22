import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import dayjs from 'dayjs';
import { MockPipe } from 'ng-mocks';

let fixture: ComponentFixture<ExamInformationComponent>;
let component: ExamInformationComponent;

const user = { id: 1, name: 'Test User' } as User;

const startDate = dayjs().subtract(5, 'hours');
const endDate = dayjs().subtract(4, 'hours');

let exam = {
    id: 1,
    title: 'Test Exam',
    startDate,
    endDate,
} as Exam;

let studentExam = { id: 1, exam, user, workingTime: 60 } as StudentExam;

describe('ExamInformationComponent', function () {
    beforeEach(() => {
        exam = { id: 1, title: 'Test Exam', startDate, endDate } as Exam;
        studentExam = { id: 1, exam, user, workingTime: 60 } as StudentExam;

        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [ExamInformationComponent, MockPipe(ArtemisTranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(ArtemisDurationFromSecondsPipe)],
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

    it('should initialize', function () {
        component.exam = exam;
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.examEndDate).toEqual(exam.endDate);
    });

    it('should return undefined if the exam is not set', function () {
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.examEndDate).toBeUndefined();
    });

    it('should return the start date plus the working time as the student exam end date', function () {
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.examEndDate?.isSame(dayjs(exam.startDate).add(studentExam.workingTime!, 'seconds'))).toEqual(true);
    });

    it('should detect if the end date is on another day', function () {
        component.exam = exam;
        exam.endDate = dayjs(exam.startDate).add(2, 'days');
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.isExamOverMultipleDays).toBe(true);
    });

    it('should detect if the working time extends to another day', function () {
        component.exam = exam;
        component.studentExam = studentExam;
        studentExam.workingTime = 24 * 60 * 60;
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.isExamOverMultipleDays).toBe(true);
    });

    it('should return false for exams that only last one day', function () {
        component.exam = exam;
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.isExamOverMultipleDays).toBe(false);

        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(fixture).not.toBeUndefined();
        expect(component.isExamOverMultipleDays).toBe(false);
    });
});
