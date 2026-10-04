import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement, NO_ERRORS_SCHEMA } from '@angular/core';
import { By } from '@angular/platform-browser';
import { TeamService } from 'app/exercise/team/team.service';
import { TeamsComponent } from 'app/exercise/team/teams/teams.component';
import { ExerciseService } from 'app/exercise/services/exercise.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { MockTeamService, mockTeams } from 'test/helpers/mocks/service/mock-team.service';
import { MockExerciseService } from 'test/helpers/mocks/service/mock-exercise.service';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { MockParticipationService } from 'test/helpers/mocks/service/mock-participation.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';

/**
 * The component under test uses signal-based state (`teams`, `exercise`, `isLoading`, ...).
 * The mocked services emit synchronously and we assert against signal values directly.
 * We replace the standalone imports of `TeamsComponent` with a minimal set so the test
 * doesn't pull in real child components (which would otherwise require `DialogService`,
 * `localStorage`, etc.).
 */
describe('TeamsComponent', () => {
    let comp: TeamsComponent;
    let fixture: ComponentFixture<TeamsComponent>;
    let debugElement: DebugElement;

    const route = {
        params: of({ exerciseId: 1 }),
        snapshot: { queryParamMap: convertToParamMap({}) },
    } as any as ActivatedRoute;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: ActivatedRoute, useValue: route },
                { provide: ParticipationService, useClass: MockParticipationService },
                { provide: ExerciseService, useClass: MockExerciseService },
                { provide: TeamService, useClass: MockTeamService },
                { provide: AccountService, useClass: MockAccountService },
                { provide: Router, useClass: MockRouter },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .overrideComponent(TeamsComponent, {
                set: {
                    imports: [],
                    schemas: [NO_ERRORS_SCHEMA],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(TeamsComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('Teams are loaded correctly', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        // Make sure that all 3 teams were received for exercise
        expect(comp.teams()).toHaveLength(mockTeams.length);

        // Check that jhi-table-view host element is rendered (via NO_ERRORS_SCHEMA)
        const datatable = debugElement.query(By.css('jhi-table-view'));
        expect(datatable).not.toBeNull();
    });

    it('should include the owner (tutor) name and login in the searchable fields', () => {
        expect(comp.tableOptions.globalFilterFields).toEqual(expect.arrayContaining(['owner.name', 'owner.login']));
    });

    it('should track the filtered team count reported by the table for the header', () => {
        expect(comp.filteredTeamsSize()).toBeUndefined();
        comp.onFilteredTeamsSizeChange(2);
        expect(comp.filteredTeamsSize()).toBe(2);
    });
});
