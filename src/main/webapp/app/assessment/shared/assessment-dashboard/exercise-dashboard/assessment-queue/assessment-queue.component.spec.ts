import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { Subject, of } from 'rxjs';
import { SubmissionService } from 'app/exercise/submission/submission.service';
import { HttpResponse } from '@angular/common/http';
import { Submission } from 'app/exercise/shared/entities/submission/submission.model';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { AssessmentQueueComponent, getAssessmentState } from './assessment-queue.component';

describe('AssessmentQueueComponent', () => {
    let fixture: ComponentFixture<AssessmentQueueComponent>;
    let comp: AssessmentQueueComponent;
    let participationService: ParticipationService;

    const exercise = { id: 7, type: ExerciseType.PROGRAMMING, teamMode: true, course: { id: 3 } } as Exercise;
    const teamRow = { participationId: 11, submissionCount: 1, submissionId: 12, teamId: 13, testRun: false, assessableCorrectionRounds: [0] } as ParticipationManagementDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [AssessmentQueueComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ParticipationService),
                MockProvider(AlertService),
                MockProvider(SubmissionService),
                provideRouter([]),
            ],
        });
        fixture = TestBed.createComponent(AssessmentQueueComponent);
        comp = fixture.componentInstance;
        participationService = TestBed.inject(ParticipationService);
        fixture.componentRef.setInput('exercise', exercise);
    });

    it('requests only the teams of the tutor and shows them', () => {
        const searchSpy = vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(of({ content: [teamRow], totalElements: 1 }));

        comp.loadPage({ pageIndex: 1, pageSize: 20, sort: { field: 'score', direction: 'desc' }, searchTerm: 'alpha' });

        expect(searchSpy).toHaveBeenCalledWith(7, {
            page: 1,
            pageSize: 20,
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: 'score',
            searchTerm: 'alpha',
            ownTeams: true,
            filterProp: 'NoPracticeMode',
        });
        expect(comp.rows()).toEqual([teamRow]);
        expect(comp.totalRecords()).toBe(1);
        expect(comp.isLoading()).toBe(false);
    });

    it('ignores stale responses, completions and errors while a newer request is loading', () => {
        const first = new Subject<{ content: ParticipationManagementDTO[]; totalElements: number }>();
        const second = new Subject<{ content: ParticipationManagementDTO[]; totalElements: number }>();
        const third = new Subject<{ content: ParticipationManagementDTO[]; totalElements: number }>();
        vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValueOnce(first).mockReturnValueOnce(second).mockReturnValueOnce(third);
        const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        comp.loadPage({ pageIndex: 0, pageSize: 20 });
        comp.loadPage({ pageIndex: 1, pageSize: 20 });
        first.next({ content: [teamRow], totalElements: 99 });
        first.complete();
        expect(comp.rows()).toEqual([]);
        expect(comp.totalRecords()).toBe(0);
        expect(comp.isLoading()).toBe(true);
        comp.loadPage({ pageIndex: 2, pageSize: 20 });
        second.error({ status: 403, message: 'stale failure' });
        expect(alertSpy).not.toHaveBeenCalled();
        expect(comp.isLoading()).toBe(true);
        third.next({ content: [teamRow], totalElements: 1 });
        third.complete();
        expect(comp.rows()).toEqual([teamRow]);
        expect(comp.totalRecords()).toBe(1);
        expect(comp.isLoading()).toBe(false);
    });

    it('reports errors from the latest request and clears its loading state', () => {
        const response = new Subject<{ content: ParticipationManagementDTO[]; totalElements: number }>();
        vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(response);
        const alertSpy = vi.spyOn(TestBed.inject(AlertService), 'error');
        comp.loadPage({ pageIndex: 0, pageSize: 20 });
        response.error({ status: 403, message: 'failed' });
        expect(alertSpy).toHaveBeenCalled();
        expect(comp.isLoading()).toBe(false);
    });

    it.each([
        [false, false, false],
        [true, false, true],
        [false, true, true],
        [undefined, false, false],
    ])('shows draft actions for owner %s and instructor %s: %s', async (ownedByCurrentUser, isAtLeastInstructor, visible) => {
        fixture.componentRef.setInput('exercise', { ...exercise, type: ExerciseType.TEXT, teamMode: false, isAtLeastInstructor });
        vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(
            of({
                content: [
                    {
                        ...teamRow,
                        correctionRoundResults: [{ resultId: 21, correctionRound: 0, assessmentType: AssessmentType.MANUAL, ownedByCurrentUser }],
                    },
                ],
                totalElements: 1,
            }),
        );
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(!!fixture.nativeElement.querySelector('[data-assessment-action="continue"]')).toBe(visible);
        expect(!!fixture.nativeElement.querySelector('[data-testid="manage-assessment-cancel"]')).toBe(visible);
    });

    it('renders a row per team with its assessment state', async () => {
        const searchSpy = vi
            .spyOn(participationService, 'searchAssessmentQueue')
            .mockReturnValue(of({ content: [{ ...teamRow, participantName: 'Team Alpha', participantIdentifier: 'alpha' }], totalElements: 1 }));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(searchSpy).toHaveBeenCalledOnce();
        const table: HTMLElement = fixture.nativeElement.querySelector('[data-testid="assessment-queue-table"]');
        expect(table.textContent).toContain('Team Alpha');
        expect(table.querySelector('[data-testid="assessment-state"]')?.getAttribute('data-state')).toBe('notAssessed');
        expect(table.querySelector('[data-testid="manage-assessment-link"]')?.getAttribute('href')).toBe(
            '/course-management/3/programming-exercises/7/submissions/12/assessment?correction-round=0',
        );
    });

    it.each([ExerciseType.PROGRAMMING, ExerciseType.TEXT, ExerciseType.MODELING, ExerciseType.FILE_UPLOAD])(
        'uses student wording and assessment links for %s exercises',
        async (type) => {
            fixture.componentRef.setInput('exercise', { ...exercise, type, teamMode: false, isAtLeastInstructor: true });
            vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(
                of({ content: [{ ...teamRow, teamId: undefined, participantName: 'Ada', participantIdentifier: 'ada' }], totalElements: 1 }),
            );
            fixture.detectChanges();
            await fixture.whenStable();
            fixture.detectChanges();
            expect(comp.columns()[0].headerKey).toBe('artemisApp.exerciseAssessmentDashboard.students.participant');
            expect(comp.translationKey()).toBe('artemisApp.exerciseAssessmentDashboard.students');
            expect(fixture.nativeElement.querySelector('[data-testid="assessment-student-id"]').textContent.trim()).toBe('11');
            expect(fixture.nativeElement.textContent).not.toContain('Ada');
            expect(fixture.nativeElement.querySelector('code')).toBeNull();
            expect(fixture.nativeElement.querySelector('[data-testid="manage-assessment-link"]').getAttribute('href')).toBe(
                `/course-management/3/${type}-exercises/7/submissions/12/assessment?correction-round=0`,
            );
            expect(fixture.nativeElement.querySelector('[data-testid="assessment-more-actions"]') !== null).toBe(type === ExerciseType.PROGRAMMING);
        },
    );

    it.each([false, true])('shows only IDs and avoids identity queries, including instructor access: %s', async (isAtLeastInstructor) => {
        fixture.componentRef.setInput('exercise', { ...exercise, teamMode: false, isAtLeastInstructor });
        const searchSpy = vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(
            of({
                content: [
                    {
                        ...teamRow,
                        teamId: undefined,
                        participantName: 'Ada Lovelace',
                        participantIdentifier: 'ada-login',
                        teamStudents: [{ name: 'Ada Lovelace', login: 'ada-login' }],
                    },
                ],
                totalElements: 1,
            }),
        );
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(searchSpy).toHaveBeenCalledWith(7, expect.objectContaining({ sortedColumn: 'participationId', searchTerm: '', ownTeams: false }));
        expect(fixture.nativeElement.querySelector('input[type="search"]')).toBeNull();
        expect(fixture.nativeElement.querySelector('[data-testid="assessment-student-id"]').textContent.trim()).toBe('11');
        expect(fixture.nativeElement.textContent).not.toContain('Ada Lovelace');
        expect(fixture.nativeElement.textContent).not.toContain('ada-login');
        expect(fixture.nativeElement.querySelector('code')).toBeNull();
        comp.loadPage({ pageIndex: 0, pageSize: 20, searchTerm: 'Ada' });
        expect(searchSpy).toHaveBeenLastCalledWith(7, expect.objectContaining({ searchTerm: '' }));
    });

    it('does not render a dead assessment button when only the submission count is available', async () => {
        vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(of({ content: [{ ...teamRow, submissionId: undefined }], totalElements: 1 }));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="manage-assessment-link"]')).toBeNull();
    });

    it('hides new assessment actions when the server omits an empty eligibility list', async () => {
        vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(of({ content: [{ ...teamRow, assessableCorrectionRounds: undefined }], totalElements: 1 }));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="manage-assessment-link"]')).toBeNull();
    });

    it('keeps new test-run assessments available without queue eligibility metadata', async () => {
        fixture.componentRef.setInput('isTestRun', true);
        vi.spyOn(TestBed.inject(SubmissionService), 'getTestRunSubmissionsForExercise').mockReturnValue(
            of(new HttpResponse({ body: [{ id: 12, participation: { id: 11 }, results: [] } as Submission] })),
        );
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(fixture.nativeElement.querySelector('[data-testid="manage-assessment-link"]').getAttribute('href')).toContain('testRun=true');
    });

    it('loads only the current user’s test-run submissions through the test-run endpoint', () => {
        fixture.componentRef.setInput('isTestRun', true);
        const testRunSpy = vi.spyOn(TestBed.inject(SubmissionService), 'getTestRunSubmissionsForExercise').mockReturnValue(
            of(
                new HttpResponse({
                    body: [{ id: 12, participation: { id: 11 }, results: [{ id: 21, correctionRound: 0, assessmentType: AssessmentType.MANUAL }] } as Submission],
                }),
            ),
        );
        const searchSpy = vi.spyOn(participationService, 'searchAssessmentQueue');
        comp.loadPage({ pageIndex: 0, pageSize: 20 });
        expect(testRunSpy).toHaveBeenCalledWith(7);
        expect(searchSpy).not.toHaveBeenCalled();
        expect(comp.rows()).toEqual([
            expect.objectContaining({
                submissionId: 12,
                participationId: 11,
                testRun: true,
                correctionRoundResults: [expect.objectContaining({ resultId: 21, correctionRound: 0 })],
            }),
        ]);
    });

    it('reloads the last requested page on refresh', () => {
        const searchSpy = vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(of({ content: [], totalElements: 0 }));
        comp.refresh();
        expect(searchSpy).not.toHaveBeenCalled();

        comp.loadPage({ pageIndex: 0, pageSize: 20 });
        comp.refresh();

        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenLastCalledWith(7, expect.objectContaining({ page: 0, sortedColumn: 'participantName', sortingOrder: SortingOrder.ASCENDING }));
    });

    it('links to the repository, the commit history and the team in the management view', () => {
        expect(comp.repositoryLink(teamRow)).toEqual(['/course-management', '3', 'programming-exercises', '7', 'repository', 'USER', '11']);
        expect(comp.commitHistoryLink(teamRow)).toEqual(['/course-management', '3', 'programming-exercises', '7', 'repository', 'USER', '11', 'commit-history']);
        expect(comp.teamLink(teamRow)).toEqual(['/course-management', 3, 'exercises', 7, 'teams', 13]);
    });

    it('preserves a complaint on the newest result for the assessment action', () => {
        const row = { ...teamRow, resultId: 21, assessmentType: AssessmentType.MANUAL, correctionRoundResults: [{ resultId: 21, correctionRound: 0, hasComplaint: true }] };
        expect(comp.toParticipation(row).submissions?.[0]?.results?.[0]).toEqual(expect.objectContaining({ id: 21, correctionRound: 0, hasComplaint: true }));
    });

    it('uses exam routes for repository and assessment links', async () => {
        fixture.componentRef.setInput('exercise', { ...exercise, exerciseGroup: { id: 5, exam: { id: 4, course: { id: 3 }, numberOfCorrectionRoundsInExam: 2 } } });
        vi.spyOn(participationService, 'searchAssessmentQueue').mockReturnValue(of({ content: [teamRow], totalElements: 1 }));
        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.repositoryLink(teamRow)).toEqual(['/course-management', '3', 'exams', '4', 'exercise-groups', '5', 'programming-exercises', '7', 'repository', 'USER', '11']);
        expect(fixture.nativeElement.querySelector('[data-testid="manage-assessment-link"]').getAttribute('href')).toBe(
            '/course-management/3/exams/4/exercise-groups/5/programming-exercises/7/submissions/12/assessment?correction-round=0',
        );
    });

    it('names the members of a team', () => {
        expect(comp.teamMembers({ ...teamRow, teamStudents: [{ name: 'Ada' }, { login: 'grace' }] })).toBe('Ada, grace');
    });

    describe('getAssessmentState', () => {
        const manual = (overrides: object) => ({ resultId: 1, correctionRound: 0, assessmentType: AssessmentType.SEMI_AUTOMATIC, ...overrides });

        it('requires all enabled rounds to be complete, preserving complaints and drafts', () => {
            const row = { ...teamRow, correctionRoundResults: [manual({ completionDate: dayjs() })] };
            expect(getAssessmentState(row, 1)).toBe('assessed');
            expect(getAssessmentState(row, 2)).toBe('notAssessed');
            fixture.componentRef.setInput('correctionRoundsEnabled', 2);
            expect(comp.state(row)).toBe('notAssessed');
            expect(getAssessmentState({ ...teamRow, correctionRoundResults: [manual({ hasComplaint: true, completionDate: dayjs() })] }, 2)).toBe('complaint');
            expect(getAssessmentState({ ...teamRow, correctionRoundResults: [manual({})] }, 2)).toBe('inProgress');
        });

        it.each([
            ['noSubmission', { submissionId: undefined }],
            ['notAssessed', { correctionRoundResults: [] }],
            ['inProgress', { correctionRoundResults: [manual({})] }],
            ['assessed', { correctionRoundResults: [manual({ completionDate: dayjs() })] }],
            ['complaint', { correctionRoundResults: [manual({ completionDate: dayjs(), hasComplaint: true })] }],
            ['inProgress', { correctionRoundResults: [manual({ completionDate: dayjs(), hasComplaint: true }), manual({ resultId: 2, correctionRound: 1 })] }],
        ])('is %s', (state, overrides) => {
            expect(getAssessmentState({ ...teamRow, ...overrides } as ParticipationManagementDTO)).toBe(state);
        });
    });
});
