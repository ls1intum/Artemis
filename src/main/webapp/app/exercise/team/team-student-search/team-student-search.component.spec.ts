import { HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { TranslateService } from '@ngx-translate/core';
import { User } from 'app/account/user/user.model';
import { TeamSearchUser } from 'app/exercise/shared/entities/team/team-search-user.model';
import { TeamStudentSearchComponent } from 'app/exercise/team/team-student-search/team-student-search.component';
import { TeamService } from 'app/exercise/team/team.service';
import { firstValueFrom, of, throwError } from 'rxjs';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { MockTeamService, mockExercise, mockNonTeamStudents, mockTeam, mockTeamSearchUsers, mockTeamStudents } from 'test/helpers/mocks/service/mock-team.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('TeamStudentSearchComponent', () => {
    setupTestBed({ zoneless: true });

    let component: TeamStudentSearchComponent;
    let fixture: ComponentFixture<TeamStudentSearchComponent>;
    let teamService: TeamService;

    beforeEach(async () => {
        vi.useFakeTimers();

        await TestBed.configureTestingModule({
            imports: [TeamStudentSearchComponent],
            providers: [
                { provide: TeamService, useClass: MockTeamService },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamStudentSearchComponent);
        component = fixture.componentInstance;
        teamService = TestBed.inject(TeamService);

        fixture.componentRef.setInput('course', mockExercise.course);
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.componentRef.setInput('team', mockTeam);
        fixture.detectChanges();
    });

    afterEach(() => {
        vi.restoreAllMocks();
        vi.clearAllTimers();
        vi.useRealTimers();
    });

    async function runSearch(query: string) {
        const resultPromise = firstValueFrom(component.onSearch(of(query)));
        await vi.advanceTimersByTimeAsync(200);
        const users = await resultPromise;
        await vi.advanceTimersByTimeAsync(0);
        return users;
    }

    it('should clear the input display value and emit the selected student', () => {
        const selectedStudent = { id: 42, login: 'ga42xyz', name: 'Ada Lovelace' } as User;
        const selectStudentSpy = vi.spyOn(component.selectStudent, 'emit');
        component.inputDisplayValue = 'Ada Lovelace (ga42xyz)';

        component.onAutocompleteSelect(selectedStudent);

        expect(component.inputDisplayValue).toBe('');
        expect(selectStudentSpy).toHaveBeenCalledOnce();
        expect(selectStudentSpy).toHaveBeenCalledWith(selectedStudent);
    });

    it('should use the current input display value as the search input formatter result', () => {
        component.inputDisplayValue = 'Grace Hopper (ga11abc)';

        expect(component.searchInputFormatter()).toBe('Grace Hopper (ga11abc)');
    });

    it('should format search results as name and login', () => {
        const student = { login: 'ga11abc', name: 'Grace Hopper' } as User;

        expect(component.searchResultFormatter(student)).toBe('Grace Hopper (ga11abc)');
    });

    it('should emit query-too-short and return an empty result for short queries', async () => {
        const searchQueryTooShortSpy = vi.spyOn(component.searchQueryTooShort, 'emit');
        const searchFailedSpy = vi.spyOn(component.searchFailed, 'emit');
        const searchNoResultsSpy = vi.spyOn(component.searchNoResults, 'emit');
        const searchingSpy = vi.spyOn(component.searching, 'emit');
        const teamServiceSpy = vi.spyOn(teamService, 'searchInCourseForExerciseTeam');

        const result = await runSearch('ab');

        expect(result).toEqual([]);
        expect(teamServiceSpy).not.toHaveBeenCalled();
        expect(searchQueryTooShortSpy).toHaveBeenNthCalledWith(1, false);
        expect(searchQueryTooShortSpy).toHaveBeenNthCalledWith(2, true);
        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(false);
        expect(searchNoResultsSpy).toHaveBeenCalledOnce();
        expect(searchNoResultsSpy).toHaveBeenCalledWith(undefined);
        expect(searchingSpy).toHaveBeenNthCalledWith(1, true);
        expect(searchingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should search in the course for matching exercise team students and return them', async () => {
        const users = mockTeamSearchUsers.slice(0, 2);
        const teamServiceSpy = vi.spyOn(teamService, 'searchInCourseForExerciseTeam').mockReturnValue(of(new HttpResponse({ body: users })));
        const searchQueryTooShortSpy = vi.spyOn(component.searchQueryTooShort, 'emit');
        const searchFailedSpy = vi.spyOn(component.searchFailed, 'emit');
        const searchNoResultsSpy = vi.spyOn(component.searchNoResults, 'emit');
        const searchingSpy = vi.spyOn(component.searching, 'emit');

        const result = await runSearch('ga12abc');

        expect(teamServiceSpy).toHaveBeenCalledOnce();
        expect(teamServiceSpy).toHaveBeenCalledWith(mockExercise.course, mockExercise, 'ga12abc');
        expect(result).toEqual(users);
        expect(searchQueryTooShortSpy).toHaveBeenCalledOnce();
        expect(searchQueryTooShortSpy).toHaveBeenCalledWith(false);
        expect(searchFailedSpy).toHaveBeenCalledOnce();
        expect(searchFailedSpy).toHaveBeenCalledWith(false);
        expect(searchNoResultsSpy).toHaveBeenCalledOnce();
        expect(searchNoResultsSpy).toHaveBeenCalledWith(undefined);
        expect(searchingSpy).toHaveBeenNthCalledWith(1, true);
        expect(searchingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should emit search-no-results when the search succeeds without matches', async () => {
        vi.spyOn(teamService, 'searchInCourseForExerciseTeam').mockReturnValue(of(new HttpResponse({ body: [] })));
        const searchNoResultsSpy = vi.spyOn(component.searchNoResults, 'emit');

        const result = await runSearch('nomatch');

        expect(result).toEqual([]);
        expect(searchNoResultsSpy).toHaveBeenNthCalledWith(1, undefined);
        expect(searchNoResultsSpy).toHaveBeenNthCalledWith(2, 'nomatch');
    });

    it('should emit search-failed and return an empty result when the service errors', async () => {
        vi.spyOn(teamService, 'searchInCourseForExerciseTeam').mockReturnValue(throwError(() => new Error('search failed')));
        const searchFailedSpy = vi.spyOn(component.searchFailed, 'emit');
        const searchNoResultsSpy = vi.spyOn(component.searchNoResults, 'emit');

        const result = await runSearch('error-case');

        expect(result).toEqual([]);
        expect(searchFailedSpy).toHaveBeenNthCalledWith(1, false);
        expect(searchFailedSpy).toHaveBeenNthCalledWith(2, true);
        expect(searchNoResultsSpy).toHaveBeenCalledOnce();
        expect(searchNoResultsSpy).toHaveBeenCalledWith(undefined);
    });

    it('should disable typeahead buttons for students already pending or assigned to another team', async () => {
        const pendingStudent = {
            id: mockTeamStudents[0].id,
            login: mockTeamStudents[0].login,
            name: mockTeamStudents[0].name,
            assignedTeamId: mockTeam.id,
        } as TeamSearchUser;
        const assignedToOtherTeamStudent = {
            id: mockNonTeamStudents[0].id,
            login: mockNonTeamStudents[0].login,
            name: mockNonTeamStudents[0].name,
            assignedTeamId: 999,
        } as TeamSearchUser;
        const sameTeamStudent = {
            id: mockTeamStudents[1].id,
            login: mockTeamStudents[1].login,
            name: mockTeamStudents[1].name,
            assignedTeamId: mockTeam.id,
        } as TeamSearchUser;
        const users = [pendingStudent, assignedToOtherTeamStudent, sameTeamStudent];
        const typeaheadButtons = [{ setAttribute: vi.fn() }, { setAttribute: vi.fn() }, { setAttribute: vi.fn() }];
        vi.spyOn(component as unknown as { readonly typeaheadButtons: typeof typeaheadButtons }, 'typeaheadButtons', 'get').mockReturnValue(typeaheadButtons);
        vi.spyOn(teamService, 'searchInCourseForExerciseTeam').mockReturnValue(of(new HttpResponse({ body: users })));
        fixture.componentRef.setInput('studentsFromPendingTeam', [pendingStudent as User]);

        const result = await runSearch('ga12abc');

        expect(result).toEqual(users);
        expect(typeaheadButtons[0].setAttribute).toHaveBeenCalledOnce();
        expect(typeaheadButtons[0].setAttribute).toHaveBeenCalledWith('disabled', '');
        expect(typeaheadButtons[1].setAttribute).toHaveBeenCalledOnce();
        expect(typeaheadButtons[1].setAttribute).toHaveBeenCalledWith('disabled', '');
        expect(typeaheadButtons[2].setAttribute).not.toHaveBeenCalled();
    });
});
