import { MockInstance, afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideTranslateService } from '@ngx-translate/core';
import { AssessmentLocksComponent } from 'app/assessment/manage/assessment-locks/assessment-locks.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { MockHasAnyAuthorityDirective } from 'test/helpers/mocks/directive/mock-has-any-authority.directive';
import { ArtemisDatePipe } from 'app/foundation/pipes/artemis-date.pipe';
import { ModelingSubmission } from 'app/modeling/shared/entities/modeling-submission.model';
import { FileUploadSubmission } from 'app/fileupload/shared/entities/file-upload-submission.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { ProgrammingSubmission } from 'app/programming/shared/entities/programming-submission.model';
import { ModelingAssessmentService } from 'app/modeling/manage/assess/modeling-assessment.service';
import { TextAssessmentService } from 'app/text/manage/assess/service/text-assessment.service';
import { ProgrammingAssessmentManualResultService } from 'app/programming/manage/assess/manual-result/programming-assessment-manual-result.service';
import { FileUploadAssessmentService } from 'app/fileupload/manage/assess/file-upload-assessment.service';
import { Submission, SubmissionExerciseType } from 'app/exercise/shared/entities/submission/submission.model';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { AlertService } from 'app/foundation/service/alert.service';
import { CourseManagementService } from 'app/course/manage/services/course-management.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { ActivatedRoute, provideRouter } from '@angular/router';
import { assessmentLocksRoute } from './assessment-locks.route';
import { IS_AT_LEAST_INSTRUCTOR, IS_AT_LEAST_TUTOR } from 'app/foundation/constants/authority.constants';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { HttpResponse, provideHttpClient } from '@angular/common/http';

describe('AssessmentLocksComponent', () => {
    let component: AssessmentLocksComponent;
    let fixture: ComponentFixture<AssessmentLocksComponent>;
    let courseService: CourseManagementService;
    let modelingAssessmentService: ModelingAssessmentService;
    let textAssessmentService: TextAssessmentService;
    let programmingAssessmentService: ProgrammingAssessmentManualResultService;
    let fileUploadAssessmentService: FileUploadAssessmentService;

    const modelingSubmission = { id: 21, submissionExerciseType: SubmissionExerciseType.MODELING } as ModelingSubmission;
    const fileUploadSubmission = { id: 22, submissionExerciseType: SubmissionExerciseType.FILE_UPLOAD } as FileUploadSubmission;
    const textSubmission = { id: 23, participation: { exercise: { id: 1 }, id: 2 }, submissionExerciseType: SubmissionExerciseType.TEXT } as TextSubmission;
    const programmingSubmission = { id: 24, submissionExerciseType: SubmissionExerciseType.PROGRAMMING } as ProgrammingSubmission;
    let windowConfirmStub: MockInstance;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AssessmentLocksComponent, MockPipe(ArtemisTranslatePipe), MockRouterLinkDirective, MockHasAnyAuthorityDirective, MockPipe(ArtemisDatePipe)],
            providers: [
                provideRouter([]),
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(TextAssessmentService),
                MockProvider(CourseManagementService),
                MockProvider(ModelingAssessmentService),
                MockProvider(ProgrammingAssessmentManualResultService),
                MockProvider(FileUploadAssessmentService),
                MockProvider(ExamManagementService),
                {
                    provide: ActivatedRoute,
                    useValue: new MockActivatedRoute({ id: 123 }),
                },
                provideTranslateService(),
            ],
        })
            .compileComponents()
            .then(() => {
                programmingAssessmentService = TestBed.inject(ProgrammingAssessmentManualResultService);
                textAssessmentService = TestBed.inject(TextAssessmentService);
                fileUploadAssessmentService = TestBed.inject(FileUploadAssessmentService);
                modelingAssessmentService = TestBed.inject(ModelingAssessmentService);
                courseService = TestBed.inject(CourseManagementService);
            });
    });

    beforeEach(() => {
        fixture = TestBed.createComponent(AssessmentLocksComponent);
        component = fixture.componentInstance;
        // Mock findAllLockedSubmissionsOfCourse before detectChanges to prevent undefined subscribe error
        vi.spyOn(courseService, 'findAllLockedSubmissionsOfCourse').mockReturnValue(of(new HttpResponse<Submission[]>({ body: [] })));
        fixture.detectChanges();
        windowConfirmStub = vi.spyOn(window, 'confirm').mockReturnValue(true);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should call getAllLockedSubmissions on init', () => {
        // Note: findAllLockedSubmissionsOfCourse is already called once in beforeEach during detectChanges
        // We verify it was called by checking the spy set up in beforeEach
        expect(courseService.findAllLockedSubmissionsOfCourse).toHaveBeenCalled();
    });

    it.each([
        [ExerciseType.TEXT, undefined, undefined, 0, '/course-management/9437/text-exercises/818/submissions/1033/assessment?correction-round=0'],
        [ExerciseType.TEXT, 42, 51, 1, '/course-management/9437/exams/42/exercise-groups/51/text-exercises/818/submissions/1033/assessment?correction-round=1'],
        [ExerciseType.MODELING, 42, 52, 0, '/course-management/9437/exams/42/exercise-groups/52/modeling-exercises/818/submissions/1033/assessment?correction-round=0'],
        [ExerciseType.PROGRAMMING, 42, 53, 1, '/course-management/9437/exams/42/exercise-groups/53/programming-exercises/818/submissions/1033/assessment?correction-round=1'],
    ])('renders a resumable %s assessment link for exam %s and group %s', (type, examId, groupId, correctionRound, href) => {
        component.courseId.set(9437);
        component.examId = examId;
        component.submissions.set([
            {
                id: 1033,
                participation: { id: 1065, exercise: { id: 818, type, exerciseGroup: groupId ? { id: groupId } : undefined } },
                latestResult: { correctionRound },
            } as Submission,
        ]);
        fixture.detectChanges();

        expect(fixture.nativeElement.querySelector('tbody a').getAttribute('href')).toBe(href);
    });

    it('requires instructor access for exam locks while retaining tutor access for course locks', () => {
        expect(assessmentLocksRoute.find((route) => route.path === 'exams/:examId/assessment-locks')?.data?.authorities).toEqual(IS_AT_LEAST_INSTRUCTOR);
        expect(assessmentLocksRoute.find((route) => route.path === 'assessment-locks')?.data?.authorities).toEqual(IS_AT_LEAST_TUTOR);
    });

    it('should release lock for programming exercise', () => {
        const cancelAssessmentStub = vi.spyOn(programmingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(programmingSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });

    it('should release lock for modeling exercise', () => {
        const cancelAssessmentStub = vi.spyOn(modelingAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(modelingSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });

    it('should release lock for text exercise', () => {
        const cancelAssessmentStub = vi.spyOn(textAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(textSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });

    it('should release lock for the file upload exercise', () => {
        const cancelAssessmentStub = vi.spyOn(fileUploadAssessmentService, 'cancelAssessment').mockReturnValue(of());
        component.cancelAssessment(fileUploadSubmission);
        expect(windowConfirmStub).toHaveBeenCalledTimes(1);
        expect(cancelAssessmentStub).toHaveBeenCalledTimes(1);
    });
    it('should load the locked submissions of an exam and show all of them', () => {
        const examService = TestBed.inject(ExamManagementService);
        const examStub = vi
            .spyOn(examService, 'findAllLockedSubmissionsOfExam')
            .mockReturnValue(of(new HttpResponse<Submission[]>({ body: [modelingSubmission, fileUploadSubmission] })));
        component.courseId.set(9437);
        component.examId = 42;

        component.getAllLockedSubmissions();

        expect(examStub).toHaveBeenCalledWith(9437, 42);
        expect(component.showAll()).toBe(true);
        expect(component.submissions()).toEqual([modelingSubmission, fileUploadSubmission]);
    });

    it('should report a failed loading of the course locks via the alert service', () => {
        const alertService = TestBed.inject(AlertService);
        const errorSpy = vi.spyOn(alertService, 'error');
        vi.spyOn(courseService, 'findAllLockedSubmissionsOfCourse').mockReturnValue(throwError(() => 'loading failed'));

        component.getAllLockedSubmissions();

        expect(errorSpy).toHaveBeenCalledWith('loading failed');
    });

    it('should render the locked submissions inside the table layout and cancel only unfinished assessments', () => {
        component.submissions.set([
            {
                id: 1,
                participation: { id: 2, exercise: { id: 3, type: ExerciseType.TEXT, title: 'Open lock' } },
                latestResult: { correctionRound: 0 },
            } as Submission,
            {
                id: 4,
                participation: { id: 5, exercise: { id: 6, type: ExerciseType.TEXT, title: 'Finished' } },
                latestResult: { correctionRound: 0, completionDate: dayjs() },
            } as Submission,
        ]);
        fixture.detectChanges();

        const layout = fixture.nativeElement.querySelector('tumaet-ui-table-layout') as HTMLElement;
        const rows = layout.querySelectorAll('tbody tr');
        expect(rows).toHaveLength(2);
        expect(rows[0].querySelectorAll('button')).toHaveLength(1);
        expect(rows[1].querySelectorAll('button')).toHaveLength(0);
    });
});
