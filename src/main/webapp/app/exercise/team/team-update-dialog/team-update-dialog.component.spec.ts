import { afterEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { TeamUpdateDialogComponent } from 'app/exercise/team/team-update-dialog/team-update-dialog.component';
import { By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTeamService, mockEmptyTeam, mockExercise, mockNonTeamStudents, mockTeam, mockTeamStudents } from 'test/helpers/mocks/service/mock-team.service';
import { TeamService } from 'app/exercise/team/team.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { TeamOwnerSearchComponent } from 'app/exercise/team/team-owner-search/team-owner-search.component';
import { TeamStudentSearchComponent } from 'app/exercise/team/team-student-search/team-student-search.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('TeamUpdateDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamUpdateDialogComponent;
    let fixture: ComponentFixture<TeamUpdateDialogComponent>;
    let debugElement: DebugElement;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let dialogConfig: { data: { team: typeof mockEmptyTeam | typeof mockTeam; exercise: typeof mockExercise } };

    const setupComponent = async (team: typeof mockEmptyTeam | typeof mockTeam) => {
        dialogRefCloseSpy = vi.fn();
        dialogConfig = { data: { team, exercise: mockExercise } };

        await TestBed.configureTestingModule({
            imports: [
                FormsModule,
                FaIconComponent,
                TeamUpdateDialogComponent,
                MockComponent(HelpIconComponent),
                MockPipe(RemoveKeysPipe),
                MockComponent(TeamOwnerSearchComponent),
                MockComponent(TeamStudentSearchComponent),
                TranslateDirective,
            ],
            providers: [
                EventManager,
                { provide: TeamService, useClass: MockTeamService },
                LocalStorageService,
                SessionStorageService,
                { provide: DynamicDialogRef, useValue: { close: dialogRefCloseSpy } },
                { provide: DynamicDialogConfig, useValue: dialogConfig },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamUpdateDialogComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('Team Update Dialog can be canceled via cancel button', async () => {
        await setupComponent(mockEmptyTeam);
        fixture.detectChanges();
        await fixture.whenStable();

        const cancelButton = debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith(undefined);

        fixture.destroy();
    });

    it('Team Update Dialog can be used to create team', async () => {
        await setupComponent(mockEmptyTeam);
        fixture.detectChanges();
        await fixture.whenStable();

        // Check that a submit button exists
        const submitButton = debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).not.toBeNull();

        // Check that all necessary elements are present
        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
            ignoreTeamSizeRecommendation: debugElement.query(By.css('#ignoreTeamSizeRecommendation')),
        };
        Object.values(inputs).forEach((input) => expect(input).not.toBeNull());

        // Enter a team name and a team short name via component methods
        comp.pendingTeam.name = 'Team 1';
        comp.onTeamShortNameChanged('team1');

        // Submit button still disabled since no students were added yet (number of students is less than the min recommended team size)
        expect(comp.teamSizeViolationUnconfirmed).toBe(true);

        // Try proceeding against recommended team size (forcing to create an empty team)
        comp.ignoreTeamSizeRecommendation = true;
        expect(comp.teamSizeViolationUnconfirmed).toBe(false);

        // Undo "proceeding against recommendation"
        comp.ignoreTeamSizeRecommendation = false;
        expect(comp.teamSizeViolationUnconfirmed).toBe(true);

        // Add a student to the team
        const [firstStudent, ...otherStudents] = mockTeamStudents;
        comp.onAddStudent(firstStudent);
        expect(comp.teamSizeViolationUnconfirmed).toBe(false);

        // Add the rest of the students to the team
        otherStudents.forEach((student) => comp.onAddStudent(student));
        expect(comp.teamSizeViolationUnconfirmed).toBe(false);

        // Save via component method
        comp.save();
        await fixture.whenStable();

        // Check that saving worked and that the dialog was closed
        expect(comp.isSaving).toBe(false);
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();

        fixture.destroy();
    });

    it('Team Update Dialog can be used to update team', async () => {
        await setupComponent(mockTeam);
        fixture.detectChanges();
        await fixture.whenStable();

        // Check that submit button exists
        const submitButton = fixture.debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).not.toBeNull();

        // Update the team name and verify it propagates through onTeamNameChanged
        const updatedTeamName = 'Updated team name';
        comp.pendingTeam.name = updatedTeamName;
        comp.onTeamNameChanged(updatedTeamName);
        expect(comp.pendingTeam.name).toEqual(updatedTeamName);

        // Remove one of the existing team members
        const originalStudents = comp.team().students!;
        comp.onRemoveStudent(comp.pendingTeam.students![0]);
        expect(comp.pendingTeam.students).toEqual(originalStudents.slice(1));

        // Add three new team members
        mockNonTeamStudents.forEach((student) => comp.onAddStudent(student));
        expect(comp.pendingTeam.students).toEqual(originalStudents.slice(1).concat(mockNonTeamStudents));

        // Run save via component method
        comp.save();
        await fixture.whenStable();

        // Check that saving worked and that the dialog was closed
        expect(comp.isSaving).toBe(false);
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();

        fixture.destroy();
    });
});
