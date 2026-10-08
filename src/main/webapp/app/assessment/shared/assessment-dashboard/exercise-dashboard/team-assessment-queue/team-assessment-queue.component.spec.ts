import { beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';
import { TranslateService } from '@ngx-translate/core';
import { MockProvider } from 'ng-mocks';
import { of } from 'rxjs';
import dayjs from 'dayjs/esm';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { ParticipationManagementDTO } from 'app/exercise/participation/participation-management-dto.model';
import { AlertService } from 'app/foundation/service/alert.service';
import { SortingOrder } from 'app/foundation/pagination/pageable-table';
import { AssessmentType } from 'app/assessment/shared/entities/assessment-type.model';
import { TeamAssessmentQueueComponent, getTeamAssessmentState } from './team-assessment-queue.component';

describe('TeamAssessmentQueueComponent', () => {
    let fixture: ComponentFixture<TeamAssessmentQueueComponent>;
    let comp: TeamAssessmentQueueComponent;
    let participationService: ParticipationService;

    const exercise = { id: 7, type: ExerciseType.PROGRAMMING, teamMode: true, course: { id: 3 } } as Exercise;
    const teamRow = { participationId: 11, submissionCount: 1, submissionId: 12, teamId: 13, testRun: false } as ParticipationManagementDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [TeamAssessmentQueueComponent],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, MockProvider(ParticipationService), MockProvider(AlertService), provideRouter([])],
        });
        fixture = TestBed.createComponent(TeamAssessmentQueueComponent);
        comp = fixture.componentInstance;
        participationService = TestBed.inject(ParticipationService);
        fixture.componentRef.setInput('exercise', exercise);
    });

    it('requests only the teams of the tutor and shows them', () => {
        const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [teamRow], totalElements: 1 }));

        comp.loadPage({ pageIndex: 1, pageSize: 20, sort: { field: 'score', direction: 'desc' }, searchTerm: 'alpha' });

        expect(searchSpy).toHaveBeenCalledWith(7, {
            page: 1,
            pageSize: 20,
            sortingOrder: SortingOrder.DESCENDING,
            sortedColumn: 'score',
            searchTerm: 'alpha',
            ownTeams: true,
        });
        expect(comp.rows()).toEqual([teamRow]);
        expect(comp.totalRecords()).toBe(1);
        expect(comp.isLoading()).toBe(false);
    });

    it('renders a row per team with its assessment state', async () => {
        const searchSpy = vi
            .spyOn(participationService, 'searchParticipations')
            .mockReturnValue(of({ content: [{ ...teamRow, participantName: 'Team Alpha', participantIdentifier: 'alpha' }], totalElements: 1 }));

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(searchSpy).toHaveBeenCalledOnce();
        const table: HTMLElement = fixture.nativeElement.querySelector('[data-testid="team-assessment-queue-table"]');
        expect(table.textContent).toContain('Team Alpha');
        expect(table.querySelector('[data-testid="team-assessment-state"]')?.getAttribute('data-state')).toBe('notAssessed');
        expect(table.querySelector('[data-testid="manage-assessment-link"]')).not.toBeNull();
    });

    it('reloads the last requested page on refresh', () => {
        const searchSpy = vi.spyOn(participationService, 'searchParticipations').mockReturnValue(of({ content: [], totalElements: 0 }));
        comp.refresh();
        expect(searchSpy).not.toHaveBeenCalled();

        comp.loadPage({ pageIndex: 0, pageSize: 20 });
        comp.refresh();

        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenLastCalledWith(7, expect.objectContaining({ page: 0, sortedColumn: 'participantName', sortingOrder: SortingOrder.ASCENDING }));
    });

    it('links to the repository, the commit history and the team in the management view', () => {
        expect(comp.repositoryLink(teamRow)).toEqual(['/course-management', 3, 'programming-exercises', 7, 'repository', 'USER', 11]);
        expect(comp.commitHistoryLink(teamRow)).toEqual(['/course-management', 3, 'programming-exercises', 7, 'repository', 'USER', 11, 'commit-history']);
        expect(comp.teamLink(teamRow)).toEqual(['/course-management', 3, 'exercises', 7, 'teams', 13]);
    });

    it('names the members of a team', () => {
        expect(comp.teamMembers({ ...teamRow, teamStudents: [{ name: 'Ada' }, { login: 'grace' }] })).toBe('Ada, grace');
    });

    describe('getTeamAssessmentState', () => {
        const manual = (overrides: object) => ({ resultId: 1, correctionRound: 0, assessmentType: AssessmentType.SEMI_AUTOMATIC, ...overrides });

        it.each([
            ['noSubmission', { submissionId: undefined }],
            ['notAssessed', { correctionRoundResults: [] }],
            ['inProgress', { correctionRoundResults: [manual({})] }],
            ['assessed', { correctionRoundResults: [manual({ completionDate: dayjs() })] }],
            ['complaint', { correctionRoundResults: [manual({ completionDate: dayjs(), hasComplaint: true })] }],
            ['inProgress', { correctionRoundResults: [manual({ completionDate: dayjs(), hasComplaint: true }), manual({ resultId: 2, correctionRound: 1 })] }],
        ])('is %s', (state, overrides) => {
            expect(getTeamAssessmentState({ ...teamRow, ...overrides } as ParticipationManagementDTO)).toBe(state);
        });
    });
});
