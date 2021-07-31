import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import * as moment from 'moment';
import { MockPipe } from 'ng-mocks';
import * as sinon from 'sinon';
import * as sinonChai from 'sinon-chai';

chai.use(sinonChai);
const expect = chai.expect;

let fixture: ComponentFixture<ExamInformationComponent>;
let component: ExamInformationComponent;

const user = { id: 1, name: 'Test User' } as User;

const startDate = moment().subtract(5, 'hours');
const endDate = moment().subtract(4, 'hours');

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
            providers: [],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamInformationComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', function () {
        component.exam = exam;
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.endTime()).to.equal(exam.endDate);
    });

    it('should return undefined if the exam is not set', function () {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.endTime()).to.not.exist;
    });

    it('should return the start date plus the working time as the student exam end date', function () {
        component.exam = exam;
        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.endTime()?.isSame(moment(exam.startDate).add(studentExam.workingTime, 'seconds'))).to.equal(true);
    });

    it('should detect if the end date is on another day', function () {
        component.exam = exam;
        exam.endDate = moment(exam.startDate).add(2, 'days');
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.isExamOverMultipleDays()).to.be.true;
    });

    it('should detect if the working time extends to another day', function () {
        component.exam = exam;
        component.studentExam = studentExam;
        studentExam.workingTime = 24 * 60 * 60;
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.isExamOverMultipleDays()).to.be.true;
    });

    it('should return false for exams that only last one day', function () {
        component.exam = exam;
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.isExamOverMultipleDays()).to.be.false;

        component.studentExam = studentExam;
        fixture.detectChanges();
        expect(fixture).to.be.ok;
        expect(component.isExamOverMultipleDays()).to.be.false;
    });
});
