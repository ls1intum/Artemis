import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { of } from 'rxjs';
import { StudentExam } from 'app/entities/student-exam.model';
import { Exam } from 'app/entities/exam.model';
import { StudentExamTimelineComponent } from 'app/exam/manage/student-exams/student-exam-timeline/student-exam-timeline.component';
import { ProgrammingExamSubmissionComponent } from 'app/exam/participate/exercises/programming/programming-exam-submission.component';
import { ModelingExamSubmissionComponent } from 'app/exam/participate/exercises/modeling/modeling-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/participate/exercises/text/text-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/participate/exercises/quiz/quiz-exam-submission.component';
import { FileUploadExamSubmissionComponent } from 'app/exam/participate/exercises/file-upload/file-upload-exam-submission.component';
import { SliderComponent } from 'ngx-slider-v2';
import { ArtemisTestModule } from '../../../../test.module';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExamNavigationBarComponent } from 'app/exam/participate/exam-navigation-bar/exam-navigation-bar.component';
import { LoadingIndicatorContainerStubComponent } from '../../../../helpers/stubs/loading-indicator-container-stub.component';
import { MockTranslateValuesDirective } from '../../../../helpers/mocks/directive/mock-translate-values.directive';
import { NgxSliderStubComponent } from '../../../../helpers/stubs/ngx-slider-stub.component';

describe('Student Exam Timeline Component', () => {
    let fixture: ComponentFixture<StudentExamTimelineComponent>;
    let component: StudentExamTimelineComponent;

    const courseValue = { id: 1 } as Course;
    const examValue = { course: courseValue, id: 2 } as Exam;
    const studentExamValue = { exam: examValue, id: 3, exercises: [], user: { login: 'abc' } } as unknown as StudentExam;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                StudentExamTimelineComponent,
                NgxSliderStubComponent,
                MockComponent(ProgrammingExamSubmissionComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(FileUploadExamSubmissionComponent),
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ExamNavigationBarComponent),
                MockTranslateValuesDirective,
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { data: of({ studentExam: { studentExam: studentExamValue } }) },
                },

                ArtemisDatePipe,
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentExamTimelineComponent);
                component = fixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();
        expect(component).not.toBeNull();
        expect(component.studentExam).toEqual(studentExamValue);
    });
});
