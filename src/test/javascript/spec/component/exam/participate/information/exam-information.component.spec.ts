import * as sinon from 'sinon';
import * as chai from 'chai';
import * as moment from 'moment';
import * as sinonChai from 'sinon-chai';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ExamInformationComponent } from 'app/exam/participate/information/exam-information.component';
import { RouterTestingModule } from '@angular/router/testing';
import { MockPipe } from 'ng-mocks';
import { TranslatePipe } from '@ngx-translate/core';
import { User } from 'app/core/user/user.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExam } from 'app/entities/student-exam.model';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';

chai.use(sinonChai);
const expect = chai.expect;

let fixture: ComponentFixture<ExamInformationComponent>;
let component: ExamInformationComponent;

const user = { id: 1, name: 'Test User' } as User;

const startDate = moment().subtract(5, 'hours');
const endDate = moment().subtract(4, 'hours');

const exam = {
    id: 1,
    title: 'Test Exam',
    startDate: startDate,
    endDate: endDate,
} as Exam;

const studentExam = { id: 1, exam: exam, user: user, workingTime: 60 } as StudentExam;

describe('ExamInformationComponent', function () {
    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([])],
            declarations: [ExamInformationComponent, MockPipe(TranslatePipe), MockPipe(ArtemisDatePipe), MockPipe(ArtemisDurationFromSecondsPipe)],
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
});
