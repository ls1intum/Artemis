import { TestBed } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Course } from 'app/course/shared/entities/course.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamStudentDTO, ExamStudentSearch } from 'app/exam/manage/students/exam-student-dto.model';
import { UserForRegistration } from 'app/shared-ui/user-registration-modal/user-for-registration.model';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { EntitySummaryCategory } from 'app/shared-ui/delete-dialog/delete-dialog.model';
import dayjs from 'dayjs/esm';
import { HttpErrorResponse } from '@angular/common/http';
import { ExamMode } from 'app/exam/shared/entities/exam-mode.model';
import { ExamImportResultDTO } from 'app/exam/shared/entities/exam-import-result.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ExamExerciseStartPreparationStatus } from 'app/exam/manage/services/exam-exercise-start-preparation-status.model';
import { ExamInformationDTO } from 'app/exam/shared/entities/exam-information.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { TextSubmission } from 'app/text/shared/entities/text-submission.model';
import { Result } from 'app/exercise/shared/entities/result/result.model';
import { AccountService } from 'app/core/auth/account.service';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';

import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
describe('Exam Management Service Tests', () => {
    let service: ExamManagementService;
    let httpMock: HttpTestingController;

    const course = { id: 456 } as Course;
    const mockExamPopulated: Exam = {
        id: 1,
        startDate: undefined,
        endDate: undefined,
        visibleDate: undefined,
        publishResultsDate: undefined,
        examStudentReviewStart: undefined,
        examStudentReviewEnd: undefined,
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                ExamManagementService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: WebsocketService, useClass: MockWebsocketService },
            ],
        });

        service = TestBed.inject(ExamManagementService);
        httpMock = TestBed.inject(HttpTestingController);
    });

    afterEach(() => {
        httpMock.verify();
    });

    it('should group the deletion summary of an exam into categories', () => {
        let categories: EntitySummaryCategory[] | undefined;
        service.getDeletionSummary(1, 2).subscribe((value) => (categories = value));

        httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/1/exams/2/deletion-summary` }).flush({ numberOfExerciseGroups: 2, numberRegisteredStudents: 150 });

        expect(categories!.map((category) => category.titleKey)).toEqual([
            'artemisApp.examManagement.delete.summary.category.structure',
            'artemisApp.examManagement.delete.summary.category.students',
        ]);
        expect(categories![1].items).toEqual([
            { labelKey: 'artemisApp.examManagement.delete.summary.numberRegisteredStudents', value: 150, highlight: false, neverHighlight: true },
        ]);
    });

    it('should return no categories when the server sends no deletion summary', () => {
        let categories: EntitySummaryCategory[] | undefined;
        service.getDeletionSummary(1, 2).subscribe((value) => (categories = value));

        httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/1/exams/2/deletion-summary` }).flush(null);

        expect(categories).toEqual([]);
    });

    const completeExam = (): Exam => ({
        id: 1,
        title: 'Exam',
        examMode: ExamMode.REAL,
        visibleDate: dayjs('2026-03-01T10:00:00.000Z'),
        startDate: dayjs('2026-03-02T10:00:00.000Z'),
        endDate: dayjs('2026-03-03T10:00:00.000Z'),
    });
    const completeBody = () => ({
        title: 'Exam',
        examMode: 'REAL',
        examWithAttendanceCheck: false,
        visibleDate: '2026-03-01T10:00:00.000Z',
        startDate: '2026-03-02T10:00:00.000Z',
        endDate: '2026-03-03T10:00:00.000Z',
        workingTime: 0,
    });

    it('should create an exam', () => {
        let created: Exam | undefined;
        service.create(course.id!, completeExam()).subscribe((res) => (created = res));

        const req = httpMock.expectOne({ method: 'POST', url: `/api/exam/courses/${course.id!}/exams` });
        expect(JSON.parse(req.request.serializeBody() as string)).toEqual({ id: 1, ...completeBody() });

        req.flush({ id: 1, title: 'Exam' });
        expect(created).toEqual({ id: 1, title: 'Exam' });
    });

    it('should update an exam', () => {
        let updated: Exam | undefined;
        service.update(course.id!, completeExam()).subscribe((res) => (updated = res));

        const req = httpMock.expectOne({ method: 'PUT', url: `/api/exam/courses/${course.id!}/exams` });
        expect(JSON.parse(req.request.serializeBody() as string)).toEqual({ id: 1, ...completeBody() });

        req.flush({ id: 1, title: 'Exam' });
        expect(updated).toEqual({ id: 1, title: 'Exam' });
    });

    it.each([
        ['title', (exam: Exam) => (exam.title = undefined)],
        ['visible date', (exam: Exam) => (exam.visibleDate = undefined)],
        ['start date', (exam: Exam) => (exam.startDate = undefined)],
        ['end date', (exam: Exam) => (exam.endDate = undefined)],
    ])('should fail a save without the %s as the server would, without a request', (_name, clear) => {
        const exam = completeExam();
        clear(exam);
        const errors: HttpErrorResponse[] = [];
        service.create(course.id!, exam).subscribe({ error: (error: HttpErrorResponse) => errors.push(error) });
        service.update(course.id!, exam).subscribe({ error: (error: HttpErrorResponse) => errors.push(error) });
        service.import(course.id!, exam, 'import-0').subscribe({ error: (error: HttpErrorResponse) => errors.push(error) });

        expect(errors.map((error) => error.status)).toEqual([400, 400, 400]);
        httpMock.expectNone(() => true);
    });

    it('should import an exam', () => {
        const importId = 'import-1';

        // the response carries the full ExamImportResultDTO so the progress dialog can show the outcome
        let result: ExamImportResultDTO | undefined;
        service.import(course.id!, completeExam(), importId).subscribe((res) => (result = res));

        // the importId is a query param, so match on the path and assert the param separately
        const req = httpMock.expectOne((request) => request.method === 'POST' && request.url.split('?')[0] === `/api/exam/courses/${course.id!}/exam-import`);
        expect(JSON.parse(req.request.serializeBody() as string)).toEqual({ ...completeBody(), courseId: course.id });
        expect(req.request.urlWithParams).toContain('importId=' + importId);

        req.flush({ exam: { id: 9, title: 'Imported' }, skippedExercises: ['Text'] });
        expect(result).toEqual({ exam: { id: 9, title: 'Imported' }, skippedExercises: ['Text'] });
    });

    it('should fail an import of an exercise without id or type as the server would, without a request', () => {
        const exam = completeExam();
        const group = new ExerciseGroup();
        group.exercises = [{ title: 'No id' } as Exercise];
        exam.exerciseGroups = [group];
        const statuses: number[] = [];
        service.import(course.id!, exam, 'import-0').subscribe({ error: (error: HttpErrorResponse) => statuses.push(error.status) });
        service.importExerciseGroup(course.id!, 1, [group], 'import-0').subscribe({ error: (error: HttpErrorResponse) => statuses.push(error.status) });

        expect(statuses).toEqual([400, 400]);
        httpMock.expectNone(() => true);
    });

    it('should import an exercise group', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExerciseGroup = [
            { id: 2, title: 'Group', isMandatory: false, exercises: [{ id: 3, type: ExerciseType.TEXT, title: 'Text', maxPoints: 5, bonusPoints: 1 }] } as ExerciseGroup,
        ];
        const importId = 'import-2';

        // WHEN: the response carries the full ExerciseGroupImportResultDTO
        service.importExerciseGroup(course.id!, mockExam.id!, mockExerciseGroup, importId).subscribe((res) => expect(res).toEqual({ exerciseGroups: mockExerciseGroup }));

        // THEN (the importId is a query param, so match on the path and assert the param separately)
        const req = httpMock.expectOne(
            (request) => request.method === 'POST' && request.url.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${mockExam.id!}/import-exercise-group`,
        );
        // The body is the slim import DTO shape (source id + type + overrides), not the loaded exercise group
        expect(req.request.body).toEqual([
            { title: 'Group', isMandatory: false, exercises: [{ id: 3, exerciseType: ExerciseType.TEXT, title: 'Text', maxPoints: 5, bonusPoints: 1 }] },
        ]);
        expect(req.request.urlWithParams).toContain('importId=' + importId);

        // CLEANUP
        req.flush({ exerciseGroups: mockExerciseGroup });
        await Promise.resolve();
    });

    it('should subscribe to import progress on the user-specific websocket channel', () => {
        // GIVEN
        const websocketService = TestBed.inject(WebsocketService);
        const subscribeSpy = vi.spyOn(websocketService, 'subscribe');
        const importId = 'import-3';

        // WHEN
        service.subscribeToImportProgress(importId);

        // THEN
        expect(subscribeSpy).toHaveBeenCalledWith(`/user/topic/exam-import/${importId}`);
    });

    it('should find an exam with exercises and without course id', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1, exerciseGroups: [{ id: 2 } as ExerciseGroup] };
        const expected: Exam = { id: 1, exerciseGroups: [{ id: 2 } as ExerciseGroup] };
        // WHEN
        service.findWithExercisesAndWithoutCourseId(mockExam.id!).subscribe((res) => expect(res).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/exams/${mockExam.id}`,
        });
        expect(req.request.url).toBe(`/api/exam/exams/${mockExam.id}`);

        // CLEANUP
        req.flush(expected);
        await Promise.resolve();
    });

    it('should find an exam without exercise groups', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const expected: Exam = { id: 1 };
        // WHEN
        service.find(course.id!, mockExam.id!).subscribe((res) => expect(res).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/courses/${course.id!}/exams/${mockExam.id}?withExerciseGroups=false`,
        });

        // CLEANUP
        req.flush(expected);
        await Promise.resolve();
    });

    it('should convert every date of a found exam to dayjs', async () => {
        const dateFields = [
            'visibleDate',
            'startDate',
            'endDate',
            'publishResultsDate',
            'examStudentReviewStart',
            'examStudentReviewEnd',
            'exampleSolutionPublicationDate',
            'examSummaryPublicationDate',
        ] as const;
        const serverExam = { id: 1, ...Object.fromEntries(dateFields.map((field) => [field, '2026-10-28T08:00:00Z'])) };
        let found: Exam | undefined;

        service.find(course.id!, 1).subscribe((res) => (found = res));

        httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/${course.id!}/exams/1?withExerciseGroups=false` }).flush(serverExam);
        await Promise.resolve();

        // The date picker of the exam form calls dayjs methods on these, so a plain ISO string breaks the form.
        dateFields.forEach((field) => expect(dayjs.isDayjs(found![field]), field).toBe(true));
    });

    it('should find all exams for course', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllExamsForCourse(course.id!).subscribe((res) => expect(res).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/${course.id!}/exams` });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('find all exams for which the instructors have access', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllExamsAccessibleToUser(course.id!).subscribe((res) => expect(res).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `/api/exam/courses/${course.id}/exams-for-user` });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should find all current and upcoming exams', async () => {
        // GIVEN
        const mockExamResponse = [{ ...mockExamPopulated }];

        // WHEN
        service.findAllCurrentAndUpcomingExams().subscribe((res) => expect(res).toEqual([mockExamPopulated]));

        // THEN
        const req = httpMock.expectOne({ method: 'GET', url: `/api/exam/admin/courses/upcoming-exams` });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=false', async () => {
        // GIVEN
        const mockExamResponse = { ...mockExamPopulated };

        // WHEN
        service.getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExamPopulated.id!, false).subscribe((res) => expect(res).toEqual(mockExamPopulated));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/courses/${course.id!}/exams/${mockExamPopulated.id}/exam-for-assessment-dashboard`,
        });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should getExamWithInterestingExercisesForAssessmentDashboard with isTestRun=true', async () => {
        // GIVEN
        const mockExamResponse = { ...mockExamPopulated };

        // WHEN
        service.getExamWithInterestingExercisesForAssessmentDashboard(course.id!, mockExamPopulated.id!, true).subscribe((res) => expect(res).toEqual(mockExamPopulated));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/courses/${course.id!}/exams/${mockExamPopulated.id}/exam-for-test-run-assessment-dashboard`,
        });
        req.flush(mockExamResponse);
        await Promise.resolve();
    });

    it('should get latest individual end date of exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse: ExamInformationDTO = { latestIndividualEndDate: dayjs() };
        const expected = { ...mockResponse };

        // WHEN
        service.getLatestIndividualEndDateOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/courses/${course.id!}/exams/${mockExam.id!}/latest-end-date`,
        });
        req.flush(mockResponse);
        await Promise.resolve();
    });

    it('should update order', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockExerciseGroups: ExerciseGroup[] = [
            { exam: mockExam, id: 2 },
            { exam: mockExam, id: 1 },
        ];

        // WHEN
        service.updateOrder(course.id!, mockExam.id!, mockExerciseGroups).subscribe();

        // THEN
        const req = httpMock.expectOne({
            method: 'PUT',
            url: `/api/exam/courses/${course.id}/exams/${mockExam.id}/exercise-groups-order`,
        });
        // Only the ordered group ids are sent, not the full exercise groups; the response carries no body.
        expect(req.request.body).toEqual([2, 1]);
        req.flush(null);
        await Promise.resolve();
    });

    it('should find all locked submissions from exam', async () => {
        // GIVEN
        const mockExam: Exam = { id: 1 };
        const mockResponse = [new TextSubmission()];
        const expected = [new TextSubmission()];

        // WHEN
        service.findAllLockedSubmissionsOfExam(course.id!, mockExam.id!).subscribe((res) => expect(res).toEqual(expected));

        // THEN
        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/courses/${course.id!}/exams/${mockExam.id!}/locked-submissions`,
        });
        req.flush(mockResponse);
        await Promise.resolve();
    });

    it('should reconnect the last result of a locked submission as latestResult', async () => {
        // GIVEN: the server's wire shape for a locked submission carries its result(s) as a plain list (the DTO the
        // assessment-locks table reads); the client is expected to derive latestResult from it via reconnectSubmissions.
        const mockExam: Exam = { id: 1 };
        const lockedResult = { id: 99, score: 42, completionDate: undefined } as Result;
        const mockResponse = [{ id: 7, submissionExerciseType: 'text', results: [lockedResult] } as TextSubmission];

        // WHEN
        let received: TextSubmission[] | undefined;
        service.findAllLockedSubmissionsOfExam(course.id!, mockExam.id!).subscribe((res) => (received = res as TextSubmission[]));

        const req = httpMock.expectOne({
            method: 'GET',
            url: `/api/exam/courses/${course.id!}/exams/${mockExam.id!}/locked-submissions`,
        });
        req.flush(mockResponse);
        await Promise.resolve();

        // THEN: reconnectSubmissions must have turned the last (and only) result into latestResult, with the
        // back-reference to its submission restored, exactly as the assessment-locks component relies on.
        expect(received).toBeDefined();
        expect(received![0].latestResult?.id).toBe(lockedResult.id);
        expect(received![0].latestResult?.score).toBe(lockedResult.score);
        expect(received![0].latestResult?.submission).toBe(received![0]);
    });

    it('should download the exam from archive', async () => {
        const mockExam: Exam = { id: 1 };

        const windowSpy = vi.spyOn(window, 'open').mockImplementation(() => null);
        service.downloadExamArchive(course.id!, mockExam.id!);
        expect(windowSpy).toHaveBeenCalledWith('api/exam/courses/456/exams/1/download-archive', '_blank');
    });

    it('should reset an exam', async () => {
        const accountService = TestBed.inject(AccountService);
        const accountServiceSpy = vi.spyOn(accountService, 'setAccessRightsForCourse').mockImplementation(() => undefined);

        // GIVEN
        const mockExam: Exam = { id: 1, course };

        // WHEN
        service.reset(course.id!, mockExam.id!).subscribe((res) => expect(res).toEqual(mockExam));

        // THEN
        const req = httpMock.expectOne({
            method: 'DELETE',
            url: `/api/exam/courses/${course.id!}/exams/${mockExam.id!}/reset`,
        });

        req.flush(mockExam);
        await Promise.resolve();

        expect(accountServiceSpy).toHaveBeenCalledOnce();
        expect(accountServiceSpy).toHaveBeenCalledWith(course);
    });

    const queryOf = (request: { urlWithParams: string }) => new URL(request.urlWithParams, 'http://localhost').searchParams;
    describe('findExamStudentsPaged', () => {
        const examId = 1;
        const baseSearch: ExamStudentSearch = {
            page: 0,
            pageSize: 20,
            sortingOrder: SortingOrder.ASCENDING,
            sortedColumn: 'name',
            searchTerm: '',
        };

        it('should send GET request with correct URL and params', async () => {
            // GIVEN
            const mockBody: ExamStudentDTO[] = [{ id: 10, login: 'student1' }];

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                expect(result.content).toHaveLength(1);
                expect(result.content[0].login).toBe('student1');
                expect(result.totalElements).toBe(1);
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/exam-students/paged`);
            expect(queryOf(req.request).get('page')).toBe('0');
            expect(queryOf(req.request).get('pageSize')).toBe('20');
            expect(queryOf(req.request).get('sortingOrder')).toBe(SortingOrder.ASCENDING);
            expect(queryOf(req.request).get('sortedColumn')).toBe('name');
            expect(queryOf(req.request).get('searchTerm')).toBe('');
            expect(queryOf(req.request).has('filterProp')).toBe(false);

            // CLEANUP
            req.flush(mockBody, { headers: { 'X-Total-Count': '1' } });
        });

        it('should include filterProp in params when provided', async () => {
            // GIVEN
            const searchWithFilter: ExamStudentSearch = { ...baseSearch, filterProp: 'Submitted' };

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, searchWithFilter).subscribe();

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/exam-students/paged`);
            expect(queryOf(req.request).get('filterProp')).toBe('Submitted');

            // CLEANUP
            req.flush([]);
        });

        it('should read totalElements from X-Total-Count header', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/exam-students/paged`);

            // CLEANUP
            req.flush([], { headers: { 'X-Total-Count': '42' } });

            expect(capturedTotal).toBe(42);
        });

        it('should convert startedDate and submissionDate from server format', async () => {
            // GIVEN
            const isoDate = '2024-06-15T10:00:00Z';
            const mockBody: ExamStudentDTO[] = [{ id: 10, startedDate: isoDate as any, submissionDate: isoDate as any }];
            let capturedRow: ExamStudentDTO | undefined;

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                capturedRow = result.content[0];
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/exam-students/paged`);

            // CLEANUP
            req.flush(mockBody, { headers: { 'X-Total-Count': '1' } });

            expect(capturedRow?.startedDate).toBeDefined();
            expect(capturedRow?.submissionDate).toBeDefined();
            // convertDateFromServer wraps them in dayjs — verify they are dayjs objects
            expect(typeof capturedRow?.startedDate?.isValid).toBe('function');
            expect(typeof capturedRow?.submissionDate?.isValid).toBe('function');
        });

        it('should default totalElements to 0 when X-Total-Count header is absent', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.findExamStudentsPaged(course.id!, examId, baseSearch).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/exam-students/paged`);

            // CLEANUP
            req.flush([]);

            expect(capturedTotal).toBe(0);
        });
    });

    describe('searchUsersForExamRegistration', () => {
        const examId = 1;

        it('should send GET request with correct URL and params', async () => {
            // GIVEN
            const mockUsers: UserForRegistration[] = [{ id: 1, login: 'alice', name: 'Alice', isRegistered: false }];

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                expect(result.content).toHaveLength(1);
                expect(result.content[0].login).toBe('alice');
                expect(result.totalElements).toBe(1);
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/students/search`);
            expect(queryOf(req.request).get('searchTerm')).toBe('alice');
            expect(queryOf(req.request).get('page')).toBe('0');
            expect(queryOf(req.request).get('size')).toBe('10');

            // CLEANUP
            req.flush(mockUsers, { headers: { 'X-Total-Count': '1' } });
        });

        it('should read totalElements from X-Total-Count header', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/students/search`);

            // CLEANUP
            req.flush([], { headers: { 'X-Total-Count': '99' } });

            expect(capturedTotal).toBe(99);
        });

        it('should default totalElements to 0 when X-Total-Count header is absent', async () => {
            // GIVEN
            let capturedTotal: number | undefined;

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                capturedTotal = result.totalElements;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/students/search`);

            // CLEANUP
            req.flush([]);

            expect(capturedTotal).toBe(0);
        });

        it('should return empty content array when response body is null', async () => {
            // GIVEN
            let capturedContent: UserForRegistration[] | undefined;

            // WHEN
            service.searchUsersForExamRegistration(course.id!, examId, 'alice', 0, 10).subscribe((result) => {
                capturedContent = result.content;
            });

            // THEN
            const req = httpMock.expectOne((r) => r.method === 'GET' && r.urlWithParams.split('?')[0] === `/api/exam/courses/${course.id!}/exams/${examId}/students/search`);

            // CLEANUP
            req.flush(null, { headers: { 'X-Total-Count': '0' } });

            expect(capturedContent).toEqual([]);
        });
    });

    it('loads announcement history and converts every timestamp', () => {
        service.getAnnouncements(1, 2).subscribe((events) => {
            expect(events).toHaveLength(1);
            expect(events[0].createdDate.toISOString()).toBe('2026-10-25T08:00:00.000Z');
        });
        httpMock.expectOne({ method: 'GET', url: '/api/exam/courses/1/exams/2/announcements' }).flush([{ id: 3, createdDate: '2026-10-25T08:00:00Z' }]);
    });

    it('creates an announcement and converts its publication date', () => {
        service.createAnnouncement(1, 2, 'Exam starts soon').subscribe((event) => {
            expect(dayjs.isDayjs(event.createdDate)).toBe(true);
            expect(event.createdDate.toISOString()).toBe('2026-10-25T08:00:00.000Z');
        });
        const req = httpMock.expectOne({ method: 'POST', url: '/api/exam/courses/1/exams/2/announcements' });
        expect(req.request.body).toBe('Exam starts soon');
        req.flush({ id: 3, createdDate: '2026-10-25T08:00:00Z' });
    });

    it('should update the working time with a number body and convert the returned exam', () => {
        let result: Exam | undefined;
        service.updateWorkingTime(1, 2, -300).subscribe((exam) => (result = exam));

        const req = httpMock.expectOne({ method: 'PATCH', url: '/api/exam/courses/1/exams/2/working-time' });
        expect(req.request.body).toBe(-300);
        req.flush({ id: 2, title: 'Exam', startDate: '2026-10-25T08:00:00Z', course: { id: 1, title: 'Course' } });

        expect(result?.id).toBe(2);
        expect(dayjs.isDayjs(result?.startDate)).toBe(true);
        expect(result?.course?.id).toBe(1);
    });

    it('should keep fields the server omitted undefined instead of applying exam defaults', () => {
        let result: Exam | undefined;
        service.find(1, 2).subscribe((exam) => (result = exam));

        httpMock.expectOne({ method: 'GET', url: '/api/exam/courses/1/exams/2?withExerciseGroups=false' }).flush({ id: 2 });

        expect(result?.examMode).toBeUndefined();
        expect(result?.workingTime).toBeUndefined();
        expect(result?.examMaxPoints).toBeUndefined();
        expect(result?.numberOfCorrectionRoundsInExam).toBeUndefined();
    });

    it('should convert the start date of the exercise start status', () => {
        let status: ExamExerciseStartPreparationStatus | null | undefined;
        service.getExerciseStartStatus(1, 2).subscribe((value) => (status = value));

        httpMock
            .expectOne({ method: 'GET', url: '/api/exam/courses/1/exams/2/student-exams/start-exercises/status' })
            .flush({ finished: 1, failed: 0, overall: 3, startedAt: '2026-10-25T08:00:00Z' });

        expect(status?.finished).toBe(1);
        expect(status?.startedAt?.toISOString()).toBe('2026-10-25T08:00:00.000Z');
    });

    it('should return null when the server sends no exercise start status', () => {
        let status: ExamExerciseStartPreparationStatus | null | undefined;
        service.getExerciseStartStatus(1, 2).subscribe((value) => (status = value));

        httpMock.expectOne({ method: 'GET', url: '/api/exam/courses/1/exams/2/student-exams/start-exercises/status' }).flush(null);

        expect(status).toBeNull();
    });
});
