import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router, UrlSegment, convertToParamMap, provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { Course } from 'app/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamStudentsComponent } from 'app/exam/manage/students/exam-students.component';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { MockPipe, MockProvider } from 'ng-mocks';
import { Observable, of, throwError } from 'rxjs';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';
import { MockDialogService } from 'test/helpers/mocks/service/mock-dialog.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { ExamChecklistService } from 'app/exam/manage/exams/exam-checklist-component/exam-checklist.service';
import { ExamChecklist } from 'app/exam/shared/entities/exam-checklist.model';
import { ExamStudentDTO } from 'app/exam/manage/students/exam-student-dto.model';
import { TableLazyLoadEvent } from 'primeng/table';
import { ConfirmationService } from 'primeng/api';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

describe('ExamStudentsComponent', () => {
    setupTestBed({ zoneless: true });

    const course = { id: 1 } as Course;

    const examWithCourse: Exam = {
        course,
        id: 2,
        title: 'Test Exam',
        examUsers: [{ id: 10 } as any, { id: 11 } as any],
    } as Exam;

    const route = {
        snapshot: { paramMap: convertToParamMap({ courseId: course.id }) },
        url: new Observable<UrlSegment[]>(),
        data: of({ exam: examWithCourse }),
    } as any as ActivatedRoute;

    const mockChecklist: ExamChecklist = {
        numberOfExamsSubmitted: 0,
        numberOfExamsStarted: 0,
        numberOfTotalParticipationsForAssessment: 0,
        existsUnassessedQuizzes: false,
        existsUnsubmittedExercises: false,
        allExamExercisesAllStudentsPrepared: true,
        numberOfGeneratedStudentExams: 2,
    };

    const mockDto: ExamStudentDTO = {
        id: 10,
        userId: 1,
        login: 'student1',
        name: 'Student One',
        studentExamId: 123,
        workingTime: 3600,
        started: true,
        submitted: false,
        progress: 'started',
        numberOfExamSessions: 2,
    };

    const mockLazyEvent: TableLazyLoadEvent = { first: 0, rows: 20 };

    let component: ExamStudentsComponent;
    let fixture: ComponentFixture<ExamStudentsComponent>;
    let examManagementService: ExamManagementService;
    let router: Router;
    let alertService: AlertService;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ExamStudentsComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                provideRouter([]),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: AccountService, useClass: MockAccountService },
                { provide: DeleteDialogService, useClass: MockDialogService },
                { provide: WebsocketService, useClass: MockWebsocketService },
                MockProvider(ExamChecklistService, {
                    getExamStatistics: (_exam: Exam) => of(mockChecklist),
                }),
                MockProvider(AlertService, {
                    success: vi.fn(),
                    error: vi.fn(),
                }),
                provideHttpClientTesting(),
            ],
        })
            .overrideTemplate(ExamStudentsComponent, '')
            .compileComponents();

        fixture = TestBed.createComponent(ExamStudentsComponent);
        component = fixture.componentInstance;
        examManagementService = TestBed.inject(ExamManagementService);

        vi.spyOn(examManagementService, 'getExerciseStartStatus').mockReturnValue(of(new HttpResponse({ body: null as any })));
        vi.spyOn(examManagementService, 'findExamStudentsPaged').mockReturnValue(of({ content: [mockDto], totalElements: 1 }));
        vi.spyOn(examManagementService, 'find').mockReturnValue(of(new HttpResponse({ body: examWithCourse })));

        router = TestBed.inject(Router);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
        fixture?.destroy();
    });

    it('should initialize with courseId and exam', () => {
        fixture.detectChanges();

        expect(component.courseId()).toBe(course.id);
        expect(component.exam()).toEqual(examWithCourse);
    });

    it('should set studentExamCount and isAllExercisesPrepared from checklist', () => {
        fixture.detectChanges();

        expect(component.studentExamCount()).toBe(mockChecklist.numberOfGeneratedStudentExams);
        expect(component.isAllExercisesPrepared()).toBe(true);
    });

    describe('computed properties', () => {
        it('hasRegisteredUsers should be true when totalExamStudents > 0', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(2);
            expect(component.hasRegisteredUsers()).toBe(true);
        });

        it('hasRegisteredUsers should be false when totalExamStudents is 0', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(0);
            expect(component.hasRegisteredUsers()).toBe(false);
        });

        it('isMissingIndividualExams should be false when studentExamCount equals totalExamStudents', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(2);
            component.studentExamCount.set(2);
            expect(component.isMissingIndividualExams()).toBe(false);
        });

        it('isMissingIndividualExams should be true when studentExamCount < totalExamStudents', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(2);
            component.studentExamCount.set(1);
            expect(component.isMissingIndividualExams()).toBe(true);
        });

        it('isMissingIndividualExams should be false when totalExamStudents is 0', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(0);
            component.studentExamCount.set(0);
            expect(component.isMissingIndividualExams()).toBe(false);
        });

        it('examPreparationsComplete should be true when no missing exams and all exercises prepared', () => {
            fixture.detectChanges();
            expect(component.examPreparationsComplete()).toBe(true);
        });

        it('examPreparationsComplete should be false when individual exams are missing', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(2);
            component.studentExamCount.set(1);
            expect(component.examPreparationsComplete()).toBe(false);
        });

        it('isTestExam should be false for a regular exam', () => {
            fixture.detectChanges();
            expect(component.isTestExam()).toBe(false);
        });

        it('isTestExam should be true when exam.testExam is set', () => {
            fixture.detectChanges();
            component.exam.set({ ...examWithCourse, testExam: true });
            expect(component.isTestExam()).toBe(true);
        });

        it('examStudentFilterGroups should contain only the progress group while exam is running', () => {
            fixture.detectChanges();
            component.hasExamEnded.set(false);

            const groups = component.examStudentFilterGroups();

            expect(groups).toHaveLength(1);
            expect(groups[0].labelKey).toContain('progress');
        });

        it('examStudentFilterGroups should include the attendance group after exam has ended', () => {
            fixture.detectChanges();
            component.hasExamEnded.set(true);

            const groups = component.examStudentFilterGroups();

            expect(groups).toHaveLength(2);
            expect(groups[1].labelKey).toContain('attendance');
        });
    });

    describe('loadExamStudents', () => {
        it('should update rows, totalRows, and totalExamStudents on a successful unfiltered load', () => {
            fixture.detectChanges();
            vi.spyOn(examManagementService, 'findExamStudentsPaged').mockReturnValue(of({ content: [mockDto], totalElements: 42 }));

            component.loadExamStudents(mockLazyEvent);

            expect(component.rows()).toEqual([mockDto]);
            expect(component.totalRows()).toBe(42);
            expect(component.totalExamStudents()).toBe(42);
            expect(component.isLoading()).toBe(false);
        });

        it('should NOT update totalExamStudents when a filter is active', () => {
            fixture.detectChanges();
            component.totalExamStudents.set(10);
            component.activeFilter.set('Submitted');
            vi.spyOn(examManagementService, 'findExamStudentsPaged').mockReturnValue(of({ content: [], totalElements: 3 }));

            component.loadExamStudents(mockLazyEvent);

            // totalExamStudents unchanged because filterProp is set
            expect(component.totalExamStudents()).toBe(10);
            expect(component.totalRows()).toBe(3);
        });

        it('should reset isLoading and show an error alert when the request fails', () => {
            fixture.detectChanges();
            const errorSpy = vi.spyOn(alertService, 'error');
            vi.spyOn(examManagementService, 'findExamStudentsPaged').mockReturnValue(
                // status 400 triggers alertService.error in onError (500 is intentionally silenced)
                throwError(() => new HttpErrorResponse({ status: 400 })),
            );

            component.loadExamStudents(mockLazyEvent);

            expect(component.isLoading()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('error.http.400');
        });

        it('should not call findExamStudentsPaged when exam has no id', () => {
            fixture.detectChanges();
            component.exam.set({ ...examWithCourse, id: undefined });
            const findPagedSpy = vi.spyOn(examManagementService, 'findExamStudentsPaged');

            component.loadExamStudents(mockLazyEvent);

            expect(findPagedSpy).not.toHaveBeenCalled();
        });
    });

    describe('onFilterChange', () => {
        it('should update activeFilter and re-trigger load with the last lazy event', () => {
            fixture.detectChanges();
            const findPagedSpy = vi.spyOn(examManagementService, 'findExamStudentsPaged').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.loadExamStudents(mockLazyEvent); // sets lastLazyEvent
            findPagedSpy.mockClear();

            component.onFilterChange('Submitted');

            expect(component.activeFilter()).toBe('Submitted');
            expect(findPagedSpy).toHaveBeenCalledOnce();
        });

        it('should update activeFilter but not call findExamStudentsPaged when no previous event exists', () => {
            fixture.detectChanges();
            const findPagedSpy = vi.spyOn(examManagementService, 'findExamStudentsPaged').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onFilterChange('NotStarted');

            expect(component.activeFilter()).toBe('NotStarted');
            expect(findPagedSpy).not.toHaveBeenCalled();
        });
    });

    describe('reloadStudentsView', () => {
        it('should refresh exam stats without re-fetching the exam', () => {
            fixture.detectChanges();
            const examChecklistService = TestBed.inject(ExamChecklistService);
            const statsSpy = vi.spyOn(examChecklistService, 'getExamStatistics');
            const findSpy = vi.spyOn(examManagementService, 'find');

            component.reloadStudentsView();

            expect(findSpy).not.toHaveBeenCalled();
            expect(statsSpy).toHaveBeenCalledWith(component.exam());
        });

        it('should do nothing when exam has no id', () => {
            fixture.detectChanges();
            component.exam.set({ ...examWithCourse, id: undefined });
            const examChecklistService = TestBed.inject(ExamChecklistService);
            const statsSpy = vi.spyOn(examChecklistService, 'getExamStatistics');
            const findSpy = vi.spyOn(examManagementService, 'find');

            component.reloadStudentsView();

            expect(findSpy).not.toHaveBeenCalled();
            expect(statsSpy).not.toHaveBeenCalled();
        });
    });

    describe('removeFromExam', () => {
        it('should call removeStudentFromExam with login from DTO and trigger reload', () => {
            fixture.detectChanges();
            const removeSpy = vi.spyOn(examManagementService, 'removeStudentFromExam').mockReturnValue(of(new HttpResponse<void>()));
            const reloadSpy = vi.spyOn(component, 'reloadStudentsView').mockImplementation(() => {});

            component.removeFromExam(mockDto, { deleteParticipationsAndSubmission: false });

            expect(removeSpy).toHaveBeenCalledWith(course.id, examWithCourse.id, mockDto.login, false);
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('should pass deleteParticipationsAndSubmission=true when checked', () => {
            fixture.detectChanges();
            const removeSpy = vi.spyOn(examManagementService, 'removeStudentFromExam').mockReturnValue(of(new HttpResponse<void>()));

            component.removeFromExam(mockDto, { deleteParticipationsAndSubmission: true });

            expect(removeSpy).toHaveBeenCalledWith(course.id, examWithCourse.id, mockDto.login, true);
        });

        it('should not call removeStudentFromExam when login is missing in DTO', () => {
            fixture.detectChanges();
            const removeSpy = vi.spyOn(examManagementService, 'removeStudentFromExam');

            component.removeFromExam({ ...mockDto, login: undefined }, { deleteParticipationsAndSubmission: false });

            expect(removeSpy).not.toHaveBeenCalled();
        });
    });

    describe('removeAllStudents', () => {
        it('should call removeAllStudentsFromExam and trigger reload on success', () => {
            fixture.detectChanges();
            const removeSpy = vi.spyOn(examManagementService, 'removeAllStudentsFromExam').mockReturnValue(of(new HttpResponse<void>()));
            const reloadSpy = vi.spyOn(component, 'reloadStudentsView').mockImplementation(() => {});

            component.removeAllStudents({ deleteParticipationsAndSubmission: false });

            expect(removeSpy).toHaveBeenCalledWith(course.id, examWithCourse.id, false);
            expect(reloadSpy).toHaveBeenCalled();
        });
    });

    describe('registerAllStudentsFromCourse', () => {
        it('should call addAllStudentsOfCourseToExam and trigger reload on success', () => {
            fixture.detectChanges();
            const addSpy = vi.spyOn(examManagementService, 'addAllStudentsOfCourseToExam').mockReturnValue(of(new HttpResponse<void>()));
            const reloadSpy = vi.spyOn(component, 'reloadStudentsView').mockImplementation(() => {});

            component.registerAllStudentsFromCourse();

            expect(addSpy).toHaveBeenCalledWith(course.id, examWithCourse.id);
            expect(reloadSpy).toHaveBeenCalled();
        });
    });

    describe('generateMissingStudentExams', () => {
        it('should call generateMissingStudentExams, show success alert, and reload', () => {
            fixture.detectChanges();
            const generateSpy = vi
                .spyOn(examManagementService, 'generateMissingStudentExams')
                .mockReturnValue(of(new HttpResponse({ body: [{} as StudentExam, {} as StudentExam] })));
            const reloadSpy = vi.spyOn(component, 'reloadStudentsView').mockImplementation(() => {});
            const successSpy = vi.spyOn(alertService, 'success');

            component.generateMissingStudentExams();

            expect(generateSpy).toHaveBeenCalledWith(course.id, examWithCourse.id);
            expect(successSpy).toHaveBeenCalledWith('artemisApp.studentExams.missingStudentExamGenerationSuccess', { number: 2 });
            expect(reloadSpy).toHaveBeenCalled();
        });

        it('should show error alert and reset isLoading on failure', () => {
            fixture.detectChanges();
            vi.spyOn(examManagementService, 'generateMissingStudentExams').mockReturnValue(
                throwError(() => new HttpErrorResponse({ error: { message: 'generation failed' }, status: 500 })),
            );
            const errorSpy = vi.spyOn(alertService, 'error');

            component.generateMissingStudentExams();

            expect(component.isLoading()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.studentExams.missingStudentExamGenerationError', { message: 'generation failed' });
        });
    });

    describe('startExercises', () => {
        it('should call startExercises, show success alert, and reset isLoading', () => {
            fixture.detectChanges();
            vi.spyOn(examManagementService, 'startExercises').mockReturnValue(of(new HttpResponse({ body: null })));
            const successSpy = vi.spyOn(alertService, 'success');

            component.startExercises();

            expect(successSpy).toHaveBeenCalledWith('artemisApp.studentExams.startExerciseSuccess');
            expect(component.isLoading()).toBe(false);
        });

        it('should show error alert and reset isLoading when starting exercises fails', () => {
            fixture.detectChanges();
            vi.spyOn(examManagementService, 'startExercises').mockReturnValue(throwError(() => new HttpErrorResponse({ error: { message: 'start failed' }, status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.startExercises();

            expect(component.isLoading()).toBe(false);
            expect(errorSpy).toHaveBeenCalledWith('artemisApp.studentExams.startExerciseFailure', { message: 'start failed' });
        });
    });

    describe('openVerifyAttendance', () => {
        it('should navigate to verify-attendance when exam has started', () => {
            fixture.detectChanges();
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            component.hasExamStarted.set(true);
            component.exam.set({ ...examWithCourse, id: 42 } as Exam);

            component.openVerifyAttendance();

            expect(navigateSpy).toHaveBeenCalledWith(['/course-management', course.id, 'exams', 42, 'students', 'verify-attendance']);
        });

        it('should not navigate when exam has not started', () => {
            fixture.detectChanges();
            const navigateSpy = vi.spyOn(router, 'navigate').mockResolvedValue(true);
            component.hasExamStarted.set(false);
            component.exam.set({ ...examWithCourse, id: 42 } as Exam);

            component.openVerifyAttendance();

            expect(navigateSpy).not.toHaveBeenCalled();
        });
    });

    describe('handleGenerateStudentExams', () => {
        it('should show a confirmation dialog when student exams already exist', () => {
            fixture.detectChanges();
            component.studentExamCount.set(2);
            const confirmationService = fixture.debugElement.injector.get(ConfirmationService);
            const confirmSpy = vi.spyOn(confirmationService, 'confirm');

            component.handleGenerateStudentExams(undefined);

            expect(confirmSpy).toHaveBeenCalled();
        });

        it('should call generateStudentExams directly when no student exams exist yet', () => {
            fixture.detectChanges();
            vi.spyOn(component as any, 'openIndividualExamsStatusPopover').mockImplementation(() => {});
            const generateSpy = vi.spyOn(examManagementService, 'generateStudentExams').mockReturnValue(of(new HttpResponse({ body: [] as any })));
            component.studentExamCount.set(0);

            component.handleGenerateStudentExams(undefined);

            expect(generateSpy).toHaveBeenCalledWith(course.id, examWithCourse.id);
        });
    });

    describe('toStudentExam', () => {
        it('should return undefined when studentExamId is not set', () => {
            fixture.detectChanges();
            expect(component.toStudentExam({ id: 1 })).toBeUndefined();
        });

        it('should map DTO fields to a StudentExam and set the exam reference', () => {
            fixture.detectChanges();
            const se: StudentExam | undefined = component.toStudentExam(mockDto);

            expect(se).toBeDefined();
            expect(se!.id).toBe(mockDto.studentExamId);
            expect(se!.workingTime).toBe(mockDto.workingTime);
            expect(se!.started).toBe(mockDto.started);
            expect(se!.submitted).toBe(mockDto.submitted);
            expect(se!.testRun).toBe(false);
            expect(se!.exam).toBe(component.exam());
        });
    });

    describe('toExamUserForReseating', () => {
        it('should map DTO to ExamUser with user.firstName set for getSelectedStudentName()', () => {
            fixture.detectChanges();
            const dto: ExamStudentDTO = {
                id: 10,
                userId: 1,
                login: 'student1',
                name: 'Student One',
                plannedRoom: 'Room A',
                actualRoom: 'Room B',
                plannedSeat: 'Seat 1',
                actualSeat: 'Seat 2',
            };

            const examUser = component.toExamUserForReseating(dto);

            expect(examUser.id).toBe(dto.id);
            expect(examUser.user?.id).toBe(dto.userId);
            expect(examUser.user?.login).toBe(dto.login);
            expect(examUser.user?.firstName).toBe(dto.name);
            expect(examUser.plannedRoom).toBe(dto.plannedRoom);
            expect(examUser.actualRoom).toBe(dto.actualRoom);
            expect(examUser.plannedSeat).toBe(dto.plannedSeat);
            expect(examUser.actualSeat).toBe(dto.actualSeat);
        });
    });

    describe('attendance helpers', () => {
        beforeEach(() => {
            fixture.detectChanges();
            component.hasExamEnded.set(true);
        });

        it('attendanceCheckPassed should be true when attended and all checks completed', () => {
            const dto: ExamStudentDTO = {
                id: 1,
                didExamUserAttendExam: true,
                didCheckLogin: true,
                didCheckImage: true,
                didCheckName: true,
                didCheckRegistrationNumber: true,
                signingImagePath: '/path/to/image',
            };
            expect(component.attendanceCheckPassed(dto)).toBeTruthy();
        });

        it('attendanceCheckFailed should be true when attended but a check is incomplete', () => {
            const dto: ExamStudentDTO = {
                id: 1,
                didExamUserAttendExam: true,
                didCheckLogin: false,
                didCheckImage: true,
                didCheckName: true,
                didCheckRegistrationNumber: true,
                signingImagePath: '/path/to/image',
            };
            expect(component.attendanceCheckFailed(dto)).toBeTruthy();
        });

        it('didNotAttendExam should be true when student did not attend', () => {
            const dto: ExamStudentDTO = { id: 1, didExamUserAttendExam: false };
            expect(component.didNotAttendExam(dto)).toBeTruthy();
        });

        it('didNotAttendExam should be false when attendance is undefined (not yet checked)', () => {
            const dto: ExamStudentDTO = { id: 1 };
            expect(component.didNotAttendExam(dto)).toBeFalsy();
        });

        it('all attendance helpers should be falsy when exam has not ended', () => {
            component.hasExamEnded.set(false);
            const dto: ExamStudentDTO = {
                id: 1,
                didExamUserAttendExam: true,
                didCheckLogin: false,
            };
            expect(component.attendanceCheckFailed(dto)).toBeFalsy();
            expect(component.attendanceCheckPassed(dto)).toBeFalsy();
            expect(component.didNotAttendExam(dto)).toBeFalsy();
        });
    });
});
