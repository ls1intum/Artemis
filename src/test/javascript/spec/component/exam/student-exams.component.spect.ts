import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as chai from 'chai';
import * as sinonChai from 'sinon-chai';
import { ActivatedRoute, convertToParamMap, Params } from '@angular/router';
import { StudentExamsComponent } from 'app/exam/manage/student-exams/student-exams.component';
import { ArtemisDataTableModule } from 'app/shared/data-table/data-table.module';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { StudentExamService } from 'app/exam/manage/student-exams/student-exam.service';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { NgbModalModule } from '@ng-bootstrap/ng-bootstrap';
import { JhiAlertService, JhiTranslateDirective } from 'ng-jhipster';
import { TranslateModule } from '@ngx-translate/core';
import { StudentExamStatusComponent } from 'app/exam/manage/student-exams/student-exam-status.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { NgxDatatableModule } from '@swimlane/ngx-datatable';
import { FontAwesomeTestingModule } from '@fortawesome/angular-fontawesome/testing';
import { RouterTestingModule } from '@angular/router/testing';
import { ArtemisDurationFromSecondsPipe } from 'app/shared/pipes/artemis-duration-from-seconds.pipe';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { MockLocalStorageService } from '../../helpers/mocks/service/mock-local-storage.service';
import { LocalStorageService } from 'ngx-webstorage';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { StudentExam } from 'app/entities/student-exam.model';
import * as sinon from 'sinon';
import { Exam } from 'app/entities/exam.model';

chai.use(sinonChai);
const expect = chai.expect;

describe('StudentExamsComponent', () => {
    let studentExamsComponentFixture: ComponentFixture<StudentExamsComponent>;
    let studentExamsComponent: StudentExamsComponent;
    let course: Course;
    let studentExamOne: StudentExam;
    let exam: Exam;

    beforeEach(() => {
        course = new Course();
        course.id = 1;

        exam = new Exam();
        exam.course = course;
        exam.id = 1;

        studentExamOne = new StudentExam();
        studentExamOne.exam = exam;
        studentExamOne.id = 1;

        studentExamOne = new StudentExam();
        return TestBed.configureTestingModule({
            imports: [RouterTestingModule.withRoutes([]), ArtemisDataTableModule, NgbModalModule, NgxDatatableModule, FontAwesomeTestingModule, TranslateModule.forRoot()],
            declarations: [
                StudentExamsComponent,
                MockComponent(StudentExamStatusComponent),
                MockComponent(AlertComponent),
                MockPipe(ArtemisDurationFromSecondsPipe),
                MockPipe(ArtemisDatePipe),
            ],
            providers: [
                MockProvider(ExamManagementService, {
                    find: () => {
                        return of(
                            new HttpResponse({
                                body: exam,
                                status: 200,
                            }),
                        );
                    },
                }),
                MockProvider(StudentExamService, {
                    findAllForExam: () => {
                        return of(
                            new HttpResponse({
                                body: [studentExamOne],
                                status: 200,
                            }),
                        );
                    },
                }),
                MockProvider(CourseManagementService, {
                    find: () => {
                        return of(
                            new HttpResponse({
                                body: course,
                                status: 200,
                            }),
                        );
                    },
                }),
                MockProvider(JhiAlertService),
                MockDirective(JhiTranslateDirective),
                {
                    provide: LocalStorageService,
                    useClass: MockLocalStorageService,
                },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        params: {
                            subscribe: (fn: (value: Params) => void) =>
                                fn({
                                    courseId: 1,
                                }),
                        },
                        snapshot: {
                            paramMap: convertToParamMap({
                                courseId: '1',
                                examId: '1',
                            }),
                        },
                    },
                },
            ],
        })
            .compileComponents()
            .then(() => {
                studentExamsComponentFixture = TestBed.createComponent(StudentExamsComponent);
                studentExamsComponent = studentExamsComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        sinon.restore();
    });

    it('should initialize', () => {
        studentExamsComponentFixture.detectChanges();
        expect(studentExamsComponentFixture).to.be.ok;
        expect(studentExamsComponent.course).to.deep.equal(course);
        expect(studentExamsComponent.studentExams).to.deep.equal([studentExamOne]);
        expect(studentExamsComponent.exam).to.deep.equal(exam);
    });
});
