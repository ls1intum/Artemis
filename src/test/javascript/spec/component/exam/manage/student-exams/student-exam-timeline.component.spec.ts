import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Course } from 'app/entities/course.model';
import { Observable, of } from 'rxjs';
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
import { EntityArrayResponseType, SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmission } from 'app/entities/programming-submission.model';
import { FileUploadSubmission } from 'app/entities/file-upload-submission.model';
import { SubmissionVersion } from 'app/entities/submission-version.model';
import { TextSubmission } from 'app/entities/text-submission.model';

describe('Student Exam Timeline Component', () => {
    let fixture: ComponentFixture<StudentExamTimelineComponent>;
    let component: StudentExamTimelineComponent;
    let submissionService: SubmissionService;

    const courseValue = { id: 1 } as Course;
    const examValue = { course: courseValue, id: 2 } as Exam;
    const programmingSubmission = { id: 1, submissionDate: dayjs('2023-01-07') } as unknown as ProgrammingSubmission;
    const fileUploadSubmission = { id: 5, submissionDate: dayjs('2023-05-07') } as unknown as FileUploadSubmission;
    const textSubmission = { id: 2, submissionDate: dayjs('2023-02-07'), text: 'abc' } as unknown as TextSubmission;
    const submissionVersion = { id: 1, createdDate: dayjs('2023-02-07'), content: 'abc', submission: textSubmission } as unknown as SubmissionVersion;
    const programmingExercise = { id: 1, type: 'programming', studentParticipations: [{ id: 1, submissions: [programmingSubmission] }] } as ProgrammingExercise;
    const textExercise = { id: 2, type: 'text', studentParticipations: [{ id: 2, submissions: [textSubmission] }] } as TextExercise;
    const fileUploadExercise = { id: 3, type: 'file-upload', studentParticipations: [{ id: 3, submissions: [fileUploadSubmission] }] } as FileUploadExercise;
    const studentExamValue = { exam: examValue, id: 3, exercises: [textExercise, programmingExercise, fileUploadExercise], user: { login: 'abc' } } as unknown as StudentExam;

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
                submissionService = TestBed.inject(SubmissionService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch submission versions and submissions in retrieveSubmissionData', fakeAsync(() => {
        const submissionServiceSpy = jest
            .spyOn(submissionService, 'findAllSubmissionsOfParticipation')
            .mockReturnValueOnce(of({ body: [programmingSubmission] }) as unknown as Observable<EntityArrayResponseType>)
            .mockReturnValueOnce(of({ body: [fileUploadSubmission] }) as unknown as Observable<EntityArrayResponseType>);
        const submissionServiceSubmissionVersionsSpy = jest
            .spyOn(submissionService, 'findAllSubmissionVersionsOfSubmission')
            .mockReturnValueOnce(of([submissionVersion]) as unknown as Observable<SubmissionVersion[]>);
        component.studentExam = studentExamValue;
        component.retrieveSubmissionDataAndTimeStamps().subscribe((results) => {
            expect(results).toEqual([programmingSubmission, fileUploadSubmission, submissionVersion]);
        });
        tick();
        expect(component.studentExam).toEqual(studentExamValue);
        //expect(submissionServiceSpy).toHaveBeenCalledTimes(2);
        expect(submissionServiceSubmissionVersionsSpy).toHaveBeenCalledOnce();
        expect(submissionServiceSubmissionVersionsSpy).toHaveBeenCalledWith(2);
        // expect(component.currentSubmission).toEqual(programmingSubmission);
        // expect(component.value).toEqual(dayjs('2023-01-07').valueOf());
    }));
});
