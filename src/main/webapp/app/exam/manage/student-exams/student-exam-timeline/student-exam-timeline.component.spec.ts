import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Observable, Subject, of } from 'rxjs';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExamTimelineComponent } from 'app/exam/manage/student-exams/student-exam-timeline/student-exam-timeline.component';
import { ModelingExamSubmissionComponent } from 'app/exam/overview/exercises/modeling/modeling-exam-submission.component';
import { TextExamSubmissionComponent } from 'app/exam/overview/exercises/text/text-exam-submission.component';
import { QuizExamSubmissionComponent } from 'app/exam/overview/exercises/quiz/quiz-exam-submission.component';
import { FileUploadExamSubmissionComponent } from 'app/exam/overview/exercises/file-upload/file-upload-exam-submission.component';
import { ArtemisDatePipe } from 'app/shared/pipes/artemis-date.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ExamNavigationBarComponent } from 'app/exam/overview/exam-navigation-bar/exam-navigation-bar.component';
import { MockTranslateValuesDirective } from 'test/helpers/mocks/directive/mock-translate-values.directive';
import { EntityArrayResponseType, SubmissionService } from 'app/exercise/submission/submission.service';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import dayjs from 'dayjs/esm';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import { signal } from '@angular/core';
import { ExamSubmissionComponent } from 'app/exam/overview/exercises/exam-submission.component';
import { SubmissionVersionService } from 'app/exercise/submission-version/submission-version.service';
import { ProgrammingExerciseExamDiffComponent } from 'app/exam/manage/student-exams/student-exam-timeline/programming-exam-diff/programming-exercise-exam-diff.component';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { MatSlider } from '@angular/material/slider';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { SubmissionVersion } from 'app/exam/shared/entities/submission-version.model';

describe('Student Exam Timeline Component', () => {
    let fixture: ComponentFixture<StudentExamTimelineComponent>;
    let component: StudentExamTimelineComponent;
    let submissionService: SubmissionService;
    let submissionVersionService: SubmissionVersionService;

    const courseValue = { id: 1 } as Course;
    const examValue = { course: courseValue, id: 2 } as Exam;
    const participation = { id: 1, exercise: { id: 1 } } as unknown as StudentParticipation;
    let programmingSubmission1 = {
        id: 1,
        submissionDate: dayjs('2023-02-07'),
        participation,
    } as unknown as ProgrammingSubmission;
    const programmingSubmission2 = {
        id: 2,
        submissionDate: dayjs('2023-03-07'),
        participation,
    } as unknown as ProgrammingSubmission;
    const programmingSubmission3 = {
        id: 3,
        submissionDate: dayjs('2023-04-07'),
        participation,
    } as unknown as ProgrammingSubmission;
    let fileUploadSubmission1 = {
        id: 5,
        submissionDate: dayjs('2023-05-07'),
        filePath: 'abc',
    } as unknown as FileUploadSubmission;

    let textSubmission = { id: 2, submissionDate: dayjs('2023-01-07'), text: 'abc' } as unknown as TextSubmission;
    const programmingExercise = {
        id: 1,
        type: 'programming',
        studentParticipations: [{ id: 1, submissions: [programmingSubmission1] }],
    } as ProgrammingExercise;
    const textExercise = {
        id: 2,
        type: 'text',
        studentParticipations: [{ id: 2, submissions: [textSubmission] }],
    } as TextExercise;
    const fileUploadExercise = {
        id: 3,
        type: 'file-upload',
        studentParticipations: [{ id: 3, submissions: [fileUploadSubmission1] }],
    } as FileUploadExercise;
    const studentExamValue = {
        exam: examValue,
        id: 3,
        exercises: [textExercise, programmingExercise, fileUploadExercise],
        user: { login: 'abc' },
    } as unknown as StudentExam;
    programmingSubmission1 = {
        ...programmingSubmission1,
        participation: { exercise: programmingExercise },
    } as unknown as ProgrammingSubmission;
    fileUploadSubmission1 = {
        ...fileUploadSubmission1,
        participation: { exercise: fileUploadExercise },
    } as unknown as FileUploadSubmission;
    textSubmission = { ...textSubmission, participation: { exercise: textExercise } } satisfies TextSubmission;
    const submissionVersion = {
        id: 1,
        createdDate: dayjs('2023-01-07'),
        content: 'abc',
        submission: textSubmission,
    } as unknown as SubmissionVersion;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            declarations: [
                StudentExamTimelineComponent,
                MockComponent(ProgrammingExerciseExamDiffComponent),
                MockComponent(ModelingExamSubmissionComponent),
                MockComponent(TextExamSubmissionComponent),
                MockComponent(QuizExamSubmissionComponent),
                MockComponent(FileUploadExamSubmissionComponent),
                MockPipe(ArtemisTranslatePipe),
                MockTranslateValuesDirective,
                MockComponent(ExamNavigationBarComponent),
                MockComponent(MatSlider),
            ],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: { data: of({ studentExam: { studentExam: studentExamValue } }) },
                },
                SessionStorageService,
                LocalStorageService,
                ArtemisDatePipe,
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(StudentExamTimelineComponent);
                component = fixture.componentInstance;
                submissionService = TestBed.inject(SubmissionService);
                submissionVersionService = TestBed.inject(SubmissionVersionService);
                fixture.detectChanges();
                jest.spyOn(component.examNavigationBarComponent(), 'changePage').mockImplementation(() => {});
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch submission versions and submissions in retrieveSubmissionData', fakeAsync(() => {
        const submissionServiceSpy = jest
            .spyOn(submissionService, 'findAllSubmissionsOfParticipation')
            .mockReturnValueOnce(of({ body: [programmingSubmission1] }) as unknown as Observable<EntityArrayResponseType>)
            .mockReturnValueOnce(of({ body: [fileUploadSubmission1] }) as unknown as Observable<EntityArrayResponseType>);
        const submissionVersionServiceSpy = jest
            .spyOn(submissionVersionService, 'findAllSubmissionVersionsOfSubmission')
            .mockReturnValueOnce(of([submissionVersion]) as unknown as Observable<SubmissionVersion[]>);
        component.studentExam = studentExamValue;
        component.retrieveSubmissionDataAndTimeStamps().subscribe((results) => {
            expect(results).toEqual([[submissionVersion], [programmingSubmission1], [fileUploadSubmission1]]);
        });
        tick();
        expect(submissionServiceSpy).toHaveBeenCalledTimes(2);
        expect(submissionVersionServiceSpy).toHaveBeenCalledOnce();
        expect(submissionVersionServiceSpy).toHaveBeenCalledWith(2);
    }));

    it('should fetch submission versions and submissions on init using retrieveSubmissionData', () => {
        const retrieveDataSpy = jest
            .spyOn(component, 'retrieveSubmissionDataAndTimeStamps')
            .mockReturnValue(of([[submissionVersion], [programmingSubmission1], [fileUploadSubmission1]]) as unknown as Observable<(SubmissionVersion[] | Submission[])[]>);
        component.ngOnInit();
        expect(retrieveDataSpy).toHaveBeenCalledOnce();
        expect(component.currentSubmission).toEqual(submissionVersion);
        expect(component.selectedTimestamp).toEqual(dayjs('2023-01-07').valueOf());
        expect(component.submissionTimeStamps).toEqual([dayjs('2023-01-07'), dayjs('2023-02-07'), dayjs('2023-05-07')]);
        expect(component.submissionVersions).toEqual([submissionVersion]);
        expect(component.fileUploadSubmissions).toEqual([fileUploadSubmission1]);
        expect(component.programmingSubmissions).toEqual([programmingSubmission1]);
    });

    it('should subscribe to changes in ViewAfterInit', () => {
        component.currentPageComponents = signal([]);
        component.ngAfterViewInit();
        expect(component.changesSubscription).toBeDefined();
    });

    it.each([
        { exercise: programmingExercise, submission: programmingSubmission1 },
        { exercise: fileUploadExercise, submission: fileUploadSubmission1 },
        { exercise: textExercise, submission: submissionVersion },
        { exercise: programmingExercise, submission: undefined },
        { exercise: textExercise, submission: undefined },
        { exercise: fileUploadExercise, submission: undefined },
    ])('should correctly set the values onPageChange', ({ exercise, submission }) => {
        // Create a mock model signal that supports update()
        const createMockSignal = <T>(initial: T) => ({
            value: initial,
            update: jest.fn(function (fn: (prev: T) => T) {
                this.value = fn(this.value);
                return this.value;
            }),
        });

        // Use spyOn to override activePageComponent and provide the necessary update() methods.
        jest.spyOn(component, 'activePageComponent', 'get').mockReturnValue({
            studentParticipation: createMockSignal({ submissions: [] }),
            exercise: createMockSignal({} as ProgrammingExercise),
            currentSubmission: createMockSignal({} as ProgrammingSubmission),
            previousSubmission: createMockSignal({}),
            submissions: createMockSignal([]),
            exerciseIdSubject: createMockSignal(new Subject<number>()),
            studentSubmission: { update: jest.fn() },
            onDeactivate() {},
            onActivate() {},
            updateViewFromSubmission() {},
            setSubmissionVersion() {},
        } as unknown as ExamSubmissionComponent);

        let expectedSubmission = submission;
        // Set the current timestamp needed to find the closest submission if no submission is set
        if (!submission) {
            component.selectedTimestamp = dayjs('2023-01-07').valueOf();
            if (exercise === programmingExercise) {
                expectedSubmission = programmingSubmission1;
                component.currentExercise = fileUploadExercise;
            }
            if (exercise === fileUploadExercise) {
                expectedSubmission = fileUploadSubmission1;
                component.currentExercise = programmingExercise;
            }
            if (exercise === textExercise) {
                expectedSubmission = submissionVersion;
                component.currentExercise = programmingExercise;
            }
        }
        component.submissionVersions = [submissionVersion];
        component.fileUploadSubmissions = [fileUploadSubmission1];
        component.programmingSubmissions = [programmingSubmission1];
        component.submissionTimeStamps = [dayjs('2023-01-07'), dayjs('2023-02-07'), dayjs('2023-05-07')];
        fixture.detectChanges();

        component.onPageChange({
            exercise: exercise,
            submission: submission,
        });

        expect(component.currentSubmission).toEqual(expectedSubmission);
        expect(component.currentExercise).toEqual(exercise);
        // For text exercise, the submission version is used to set the timestamp.
        if (exercise === textExercise) {
            const submissionVersionObj = component.currentSubmission as SubmissionVersion;
            expect(component.selectedTimestamp).toEqual(submissionVersionObj.createdDate.valueOf());
        } else if (exercise === programmingExercise) {
            const programmingSubmission = component.currentSubmission as ProgrammingSubmission;
            expect(component.selectedTimestamp).toEqual(programmingSubmission.submissionDate?.valueOf());
        } else {
            expect(component.selectedTimestamp).toEqual(fileUploadSubmission1.submissionDate?.valueOf());
        }
    });

    it.each([0, 1, 2])(
        'should correctly set the values onInputChange',
        fakeAsync((index: number) => {
            component.submissionVersions = [submissionVersion];
            component.fileUploadSubmissions = [fileUploadSubmission1];
            component.programmingSubmissions = [programmingSubmission1];
            component.submissionTimeStamps = [dayjs('2023-01-07'), dayjs('2023-02-07'), dayjs('2023-05-07')];
            component.timestampIndex = index;

            //when
            component.onSliderInputChange();
            fixture.detectChanges();
            //then
            if (dayjs(component.submissionTimeStamps[component.timestampIndex]).isSame(dayjs('2023-01-07'))) {
                expect(component.currentSubmission).toEqual(submissionVersion);
                expect(component.exerciseIndex).toBe(0);
                expect(component.currentExercise).toEqual(textExercise);
            } else if (dayjs(component.submissionTimeStamps[component.timestampIndex]).isSame(dayjs('2023-02-07'))) {
                expect(component.currentSubmission).toEqual(programmingSubmission1);
                expect(component.exerciseIndex).toBe(1);
                expect(component.currentExercise).toEqual(programmingExercise);
            } else {
                expect(component.currentSubmission).toEqual(fileUploadSubmission1);
                expect(component.exerciseIndex).toBe(2);
                expect(component.currentExercise).toEqual(fileUploadExercise);
            }
            expect(component.selectedTimestamp).toEqual(component.submissionTimeStamps[component.timestampIndex].valueOf());
        }),
    );
    it.each([programmingSubmission1, programmingSubmission2, programmingSubmission3])(
        'should correctly determine the previous submission',
        (currentSubmission: ProgrammingSubmission) => {
            component.programmingSubmissions = [programmingSubmission1, programmingSubmission2, programmingSubmission3];
            const exercise = { id: 1 } as ProgrammingExercise;
            const actualPreviousSubmission = component.findPreviousProgrammingSubmission(exercise, currentSubmission);
            if (currentSubmission.id === 1) {
                expect(actualPreviousSubmission).toBeUndefined();
            }
            if (currentSubmission.id === 2) {
                expect(actualPreviousSubmission).toEqual(programmingSubmission1);
            }
            if (currentSubmission.id === 3) {
                expect(actualPreviousSubmission).toEqual(programmingSubmission2);
            }
        },
    );
});
