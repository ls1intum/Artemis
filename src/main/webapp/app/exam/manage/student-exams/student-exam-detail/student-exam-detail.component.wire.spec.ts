import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute } from '@angular/router';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, TestRequest, provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { StudentExamDetailComponent } from 'app/exam/manage/student-exams/student-exam-detail/student-exam-detail.component';
import { StudentExamWithGradeDTO } from 'app/exam/manage/exam-scores/exam-score-dtos.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { GradeType } from 'app/assessment/shared/entities/grading-scale.model';
import { AccountService } from 'app/core/auth/account.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { cloneWith, deepClone } from 'app/foundation/util/deep-clone.util';

/**
 * Wire contract of the student exam detail writes: the same UI state must send the same request, and the server JSON
 * of StudentExamDTO must change the loaded student exam the way the hand-written client changed it.
 */
describe('StudentExamDetailComponent wire contract', () => {
    let fixture: ComponentFixture<StudentExamDetailComponent>;
    let component: StudentExamDetailComponent;
    let httpMock: HttpTestingController;
    let accountService: AccountService;
    let alertService: AlertService;

    const url = (suffix: string) => 'api/exam/courses/1/exams/5/student-exams/9/' + suffix;
    const expectRequest = (suffix: string): TestRequest => httpMock.expectOne((req) => req.url.replace(/^\//, '') === url(suffix));

    // StudentExamDTO.of: no nested user and exam, as the toggle endpoints send it
    const toggledDto = {
        id: 9,
        workingTime: 7200,
        started: true,
        startedDate: '2026-03-04T10:00:00.000Z',
        submitted: true,
        submissionDate: '2026-03-04T11:30:15.250Z',
        testRun: false,
    };
    // StudentExamDTO.withExam: nested exam with ExamForStudentExamDTO and CourseForStudentExamDTO, as the working-time endpoint sends it
    const workingTimeDto = {
        id: 9,
        workingTime: 5400,
        started: true,
        startedDate: '2026-03-04T10:00:00.000Z',
        submitted: true,
        submissionDate: '2026-03-04T11:00:00.000Z',
        testRun: false,
        exam: {
            id: 5,
            title: 'Exam',
            examMode: 'REAL',
            workingTime: 7200,
            course: { id: 1, accuracyOfScores: 2, maxComplaintTextLimit: 2000, maxComplaintResponseTextLimit: 2000, athenaFormativeFeedbackEnabled: true },
        },
    };

    const loadedStudentExam = (): StudentExam => ({
        id: 9,
        workingTime: 7200,
        started: true,
        exam: { id: 5, course: { id: 1 }, startDate: dayjs('2026-03-04T10:00:00.000Z'), endDate: dayjs('2026-03-04T12:00:00.000Z'), gracePeriod: 180 },
        user: { id: 7, login: 'student', name: 'Student' },
        exercises: [],
        numberOfExamSessions: 2,
    });

    const create = (studentExam: StudentExam): void => {
        const withGrade = {
            studentExam,
            maxPoints: 100,
            gradeType: GradeType.NONE,
            studentResult: { overallPointsAchieved: 0, overallScoreAchieved: 0 },
        } as StudentExamWithGradeDTO;
        const route = TestBed.inject(ActivatedRoute) as unknown as Record<string, unknown>;
        route['data'] = of({ studentExam: withGrade });
        route['params'] = of({ courseId: 1, examId: 5 });
        route['url'] = of([]);
        fixture = TestBed.createComponent(StudentExamDetailComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                MockProvider(AccountService),
                MockProvider(AlertService),
                { provide: ActivatedRoute, useValue: {} },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        httpMock = TestBed.inject(HttpTestingController);
        accountService = TestBed.inject(AccountService);
        alertService = TestBed.inject(AlertService);
        vi.spyOn(alertService, 'success');
        vi.spyOn(alertService, 'error');
        vi.spyOn(accountService, 'setAccessRightsForCourse');
    });

    afterEach(() => {
        httpMock.verify();
    });

    describe('toggle', () => {
        it('should PUT an empty body to toggle-to-submitted for a student exam that is not submitted and take over the response', () => {
            create(loadedStudentExam());
            const before = deepClone(component.studentExam()!);

            component.toggle();

            const req = expectRequest('toggle-to-submitted');
            expect(req.request.method).toBe('PUT');
            expect(req.request.params.keys()).toEqual([]);
            // the old client sent {}, the generated method sends no body; the server reads none of it
            expect(Object.keys(req.request.body ?? {})).toEqual([]);
            expect(component.isSaving()).toBe(true);
            req.flush(toggledDto);

            // the old client copied the submission date as the server sent it
            expect(component.studentExam()).toEqual(cloneWith(before, { submitted: true, submissionDate: toggledDto.submissionDate }));
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.studentExamDetail.toggleSuccessful');
            expect(component.isSaving()).toBe(false);
        });

        it('should PUT an empty body to toggle-to-unsubmitted for a submitted student exam and clear the submission date', () => {
            const submitted = loadedStudentExam();
            submitted.submitted = true;
            submitted.submissionDate = dayjs('2026-03-04T11:30:15.250Z');
            create(submitted);
            const before = deepClone(component.studentExam()!);

            component.toggle();

            const req = expectRequest('toggle-to-unsubmitted');
            expect(req.request.method).toBe('PUT');
            expect(req.request.params.keys()).toEqual([]);
            // the old client sent {}, the generated method sends no body; the server reads none of it
            expect(Object.keys(req.request.body ?? {})).toEqual([]);
            // StudentExamDTO.of with submitted = false: NON_EMPTY leaves the submission date out
            req.flush({ id: 9, workingTime: 7200, started: true, startedDate: '2026-03-04T10:00:00.000Z', submitted: false, testRun: false });

            expect(component.studentExam()).toEqual(cloneWith(before, { submitted: false, submissionDate: undefined }));
            expect(component.isSaving()).toBe(false);
        });

        it('should alert and leave the student exam unchanged when the request fails', () => {
            create(loadedStudentExam());
            const before = deepClone(component.studentExam()!);

            component.toggle();
            expectRequest('toggle-to-submitted').flush(null, { status: 500, statusText: 'Server Error' });

            expect(component.studentExam()).toEqual(before);
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.studentExamDetail.toggleFailed');
            expect(component.isSaving()).toBe(false);
        });
    });

    describe('updateWorkingTime', () => {
        it('should PATCH the working time in seconds as the bare body and merge the response into the loaded student exam', () => {
            create(loadedStudentExam());
            const before = deepClone(component.studentExam()!);
            component.workingTimeSeconds.set(5400);

            component.saveWorkingTime();

            const req = expectRequest('working-time');
            expect(req.request.method).toBe('PATCH');
            expect(req.request.params.keys()).toEqual([]);
            expect(req.request.body).toBe(5400);
            expect(component.isSavingWorkingTime()).toBe(true);
            req.flush(workingTimeDto);

            expect(component.studentExam()).toEqual(cloneWith(before, { workingTime: 5400, submissionDate: dayjs(workingTimeDto.submissionDate) }));
            expect(component.workingTimeSeconds()).toBe(5400);
            // the nested course of the response still reaches the access rights
            expect(accountService.setAccessRightsForCourse).toHaveBeenCalledWith(workingTimeDto.exam.course);
            expect(alertService.success).toHaveBeenCalledWith('artemisApp.studentExamDetail.saveWorkingTimeSuccessful');
            expect(component.isSavingWorkingTime()).toBe(false);
        });

        it('should alert and keep the student exam when the request fails', () => {
            create(loadedStudentExam());
            const before = deepClone(component.studentExam()!);

            component.saveWorkingTime();
            expectRequest('working-time').flush(null, { status: 400, statusText: 'Bad Request' });

            expect(component.studentExam()).toEqual(before);
            expect(alertService.error).toHaveBeenCalledWith('artemisApp.studentExamDetail.workingTimeCouldNotBeSaved');
            expect(component.isSavingWorkingTime()).toBe(false);
        });
    });
});
