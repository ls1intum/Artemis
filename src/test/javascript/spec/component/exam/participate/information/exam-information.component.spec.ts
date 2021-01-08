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

const visibleDate = moment().subtract(6, 'hours');
const startDate = moment().subtract(5, 'hours');
const endDate = moment().subtract(4, 'hours');
const publishResultsDate = moment().subtract(3, 'hours');
const reviewStartDate = moment().subtract(2, 'hours');
const reviewEndDate = moment().add(1, 'hours');

const exam = {
    id: 1,
    title: 'Test Exam',
    visibleDate: visibleDate,
    startDate: startDate,
    endDate: endDate,
    publishResultsDate: publishResultsDate,
    examStudentReviewStart: reviewStartDate,
    examStudentReviewEnd: reviewEndDate,
} as Exam;

const studentExam = { id: 1, exam: exam, user: user } as StudentExam;

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
                component.studentExam = studentExam;
                component.exam = exam;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', function () {
        fixture.detectChanges();
        expect(fixture).to.be.ok;
    });
});
