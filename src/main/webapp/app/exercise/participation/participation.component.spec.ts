import { signal } from '@angular/core';
import { ParticipationPrivacyService } from './participation-privacy.service';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ActivatedRoute, Params, Router, convertToParamMap } from '@angular/router';
import { HttpErrorResponse, HttpResponse, provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { FilterProp, ParticipationComponent } from 'app/exercise/participation/participation.component';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { Course } from 'app/course/shared/entities/course.model';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { of, throwError } from 'rxjs';
import { ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import dayjs from 'dayjs/esm';
import { ProgrammingSubmissionService } from 'app/programming/shared/services/programming-submission.service';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { MockProvider } from 'ng-mocks';
import { MockProgrammingSubmissionService } from 'test/helpers/mocks/service/mock-programming-submission.service';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { AlertService } from 'app/foundation/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { By } from '@angular/platform-browser';
import { TumAetUiDatePickerComponent } from '@tumaet/ui-angular';
import { WebsocketService } from 'app/foundation/service/websocket.service';
import { MockWebsocketService } from 'test/helpers/mocks/service/mock-websocket.service';
import { PageableResult } from 'app/foundation/pagination/pageable-table';
import { DialogService } from 'primeng/dynamicdialog';
import { ProgrammingExercise } from 'app/programming/shared/entities/programming-exercise.model';
import { ResultService } from 'app/exercise/result/result.service';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { Range } from 'app/foundation/util/utils';
import { ParticipationNameExportDTO } from 'app/exercise/exercise-scores/participation-name-export-dto.model';
import { MockResultService } from 'test/helpers/mocks/service/mock-result.service';
import { CourseTitleBarService } from 'app/course/shared/services/course-title-bar.service';
import { ParticipationType } from 'app/exercise/shared/entities/participation/participation.model';
import { DeleteDialogService } from 'app/shared-ui/delete-dialog/service/delete-dialog.service';

describe('ParticipationComponent', () => {
    let component: ParticipationComponent;
    let componentFixture: ComponentFixture<ParticipationComponent>;
    let participationService: ParticipationService;
    let exerciseService: ExerciseService;
    let alertService: AlertService;
    let resultService: ResultService;
    let profileService: ProfileService;
    let router: Router;
    let queryParams: Params;

    const course: Course = { id: 10, presentationScore: 1 };

    const exercise: Exercise = {
        numberOfAssessmentsOfCorrectionRounds: [],
        studentAssignedTeamIdComputed: false,
        id: 1,
        secondCorrectionEnabled: true,
        type: ExerciseType.TEXT,
        course,
    };

    const sampleDto: ParticipationManagementDTO = {
        participationId: 3,
        submissionCount: 1,
        testRun: false,
        participantName: 'Alice',
        participantIdentifier: 'alice',
    };

    const route = {
        params: of({ courseId: '10', exerciseId: '1' } as Params),
        snapshot: {
            get queryParamMap() {
                return convertToParamMap(queryParams);
            },
        },
    } as unknown as ActivatedRoute;

    const filters = () => component.filterGroups().flatMap((group) => group.items);
    const headerKeys = () => component.columns().map((column) => column.headerKey);

    beforeEach(() => {
        queryParams = {};
        TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ProfileService, useClass: MockProfileService },
                { provide: ProgrammingSubmissionService, useClass: MockProgrammingSubmissionService },
                { provide: AlertService, useClass: MockAlertService },
                LocalStorageService,
                SessionStorageService,
                MockProvider(ExerciseService),
                MockProvider(ParticipationService),
                MockProvider(DeleteDialogService),
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(EventManager),
                { provide: WebsocketService, useClass: MockWebsocketService },
                { provide: ResultService, useClass: MockResultService },
                MockProvider(DialogService),
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(ParticipationComponent);
                component = componentFixture.componentInstance;
                participationService = TestBed.inject(ParticipationService);
                exerciseService = TestBed.inject(ExerciseService);
                alertService = TestBed.inject(AlertService);
                resultService = TestBed.inject(ResultService);
                profileService = TestBed.inject(ProfileService);
                router = TestBed.inject(Router);
                vi.spyOn(router, 'navigate').mockResolvedValue(true);
                component.exercise.set(exercise);
            });
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('Initialization', () => {
        it('should initialize with exerciseId from route', () => {
            const exerciseFindStub = vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            component.ngOnInit();

            expect(exerciseFindStub).toHaveBeenCalledExactlyOnceWith(1);
            expect(component.exercise()).toEqual(exercise);
            expect(component.course()?.id).toBe(10);
        });

        it('should open the score range named in the query parameters', () => {
            queryParams = { scoreRangeFilter: '3' };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exercise })));

            component.ngOnInit();

            expect(component.rangeFilter()).toEqual(new Range(30, 40));
        });
    });

    describe('Columns', () => {
        const render = async (ex: Exercise, dto: ParticipationManagementDTO) => {
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: ex })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [dto], totalElements: 1 }));
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();
            return componentFixture.nativeElement.querySelector('tumaet-ui-table tbody tr') as HTMLElement;
        };

        it('should show the state and the result of a participation side by side', () => {
            component.exercise.set({ ...exercise, isAtLeastInstructor: true, dueDate: dayjs() });

            expect(headerKeys()).toEqual([
                'artemisApp.participation.student',
                'artemisApp.participation.initializationState',
                'artemisApp.participation.initializationDate',
                'artemisApp.exercise.lastResult',
                'artemisApp.exercise.completionDate',
                'artemisApp.exercise.submissionCount',
                'artemisApp.participation.individualDueDate',
            ]);
        });

        it('should list the team members below the team instead of in a column of their own', async () => {
            const row = await render(
                { ...exercise, isAtLeastInstructor: true, teamMode: true },
                { ...sampleDto, participantName: 'Team A', teamId: 7, teamStudents: [{ name: 'Bob', login: 'bob' }] },
            );

            expect(headerKeys()).not.toContain('artemisApp.participation.students');
            const teamCell = row.querySelector('td')!;
            expect(teamCell.querySelector('a[href*="/teams/7"]')?.textContent).toContain('Team A');
            expect(teamCell.textContent).toContain('bob');
        });

        it('should mark a practice participation in its status', async () => {
            const row = await render({ ...exercise, isAtLeastInstructor: true }, { ...sampleDto, testRun: true, initializationState: 'INITIALIZED' });

            expect(row.querySelector('[data-testid="participation-practice-tag"]')).not.toBeNull();
        });

        it('should name the assessment type next to the result only where manual results exist', async () => {
            const dto: ParticipationManagementDTO = { ...sampleDto, submissionId: 2, resultId: 5, score: 80, assessmentType: AssessmentType.MANUAL, completionDate: dayjs() };
            // MockResultService leaves out what jhi-result needs to render the score
            Object.defineProperty(resultService, 'getResultString', { value: vi.fn(() => '80%') });
            const row = await render({ ...exercise, isAtLeastInstructor: true, assessmentType: AssessmentType.MANUAL }, dto);

            expect(row.querySelector('jhi-result')).not.toBeNull();
            expect(row.querySelector('[data-testid="participation-assessment-type"]')?.textContent).toContain(AssessmentType.MANUAL);
        });

        it('should hide the assessment type and note for automatically assessed exercises', () => {
            component.exercise.set({ ...exercise, assessmentType: AssessmentType.AUTOMATIC });
            expect(component.showAssessmentType()).toBe(false);
            expect(component.showAssessmentNote()).toBe(false);

            component.exercise.set({ ...exercise, assessmentType: AssessmentType.SEMI_AUTOMATIC, allowComplaintsForAutomaticAssessments: true });
            expect(component.showAssessmentType()).toBe(true);
            expect(component.showAssessmentNote()).toBe(true);
        });
    });

    describe('Live repository actions', () => {
        it.each([
            ['tutor', false, false],
            ['editor', true, false],
            ['instructor', true, true],
        ])('shows the online editor only to editors and instructors (%s)', async (_role, isAtLeastEditor, isAtLeastInstructor) => {
            vi.spyOn(exerciseService, 'find').mockReturnValue(
                of(
                    new HttpResponse({
                        body: { ...exercise, type: ExerciseType.PROGRAMMING, isAtLeastTutor: true, isAtLeastEditor, isAtLeastInstructor, dueDate: dayjs().add(1, 'day') },
                    }),
                ),
            );
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(
                of({ content: [{ ...sampleDto, repositoryUri: 'https://localhost/git/EX/alice.git' }], totalElements: 1 }),
            );
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();

            expect(!!componentFixture.nativeElement.querySelector('a[href*="code-editor"]')).toBe(isAtLeastEditor || isAtLeastInstructor);
            expect(componentFixture.nativeElement.querySelector('jhi-code-button')).not.toBeNull();
        });

        it('preserves editor and instructor access while identities are masked', () => {
            component.exercise.set({
                ...exercise,
                type: ExerciseType.PROGRAMMING,
                isAtLeastEditor: true,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                dueDate: dayjs().subtract(1, 'hour'),
            });
            expect(component.hideParticipantInformation()).toBe(true);
            expect(component.canAccessLiveRepository()).toBe(true);
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING, isAtLeastEditor: true, exerciseGroup: { id: 1 } });
            expect(component.canAccessLiveRepository()).toBe(true);
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING, isAtLeastInstructor: true, exerciseGroup: { id: 1 } });
            expect(component.canAccessLiveRepository()).toBe(true);
        });
    });

    describe('Tutor support access', () => {
        it.each([AssessmentType.AUTOMATIC, AssessmentType.SEMI_AUTOMATIC])('shows names, code and an explanation before the deadline (%s)', async (assessmentType) => {
            const supportExercise = {
                ...exercise,
                type: ExerciseType.PROGRAMMING,
                assessmentType,
                isAtLeastTutor: true,
                isAtLeastInstructor: false,
                dueDate: dayjs().add(1, 'day'),
            };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: supportExercise })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(
                of({ content: [{ ...sampleDto, repositoryUri: 'https://localhost/git/EX/alice.git' }], totalElements: 1 }),
            );
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();

            expect(component.tableOptions().showSearch).toBe(true);
            expect(component.columns()[0].field).toBe('participantName');
            expect(component.columns().some((column) => column.headerKey === 'artemisApp.participation.repository')).toBe(true);
            expect(componentFixture.nativeElement.querySelector('jhi-code-button')).not.toBeNull();
            expect(componentFixture.nativeElement.querySelector('a[href*="code-editor"]')).toBeNull();
            const notice = componentFixture.nativeElement.querySelector('[data-testid="tutor-participation-access-notice"]');
            expect(notice).not.toBeNull();
            expect(notice.textContent).toContain(assessmentType === AssessmentType.AUTOMATIC ? 'tutorAccess.available' : 'tutorAccess.untilDueDate');
        });

        it('removes named data and reloads anonymously when an open page switches to anonymous assessment', async () => {
            const anonymous = signal(false);
            vi.spyOn(TestBed.inject(ParticipationPrivacyService), 'isParticipantInformationHidden').mockImplementation(() => anonymous());
            const dueDate = dayjs().add(2, 'seconds');
            const supportExercise = {
                ...exercise,
                type: ExerciseType.PROGRAMMING,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                isAtLeastTutor: true,
                isAtLeastInstructor: false,
                dueDate,
            };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: supportExercise })));
            const search = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [sampleDto], totalElements: 1 }));
            componentFixture.detectChanges();
            component.onDataRequest({ pageIndex: 0, pageSize: 50, searchTerm: 'alice', sort: { field: 'participantName', direction: 'asc' } });
            componentFixture.detectChanges();
            expect(component.hideParticipantInformation()).toBe(false);
            search.mockReturnValue(of({ content: [{ participationId: 3, submissionCount: 1, testRun: false }], totalElements: 1 }));
            anonymous.set(true);
            componentFixture.detectChanges();

            expect(component.hideParticipantInformation()).toBe(true);
            expect(component.tableOptions().showSearch).toBe(false);
            expect(component.participations()[0].participantName).toBeUndefined();
            expect(search).toHaveBeenLastCalledWith(1, expect.objectContaining({ searchTerm: '', sortedColumn: 'id' }));
            expect(componentFixture.nativeElement.querySelector('jhi-code-button')).toBeNull();
            expect(componentFixture.nativeElement.querySelector('a[href*="code-editor"]')).toBeNull();
            expect(componentFixture.nativeElement.querySelector('[data-testid="tutor-participation-access-notice"]').textContent).toContain('tutorAccess.anonymousAssessment');
            expect(componentFixture.nativeElement.querySelector('a[href*="assessment-dashboard"]')).not.toBeNull();
        });
    });

    describe('Anonymous participation list', () => {
        it.each([false, true])('should show participation IDs without identity search or team links for tutors (team mode: %s)', async (teamMode) => {
            const tutorExercise = {
                ...exercise,
                course: undefined,
                type: ExerciseType.PROGRAMMING,
                isAtLeastTutor: true,
                isAtLeastInstructor: false,
                teamMode,
                assessmentType: AssessmentType.SEMI_AUTOMATIC,
                dueDate: dayjs().subtract(1, 'day'),
            };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: tutorExercise })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [sampleDto], totalElements: 1 }));

            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();

            expect(component.columns()[0]).toMatchObject({ headerKey: 'artemisApp.participation.participationId', field: 'participationId', sort: true });
            expect(
                component.columns().some((column) => column.headerKey === 'artemisApp.participation.students' || column.headerKey === 'artemisApp.participation.repository'),
            ).toBe(false);
            const table: HTMLElement = componentFixture.nativeElement.querySelector('tumaet-ui-table');
            expect(table.querySelector('tbody td')?.textContent?.trim()).toBe(String(sampleDto.participationId));
            expect(table.textContent).not.toContain(sampleDto.participantName);
            expect(table.querySelector('a[href*="/teams/"]')).toBeNull();
            expect(table.querySelector('input[type=search]')).toBeNull();
        });

        it.each([false, true])('should retain identity columns and search for instructors (team mode: %s)', (teamMode) => {
            component.exercise.set({ ...exercise, teamMode, isAtLeastInstructor: true });

            expect(component.tableOptions().showSearch).not.toBe(false);
            expect(component.columns()[0]).toMatchObject({
                headerKey: teamMode ? 'artemisApp.participation.team' : 'artemisApp.participation.student',
                field: 'participantName',
                sort: true,
            });
        });

        it.each(['participantName', 'participantIdentifier', 'buildPlanId'])(
            'should discard stale identity search and sorting for tutors while retaining filters and paging (%s)',
            (sortField) => {
                component.exercise.set({ ...exercise, isAtLeastInstructor: false });
                component.activeFilters.set([FilterProp.NO_SUBMISSIONS]);
                const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));

                component.onDataRequest({ pageIndex: 2, pageSize: 25, searchTerm: 'alice', sort: { field: sortField, direction: 'asc' } });

                expect(searchSpy).toHaveBeenCalledWith(
                    exercise.id,
                    expect.objectContaining({ page: 2, pageSize: 25, searchTerm: '', sortedColumn: 'id', filterProp: FilterProp.NO_SUBMISSIONS }),
                );
            },
        );

        it('should retain instructor identity search and sorting', () => {
            component.exercise.set({ ...exercise, isAtLeastInstructor: true });
            const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onDataRequest({ pageIndex: 0, pageSize: 50, searchTerm: 'alice', sort: { field: 'participantName', direction: 'asc' } });

            expect(searchSpy).toHaveBeenCalledWith(exercise.id, expect.objectContaining({ searchTerm: 'alice', sortedColumn: 'participantName' }));
        });

        it('should identify an anonymous participation in the due-date success message', () => {
            const dto: ParticipationManagementDTO = { participationId: 42, submissionCount: 1, testRun: false };
            vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const successSpy = vi.spyOn(alertService, 'success');
            component.startEditDueDate(dto);

            component.saveIndividualDueDate(dto);

            expect(successSpy).toHaveBeenCalledWith('artemisApp.participation.updateDueDates.success', { name: '42' });
        });
    });

    describe('Practice participations of a team exercise', () => {
        const teamDto: ParticipationManagementDTO = {
            participationId: 11,
            submissionCount: 2,
            testRun: false,
            participantName: 'Team Alpha',
            participantIdentifier: 'alpha',
            teamId: 7,
            teamStudents: [
                { name: 'Alice Anderson', login: 'alice' },
                { name: 'Bob Brown', login: 'bob' },
            ],
        };

        const practiceDto: ParticipationManagementDTO = {
            participationId: 12,
            submissionCount: 0,
            testRun: true,
            participantName: 'Carol Clark',
            participantIdentifier: 'carol',
            studentId: 5,
            studentLogin: 'carol',
        };

        async function renderRows(teamMode: boolean, rows: ParticipationManagementDTO[]): Promise<HTMLElement> {
            const instructorExercise = { ...exercise, teamMode, isAtLeastTutor: true, isAtLeastInstructor: true };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: instructorExercise })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: rows, totalElements: rows.length }));

            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();

            return componentFixture.nativeElement.querySelector('tumaet-ui-table');
        }

        function bodyRows(table: HTMLElement): HTMLTableRowElement[] {
            return Array.from(table.querySelectorAll<HTMLTableRowElement>('tbody tr'));
        }

        it('should offer a new manual assessment for the team row but not its individual practice row', async () => {
            const table = await renderRows(true, [
                { ...teamDto, submissionId: 21 },
                { ...practiceDto, submissionCount: 1, submissionId: 22 },
            ]);
            const [teamRow, practiceRow] = bodyRows(table);

            expect(teamRow.querySelector('jhi-manage-assessment-buttons a')?.textContent).toContain('artemisApp.assessment.dashboard.actions.assess');
            expect(practiceRow.querySelector('jhi-manage-assessment-buttons a')).toBeNull();
        });

        it('should list a team row and a practice row of a team exercise', async () => {
            const table = await renderRows(true, [teamDto, practiceDto]);

            expect(bodyRows(table)).toHaveLength(2);
            expect(table.querySelectorAll('[data-testid="participation-practice-tag"]')).toHaveLength(1);
        });

        it('should render a team row with its team link and members and without a practice marker', async () => {
            const table = await renderRows(true, [teamDto, practiceDto]);
            const teamRow = bodyRows(table)[0];

            const teamLink = teamRow.querySelector('a[href*="/teams/"]');
            expect(teamLink?.getAttribute('href')).toContain('/course-management/10/exercises/1/teams/7');
            expect(teamLink?.textContent?.trim()).toBe('Team Alpha');
            expect(teamRow.querySelector('jhi-team-students-list')).not.toBeNull();
            expect(teamRow.textContent).toContain('Alice Anderson');
            expect(teamRow.textContent).toContain('Bob Brown');
            expect(teamRow.querySelector('[data-testid="participation-practice-tag"]')).toBeNull();
            expect(teamRow.querySelector('[data-testid="participation-practice-student"]')).toBeNull();
        });

        it('should render a practice row with the student, the practice marker and no team link', async () => {
            const table = await renderRows(true, [teamDto, practiceDto]);
            const practiceRow = bodyRows(table)[1];

            expect(practiceRow.textContent).toContain('Carol Clark');
            expect(practiceRow.querySelector('a[href*="/teams/"]')).toBeNull();
            expect(practiceRow.querySelector('jhi-team-students-list')).toBeNull();
            expect(practiceRow.querySelector('[data-testid="participation-practice-tag"]')?.textContent).toContain('artemisApp.participation.practice');
            expect(practiceRow.querySelector('[data-testid="participation-practice-student"]')?.textContent?.trim()).toBe('Carol Clark (carol)');
        });

        it('should link the student of a practice row to the user management for administrators', async () => {
            const table = await renderRows(true, [teamDto, practiceDto]);
            component.isAdmin.set(true);
            componentFixture.detectChanges();
            const [teamRow, practiceRow] = bodyRows(table);

            const studentLink = practiceRow.querySelector('a[href*="/admin/user-management/"]');
            expect(studentLink?.getAttribute('href')).toContain('/admin/user-management/carol');
            expect(studentLink?.textContent?.trim()).toBe('Carol Clark');
            expect(practiceRow.querySelector('a[href*="/teams/"]')).toBeNull();
            expect(teamRow.querySelector('a[href*="/admin/user-management/"]')).toBeNull();
            expect(teamRow.querySelector('a[href*="/teams/7"]')).not.toBeNull();
        });

        it('should mark practice in the status column for an individual exercise', async () => {
            const table = await renderRows(false, [{ ...practiceDto, testRun: true }, sampleDto]);

            expect(bodyRows(table)).toHaveLength(2);
            expect(bodyRows(table)[0].querySelector('td [data-testid="participation-practice-tag"]')).not.toBeNull();
            expect(table.querySelector('[data-testid="participation-practice-student"]')).toBeNull();
            expect(table.querySelector('a[href*="/teams/"]')).toBeNull();
            expect(bodyRows(table)[0].textContent).toContain('Carol Clark');
            expect(bodyRows(table)[1].textContent).toContain('Alice');
        });

        it('should keep the identity columns of a team exercise for practice rows', async () => {
            await renderRows(true, [practiceDto]);

            expect(component.columns().map((column) => column.headerKey)).toEqual(expect.arrayContaining(['artemisApp.participation.team']));
            expect(component.participations()).toEqual([practiceDto]);
        });
    });

    describe('Pagination / lazy loading', () => {
        it('should load page when a lazy load event fires', () => {
            const searchStub = vi
                .spyOn(participationService, 'searchParticipations')
                .mockReturnValue(of({ content: [sampleDto], totalElements: 1 } as PageableResult<ParticipationManagementDTO>));

            component.onDataRequest({ pageIndex: 0, pageSize: 50 });

            expect(searchStub).toHaveBeenCalledOnce();
            expect(component.participations()).toEqual([sampleDto]);
            expect(component.totalRows()).toBe(1);
            expect(component.isLoading()).toBe(false);
        });

        it('should update active filter and reload on filter change', () => {
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));

            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchStub.mockClear();

            component.updateParticipationFilter([FilterProp.FAILED, FilterProp.MANUAL]);

            expect(component.activeFilters()).toEqual([FilterProp.FAILED, FilterProp.MANUAL]);
            expect(searchStub).toHaveBeenCalledExactlyOnceWith(exercise.id, expect.objectContaining({ filterProp: 'Failed,Manual' }));
        });
    });

    it('preserves server paging, descending sorting, search and combined filters from the TUM table', () => {
        component.exercise.set({ ...exercise, isAtLeastInstructor: true });
        component.activeFilters.set([FilterProp.SUCCESSFUL, FilterProp.MANUAL]);
        const search = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
        component.onDataRequest({ pageIndex: 2, pageSize: 25, searchTerm: 'alice', sort: { field: 'score', direction: 'desc' } });
        expect(search).toHaveBeenCalledExactlyOnceWith(
            exercise.id,
            expect.objectContaining({
                page: 2,
                pageSize: 25,
                searchTerm: 'alice',
                sortedColumn: 'score',
                sortingOrder: 'DESCENDING',
                filterProp: 'Successful,Manual',
            }),
        );
    });

    describe('Filters', () => {
        it('should not include the build filters for non-programming exercises', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.TEXT });

            expect(filters()).not.toContain(FilterProp.FAILED);
            expect(filters()).not.toContain(FilterProp.BUILD_FAILED);
            expect(filters()).not.toContain(FilterProp.NO_PRACTICE);
            expect(filters()).toContain(FilterProp.NO_SUBMISSIONS);
        });

        it('should include the build filters for programming exercises', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING });

            expect(filters()).toContain(FilterProp.FAILED);
            expect(filters()).toContain(FilterProp.BUILD_FAILED);
            expect(filters()).toContain(FilterProp.NO_PRACTICE);
        });

        it.each([
            [FilterProp.SUCCESSFUL, { type: ExerciseType.PROGRAMMING } as Exercise, false, true],
            [FilterProp.UNSUCCESSFUL, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.MANUAL, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: true }, false, true],
            [FilterProp.MANUAL, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: false }, false, false],
            [FilterProp.MANUAL, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.AUTOMATIC, { type: ExerciseType.PROGRAMMING, allowComplaintsForAutomaticAssessments: false }, false, false],
            [FilterProp.AUTOMATIC, { type: ExerciseType.TEXT }, true, true],
            [FilterProp.LOCKED, { type: ExerciseType.PROGRAMMING, isAtLeastInstructor: true }, true, true],
            [FilterProp.LOCKED, { type: ExerciseType.PROGRAMMING, isAtLeastInstructor: false }, false, false],
            [FilterProp.LOCKED, { type: ExerciseType.TEXT }, true, false],
        ])('should offer the result filter %s only where it applies', (filter: FilterProp, ex: Partial<Exercise>, newManualResultsAllowed: boolean, expected: boolean) => {
            component.exercise.set(ex as Exercise);
            component.newManualResultAllowed.set(newManualResultsAllowed);

            expect(filters().includes(filter)).toBe(expected);
        });

        it('should pass the score range to the search', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.rangeFilter.set(new Range(60, 70));

            component.onDataRequest({ pageIndex: 0, pageSize: 50 });

            expect(searchSpy).toHaveBeenCalledWith(exercise.id, expect.objectContaining({ scoreRangeLower: 60, scoreRangeUpper: 70 }));
        });

        it('should reset filter options, drop the range from the URL and reload', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            component.rangeFilter.set(new Range(0, 10));
            component.activeFilters.set([FilterProp.SUCCESSFUL]);
            searchSpy.mockClear();

            component.resetFilterOptions();

            expect(component.rangeFilter()).toBeUndefined();
            expect(component.activeFilters()).toEqual([]);
            expect(router.navigate).toHaveBeenCalledWith([], expect.objectContaining({ queryParams: { scoreRangeFilter: undefined } }));
            expect(searchSpy).toHaveBeenCalledOnce();
        });

        it('should refresh the current page', () => {
            const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [sampleDto], totalElements: 1 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchSpy.mockClear();

            component.refresh();

            expect(searchSpy).toHaveBeenCalledOnce();
            expect(component.participations()).toEqual([sampleDto]);
        });
    });

    describe('Navigation', () => {
        it('should return correct participation link for non-exam exercise', () => {
            expect(component.getParticipationLink(42)).toEqual(['42', 'submissions']);
        });

        it('should return correct participation link for exam exercise', () => {
            component.exercise.set({ ...exercise, exerciseGroup: { id: 5, exam: { id: 2 } } });

            expect(component.getParticipationLink(42)).toEqual(['42']);
        });
    });

    describe('Presentation enabled computed signals', () => {
        it('should compute basicPresentationEnabled correctly', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course: { ...course, presentationScore: 1 },
            });
            expect(component.basicPresentationEnabled()).toBe(true);

            component.exercise.set({ ...exercise, presentationScoreEnabled: false, course });
            expect(component.basicPresentationEnabled()).toBe(false);
        });

        it('should compute gradedPresentationEnabled correctly', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course,
            });
            component.gradeStepsDTO.set({ presentationsNumber: 2, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });

            expect(component.gradedPresentationEnabled()).toBe(true);

            component.gradeStepsDTO.set({ presentationsNumber: 0, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });
            expect(component.gradedPresentationEnabled()).toBe(false);
        });
    });

    describe('Individual due date input validation', () => {
        let picker: TumAetUiDatePickerComponent;
        let saveButton: HTMLButtonElement;
        let dto: ParticipationManagementDTO;
        const dueDate = dayjs('2030-06-01T12:00:00');
        const individualDueDate = dayjs('2030-06-03T12:00:00');

        /** Types into the picker's text input like a user does. */
        const type = async (text: string) => {
            const input: HTMLInputElement = componentFixture.nativeElement.querySelector(`#individual-due-date-${dto.participationId}`);
            input.value = text;
            input.dispatchEvent(new Event('input'));
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();
        };

        beforeEach(async () => {
            dto = { ...sampleDto, individualDueDate };
            const exerciseWithDueDate = { ...exercise, course: undefined, dueDate };
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: exerciseWithDueDate })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [dto], totalElements: 1 }));
            component.startEditDueDate(dto);
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            componentFixture.detectChanges();
            picker = componentFixture.debugElement.query(By.directive(TumAetUiDatePickerComponent)).componentInstance;
            saveButton = componentFixture.nativeElement.querySelector('button[aria-label="entity.action.save"]');
        });

        it('should show the existing individual due date and open the calendar on it', () => {
            expect(picker.value()!.isSame(individualDueDate)).toBe(true);
            expect(picker.startAt()!.isSame(individualDueDate)).toBe(true);
            expect(picker.invalid()).toBe(false);
            expect(saveButton.disabled).toBe(false);
        });

        it('should grey out the days before the due date of the exercise in the calendar', () => {
            expect(picker.min()!.isSame(component.exercise()!.dueDate!)).toBe(true);
        });

        it('should prevent saving text that is not a date', async () => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const successSpy = vi.spyOn(alertService, 'success');

            await type('invalid date');

            expect(saveButton.disabled).toBe(true);
            saveButton.click();
            expect(updateSpy).not.toHaveBeenCalled();
            expect(successSpy).not.toHaveBeenCalled();
            expect(dto.individualDueDate).toEqual(individualDueDate);
        });

        it('should prevent saving a date before the due date of the exercise and explain why', async () => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));

            await type('01.01.2020 12:00');

            expect(component.getPendingDueDate(dto.participationId)!.isSame(dayjs('2020-01-01T12:00:00'))).toBe(true);
            expect(picker.invalid()).toBe(true);
            expect(componentFixture.nativeElement.querySelector('[data-testid="individual-due-date-error"]')).not.toBeNull();
            expect(picker.ariaDescribedBy()).toBe(`individual-due-date-error-${dto.participationId}`);
            expect(saveButton.disabled).toBe(true);
            saveButton.click();
            expect(updateSpy).not.toHaveBeenCalled();
            expect(dto.individualDueDate).toEqual(individualDueDate);
        });

        it('should accept a date equal to the due date of the exercise', async () => {
            await type(dueDate.format('DD.MM.YYYY HH:mm'));

            expect(picker.invalid()).toBe(false);
            expect(componentFixture.nativeElement.querySelector('[data-testid="individual-due-date-error"]')).toBeNull();
            expect(saveButton.disabled).toBe(false);
        });

        it('should save when invalid input is corrected', async () => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const successSpy = vi.spyOn(alertService, 'success');
            await type('invalid date');
            expect(saveButton.disabled).toBe(true);

            await type(individualDueDate.format('DD.MM.YYYY HH:mm'));

            expect(saveButton.disabled).toBe(false);
            expect(component.getPendingDueDate(dto.participationId)).toEqual(individualDueDate);
            saveButton.click();
            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(component.exercise(), [expect.objectContaining({ id: dto.participationId, individualDueDate })]);
            expect(successSpy).toHaveBeenCalledOnce();
        });

        it('should allow clearing an existing individual due date after invalid input', async () => {
            const updateSpy = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [{ id: dto.participationId }] })));
            await type('invalid date');
            expect(saveButton.disabled).toBe(true);

            await type('');

            expect(saveButton.disabled).toBe(false);
            saveButton.click();
            expect(updateSpy).toHaveBeenCalledExactlyOnceWith(component.exercise(), [expect.objectContaining({ id: dto.participationId, individualDueDate: undefined })]);
            expect(dto.individualDueDate).toBeUndefined();
        });

        it('should forget the invalid text when the edit is cancelled', async () => {
            await type('invalid date');
            expect(component.isPendingDueDateInvalid(dto.participationId)).toBe(true);

            component.cancelEditDueDate(dto);

            expect(component.isPendingDueDateInvalid(dto.participationId)).toBe(false);
        });
    });

    describe('Individual due date', () => {
        it('should track individual due date editing lifecycle', () => {
            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);

            component.startEditDueDate(sampleDto);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(true);
            expect(component.getPendingDueDate(sampleDto.participationId)).toEqual(sampleDto.individualDueDate);

            const newDate = dayjs().add(1, 'day');
            component.setPendingDueDate(sampleDto.participationId, newDate);
            expect(component.getPendingDueDate(sampleDto.participationId)).toEqual(newDate);

            component.cancelEditDueDate(sampleDto);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);
            expect(component.getPendingDueDate(sampleDto.participationId)).toBeUndefined();
        });

        it('should save individual due date and reload', () => {
            const newDate = dayjs().add(2, 'days');
            component.startEditDueDate(sampleDto);
            component.setPendingDueDate(sampleDto.participationId, newDate);

            const updateStub = vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(of(new HttpResponse({ body: [] })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchStub.mockClear();

            component.saveIndividualDueDate(sampleDto);

            expect(updateStub).toHaveBeenCalledOnce();
            expect(sampleDto.individualDueDate).toEqual(newDate);
            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);
            expect(component.isSaving()).toBe(false);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should show error alert when saving due date fails', () => {
            component.startEditDueDate(sampleDto);
            vi.spyOn(participationService, 'updateIndividualDueDates').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.saveIndividualDueDate(sampleDto);

            expect(errorSpy).toHaveBeenCalledOnce();
            expect(component.isSaving()).toBe(false);
        });
    });

    describe('Delete participation', () => {
        it('should delete participation and reload', () => {
            vi.spyOn(participationService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchStub.mockClear();

            component.deleteParticipation(sampleDto.participationId);

            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should clean up pending due date and presentation map on delete', () => {
            vi.spyOn(participationService, 'delete').mockReturnValue(of(new HttpResponse<void>()));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });

            component.startEditDueDate(sampleDto);
            component.changeGradedPresentation(sampleDto);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(true);
            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(true);

            component.deleteParticipation(sampleDto.participationId);

            expect(component.isEditingDueDate(sampleDto.participationId)).toBe(false);
            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(false);
        });
    });

    describe('Cleanup programming participation', () => {
        it('should call cleanupBuildPlan and reload on success', () => {
            const cleanupStub = vi.spyOn(participationService, 'cleanupBuildPlan').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchStub.mockClear();

            component.cleanupProgrammingExerciseParticipation(sampleDto);

            expect(cleanupStub).toHaveBeenCalledOnce();
            expect(searchStub).toHaveBeenCalledOnce();
        });
    });

    describe('Graded presentation tracking', () => {
        it('should track graded presentation changes', () => {
            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(false);

            component.changeGradedPresentation(sampleDto);

            expect(component.hasGradedPresentationChanged(sampleDto)).toBe(true);
        });
    });

    describe('Basic presentation', () => {
        const basicExercise: Exercise = {
            ...exercise,
            isAtLeastTutor: true,
            presentationScoreEnabled: true,
            course: { id: 10, presentationScore: 1 },
        };

        it('should call update with presentationScore=1 and reload on success', () => {
            component.exercise.set(basicExercise);
            const updateStub = vi.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchStub.mockClear();

            component.addBasicPresentation(sampleDto);

            expect(updateStub).toHaveBeenCalledOnce();
            const passedParticipation = updateStub.mock.calls[0][1];
            expect(passedParticipation.presentationScore).toBe(1);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should show error alert when addBasicPresentation fails', () => {
            component.exercise.set(basicExercise);
            vi.spyOn(participationService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.addBasicPresentation(sampleDto);

            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('should not call update when basicPresentationEnabled is false', () => {
            component.exercise.set({ ...exercise, presentationScoreEnabled: false });
            const updateStub = vi.spyOn(participationService, 'update');

            component.addBasicPresentation(sampleDto);

            expect(updateStub).not.toHaveBeenCalled();
        });
    });

    describe('Graded presentation', () => {
        const gradedExercise: Exercise = {
            ...exercise,
            isAtLeastTutor: true,
            presentationScoreEnabled: true,
            course,
        };

        beforeEach(() => {
            component.exercise.set(gradedExercise);
            component.gradeStepsDTO.set({ presentationsNumber: 2, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });
        });

        it('should call update and reload on success', () => {
            const dto = { ...sampleDto, presentationScore: 75 };
            const updateStub = vi.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            component.changeGradedPresentation(dto);
            searchStub.mockClear();

            component.addGradedPresentation(dto);

            expect(updateStub).toHaveBeenCalledOnce();
            expect(component.hasGradedPresentationChanged(dto)).toBe(false);
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should not call update when score > 100', () => {
            const dto = { ...sampleDto, presentationScore: 101 };
            const updateStub = vi.spyOn(participationService, 'update');

            component.addGradedPresentation(dto);

            expect(updateStub).not.toHaveBeenCalled();
        });

        it('should not call update when score < 0', () => {
            const dto = { ...sampleDto, presentationScore: -1 };
            const updateStub = vi.spyOn(participationService, 'update');

            component.addGradedPresentation(dto);

            expect(updateStub).not.toHaveBeenCalled();
        });

        it('should clear presentationScore when maxPresentationsExceeded error is returned', () => {
            const dto = { ...sampleDto, presentationScore: 80 };
            vi.spyOn(participationService, 'update').mockReturnValue(
                throwError(
                    () =>
                        new HttpErrorResponse({
                            status: 400,
                            error: { errorKey: 'invalid.presentations.maxNumberOfPresentationsExceeded' },
                        }),
                ),
            );

            component.addGradedPresentation(dto);

            expect(dto.presentationScore).toBeUndefined();
        });

        it('should show error alert on generic error', () => {
            const dto = { ...sampleDto, presentationScore: 80 };
            vi.spyOn(participationService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500, error: { errorKey: 'other' } })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.addGradedPresentation(dto);

            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('should not call update when gradedPresentationEnabled is false', () => {
            component.gradeStepsDTO.set({ presentationsNumber: 0, gradeSteps: [], gradeType: undefined as any, title: '', plagiarismGrade: '', noParticipationGrade: '' });
            const updateStub = vi.spyOn(participationService, 'update');

            component.addGradedPresentation({ ...sampleDto, presentationScore: 50 });

            expect(updateStub).not.toHaveBeenCalled();
        });
    });

    describe('Remove presentation', () => {
        it('should call update with undefined presentationScore and reload (basic enabled)', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course: { id: 10, presentationScore: 1 },
            });
            const updateStub = vi.spyOn(participationService, 'update').mockReturnValue(of(new HttpResponse({ body: {} as any })));
            const searchStub = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            component.onDataRequest({ pageIndex: 0, pageSize: 50 });
            searchStub.mockClear();

            component.removePresentation(sampleDto);

            expect(updateStub).toHaveBeenCalledOnce();
            const passedParticipation = updateStub.mock.calls[0][1];
            expect(passedParticipation.presentationScore).toBeUndefined();
            expect(searchStub).toHaveBeenCalledOnce();
        });

        it('should show error alert when removePresentation fails', () => {
            component.exercise.set({
                ...exercise,
                isAtLeastTutor: true,
                presentationScoreEnabled: true,
                course: { id: 10, presentationScore: 1 },
            });
            vi.spyOn(participationService, 'update').mockReturnValue(throwError(() => new HttpErrorResponse({ status: 500 })));
            const errorSpy = vi.spyOn(alertService, 'error');

            component.removePresentation(sampleDto);

            expect(errorSpy).toHaveBeenCalledOnce();
        });

        it('should not call update when neither basic nor graded presentation is enabled', () => {
            component.exercise.set({ ...exercise, presentationScoreEnabled: false });
            const updateStub = vi.spyOn(participationService, 'update');

            component.removePresentation(sampleDto);

            expect(updateStub).not.toHaveBeenCalled();
        });
    });

    describe('Exports', () => {
        it.each([false, true])('should offer exports only to instructors (instructor: %s)', async (isAtLeastInstructor) => {
            vi.spyOn(exerciseService, 'find').mockReturnValue(of(new HttpResponse({ body: { ...exercise, isAtLeastInstructor } })));
            vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
            componentFixture.detectChanges();
            await componentFixture.whenStable();
            const view = TestBed.inject(CourseTitleBarService).actionsTemplate()!.createEmbeddedView({});
            view.detectChanges();
            const container = document.createElement('div');
            view.rootNodes.forEach((node) => container.append(node));

            expect(!!container.querySelector('button[tumAetUiButton][aria-label="entity.action.export"]')).toBe(isAtLeastInstructor);
            view.destroy();
        });

        it('should export names correctly for individual students', () => {
            const exportDto: ParticipationNameExportDTO = { participantName: 'participantName', participantIdentifier: 'login1' };
            vi.spyOn(participationService, 'getParticipationNamesForExport').mockReturnValue(of([exportDto]));
            const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

            component.exportNames();

            expect(resultServiceStub).toHaveBeenCalledExactlyOnceWith(['participantName'], 'results-names.csv');
        });

        it('should export names with team students format', () => {
            const exportDto: ParticipationNameExportDTO = { participantName: 'Team A', participantIdentifier: 'team-a', teamStudentNames: ['Alice', 'Bob'] };
            vi.spyOn(participationService, 'getParticipationNamesForExport').mockReturnValue(of([exportDto]));
            const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

            component.exportNames();

            const rows: string[] = resultServiceStub.mock.calls[0][0];
            expect(rows[0]).toBe('Team Name,Team Short Name,Students');
            expect(rows[1]).toContain('Team A');
            expect(rows[1]).toContain('Alice');
        });

        it('should not export when participant list is empty', () => {
            vi.spyOn(participationService, 'getParticipationNamesForExport').mockReturnValue(of([]));
            const resultServiceStub = vi.spyOn(resultService, 'triggerDownloadCSV');

            component.exportNames();

            expect(resultServiceStub).not.toHaveBeenCalled();
        });
    });

    describe('getBuildPlanUrl', () => {
        const programmingExercise = { ...exercise, type: ExerciseType.PROGRAMMING, projectKey: 'key' } as ProgrammingExercise;

        it('should construct build plan URL from template', () => {
            component.exercise.set(programmingExercise);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({ buildPlanURLTemplate: 'https://example.com/job/{projectKey}/job/{buildPlanId}' } as ProfileInfo);

            expect(component.getBuildPlanUrl({ ...sampleDto, buildPlanId: '1' })).toBe('https://example.com/job/key/job/1');
        });

        it('should return undefined when no template available', () => {
            component.exercise.set(programmingExercise);
            vi.spyOn(profileService, 'getProfileInfo').mockReturnValue({} as ProfileInfo);

            expect(component.getBuildPlanUrl({ ...sampleDto, buildPlanId: '1' })).toBeUndefined();
        });
    });

    describe('Results', () => {
        it('should return undefined when dto has no resultId', () => {
            expect(component.toResult(sampleDto)).toBeUndefined();
        });

        it('should build a Result from dto fields', () => {
            const result = component.toResult({ ...sampleDto, resultId: 42, score: 75, successful: true, assessmentType: AssessmentType.AUTOMATIC });

            expect(result).toMatchObject({ id: 42, score: 75, successful: true, assessmentType: AssessmentType.AUTOMATIC });
        });

        it('should add one result per correction round besides the newest one', () => {
            const participation = component.toParticipation({
                ...sampleDto,
                submissionId: 20,
                resultId: 31,
                correctionRoundResults: [
                    { resultId: 30, correctionRound: 0, assessmentType: AssessmentType.MANUAL },
                    { resultId: 31, correctionRound: 1, assessmentType: AssessmentType.MANUAL },
                ],
            });

            const results = participation.submissions![0].results!;
            expect(results.map((result) => result.id)).toEqual([31, 30]);
            expect(results[0].correctionRound).toBe(1);
        });

        it('should build a programming participation with a programming submission', () => {
            component.exercise.set({ ...exercise, type: ExerciseType.PROGRAMMING });

            const participation = component.toParticipation({ ...sampleDto, participationId: 10, submissionId: 20, resultId: 30, buildFailed: true });

            expect(participation.id).toBe(10);
            expect(participation.type).toBe(ParticipationType.PROGRAMMING);
            expect(participation.submissions![0].id).toBe(20);
            expect(participation.submissions![0].results![0].id).toBe(30);
            expect((participation.submissions![0] as any).submissionExerciseType).toBe('programming');
            expect((participation.submissions![0] as any).buildFailed).toBe(true);
        });

        it('should build a plain submission without programming fields for a non-programming exercise', () => {
            const participation = component.toParticipation({ ...sampleDto, submissionId: 20, resultId: 30, buildFailed: true });

            expect(participation.type).toBe(ParticipationType.STUDENT);
            expect(participation.submissions![0].submissionExerciseType).toBeUndefined();
            expect('buildFailed' in participation.submissions![0]).toBe(false);
        });

        it('should build a participation without submissions when there is none', () => {
            expect(component.toParticipation(sampleDto).submissions).toHaveLength(0);
        });
    });
});
