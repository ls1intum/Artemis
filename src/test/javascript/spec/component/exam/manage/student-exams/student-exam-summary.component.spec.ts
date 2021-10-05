import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ActivatedRoute } from '@angular/router';
import { MockDirective } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import * as sinon from 'sinon';
import { Exam } from 'app/entities/exam.model';
import { StudentExamSummaryComponent } from 'app/exam/manage/student-exams/student-exam-summary.component';
import { ExamParticipationSummaryComponent } from 'app/exam/participate/summary/exam-participation-summary.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentExamSummaryComponent', () => {
    let fixture: ComponentFixture<StudentExamSummaryComponent>;
    let component: StudentExamSummaryComponent;

    const courseValue = { id: 1 } as Course;
    const examValue = { course: courseValue, id: 2 } as Exam;
    const studentExamValue = { exam: examValue, id: 3 } as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [StudentExamSummaryComponent, MockDirective(ExamParticipationSummaryComponent)],
            providers: [{ provide: ActivatedRoute, useValue: { data: of({ studentExam: studentExamValue }) } }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentExamSummaryComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).to.be.ok;
        expect(component.studentExam).to.deep.equal(studentExamValue);
    });
});
