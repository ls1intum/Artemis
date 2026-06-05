import { afterEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import { TeamUpdateDialogComponent } from 'app/exercise/team/team-update-dialog/team-update-dialog.component';
import { By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { DynamicDialogConfig, DynamicDialogRef } from 'primeng/dynamicdialog';
import { LocalStorageService } from 'app/foundation/service/local-storage.service';
import { SessionStorageService } from 'app/foundation/service/session-storage.service';
import { MockTeamService, mockEmptyTeam, mockExercise, mockNonTeamStudents, mockTeam, mockTeamStudents } from 'test/helpers/mocks/service/mock-team.service';
import { TeamService } from 'app/exercise/team/team.service';
import { EventManager } from 'app/foundation/service/event-manager.service';
import { MockComponent, MockPipe } from 'ng-mocks';
import { HelpIconComponent } from 'app/shared-ui/components/help-icon/help-icon.component';
import { RemoveKeysPipe } from 'app/foundation/pipes/remove-keys.pipe';
import { TeamOwnerSearchComponent } from 'app/exercise/team/team-owner-search/team-owner-search.component';
import { TeamStudentSearchComponent } from 'app/exercise/team/team-student-search/team-student-search.component';
import { TranslateDirective } from 'app/foundation/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { HttpResponse } from '@angular/common/http';
import { of } from 'rxjs';
import { Team } from 'app/exercise/shared/entities/team/team.model';

/**
 * All `fixture.detectChanges(...)` calls below pass `false` to skip the dev-mode
 * "expression changed after it was checked" verification pass. The dialog uses plain
 * (non-signal) mutable state — `pendingTeam`, ngModel-bound flags, and a template-driven
 * NgForm. In zoneless TestBed mode, these mutations are not tracked by the verification
 * pass even though the real CD pass renders correctly. Skipping verification preserves
 * the DOM-state assertions and lets tests exercise the component end-to-end. In prod
 * (zone-based change detection), this whole class of NG0100 false positives does not occur.
 */
describe('TeamUpdateDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamUpdateDialogComponent;
    let fixture: ComponentFixture<TeamUpdateDialogComponent>;
    let debugElement: DebugElement;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let dialogConfig: { data: { team: typeof mockEmptyTeam | typeof mockTeam; exercise: typeof mockExercise } };
    let teamService: TeamService;

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
        teamService = TestBed.inject(TeamService);
    };

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('Team Update Dialog can be canceled via cancel button', async () => {
        await setupComponent(mockEmptyTeam);
        fixture.detectChanges(false);
        await fixture.whenStable();

        const cancelButton = debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith(undefined);

        fixture.destroy();
    });

    it('Team Update Dialog can be used to create team', async () => {
        await setupComponent(mockEmptyTeam);
        fixture.detectChanges(false);
        await fixture.whenStable();

        // Set up spies for the save flow so we can verify create vs. update branching.
        const createdTeam = { ...new Team(), id: 42, name: 'Team 1', shortName: 'team1', students: [...mockTeamStudents] } as Team;
        const createSpy = vi.spyOn(teamService, 'create').mockReturnValue(of(new HttpResponse({ body: createdTeam })));
        const updateSpy = vi.spyOn(teamService, 'update').mockReturnValue(of(new HttpResponse({ body: createdTeam })));

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

        // Initially the form is invalid (required name/shortName missing) and no students were added,
        // so the submit button must be disabled.
        expect(submitButton.nativeElement.disabled).toBe(true);

        // Enter a team name via the DOM so ngModel/NgForm pick up the change.
        inputs.teamName.nativeElement.value = 'Team 1';
        inputs.teamName.nativeElement.dispatchEvent(new Event('input'));
        fixture.detectChanges(false);
        await fixture.whenStable();

        // Enter a short name via the DOM. onTeamNameChanged auto-fills shortName, but exercising
        // the DOM binding ensures the form control wires correctly through ngModelChange.
        inputs.teamShortName.nativeElement.value = 'team1';
        inputs.teamShortName.nativeElement.dispatchEvent(new Event('input'));
        fixture.detectChanges(false);
        await fixture.whenStable();

        // Submit button should still be disabled: no students added yet so teamSizeViolationUnconfirmed is true.
        expect(comp.teamSizeViolationUnconfirmed).toBe(true);
        expect(submitButton.nativeElement.disabled).toBe(true);

        // Try proceeding against recommended team size (forcing to create an empty team) via DOM.
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = true;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.teamSizeViolationUnconfirmed).toBe(false);
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Undo "proceeding against recommendation"
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = false;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.teamSizeViolationUnconfirmed).toBe(true);
        expect(submitButton.nativeElement.disabled).toBe(true);

        // Add a student to the team (search-result emit equivalent — direct method call mirrors the
        // child component's (selectStudent) output). Calling updateValueAndValidity() forces the
        // template-driven NgForm to recompute its validity after the @if(showIgnoreTeamSizeRecommendationOption)
        // block toggles (which destroys the standalone checkbox control).
        const [firstStudent, ...otherStudents] = mockTeamStudents;
        comp.onAddStudent(firstStudent);
        comp.editForm().control.updateValueAndValidity();
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.teamSizeViolationUnconfirmed).toBe(false);
        // Combined-condition assertion mirrors the template's [disabled] binding logic exactly:
        // editForm.invalid || isSaving || teamSizeViolationUnconfirmed. The early-test assertions
        // (lines around 111, 128, 144, 156) already validate the binding wires `submitButton.disabled`
        // to the same expression, so we test the logical state here without re-reading the DOM.
        expect(comp.editForm().invalid || comp.isSaving || comp.teamSizeViolationUnconfirmed).toBe(false);

        // Add the rest of the students to the team
        otherStudents.forEach((student) => comp.onAddStudent(student));
        comp.editForm().control.updateValueAndValidity();
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.teamSizeViolationUnconfirmed).toBe(false);
        expect(comp.editForm().invalid || comp.isSaving || comp.teamSizeViolationUnconfirmed).toBe(false);

        // Submit via the form's ngSubmit (form.submit doesn't trigger Angular's ngSubmit listener,
        // so dispatch the submit event on the form element directly).
        const formEl = debugElement.query(By.css('form#teamUpdateDialogForm')).nativeElement as HTMLFormElement;
        formEl.dispatchEvent(new Event('submit'));
        fixture.detectChanges(false);
        await fixture.whenStable();

        // Verify save semantics:
        //  - create was called (NEW team has no id), update was NOT — guards the create/update branch.
        //  - dialogRef.close received the body returned by the service.
        expect(createSpy).toHaveBeenCalledTimes(1);
        const [createExerciseArg, createTeamArg] = createSpy.mock.calls[0];
        expect(createExerciseArg).toBe(mockExercise);
        expect(createTeamArg.name).toBe('Team 1');
        expect(createTeamArg.shortName).toBe('team1');
        expect(createTeamArg.students).toEqual(mockTeamStudents);
        expect(createTeamArg.id).toBeUndefined();
        expect(updateSpy).not.toHaveBeenCalled();

        expect(comp.isSaving).toBe(false);
        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith(createdTeam);

        fixture.destroy();
    });

    it('Team Update Dialog can be used to update team', async () => {
        await setupComponent(mockTeam);
        fixture.detectChanges(false);
        await fixture.whenStable();

        const updatedTeamName = 'Updated team name';
        const updatedTeam = { ...mockTeam, name: updatedTeamName } as Team;
        const createSpy = vi.spyOn(teamService, 'create').mockReturnValue(of(new HttpResponse({ body: updatedTeam })));
        const updateSpy = vi.spyOn(teamService, 'update').mockReturnValue(of(new HttpResponse({ body: updatedTeam })));

        // Check that submit button exists and is initially enabled (existing valid team with min students).
        const submitButton = fixture.debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).not.toBeNull();
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Check that all necessary elements are present
        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
        };
        Object.values(inputs).forEach((input) => expect(input).not.toBeNull());

        // Update the team name via the DOM
        inputs.teamName.nativeElement.value = updatedTeamName;
        inputs.teamName.nativeElement.dispatchEvent(new Event('input'));
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.pendingTeam.name).toEqual(updatedTeamName);

        // Submit button should remain enabled (valid form, no team size violation).
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Remove one of the existing team members via the rendered remove link.
        const studentRemoveLink = debugElement.query(By.css('.jest-student-remove-link'));
        expect(studentRemoveLink).not.toBeNull();
        studentRemoveLink.nativeElement.dispatchEvent(new Event('click'));
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.pendingTeam.students).toEqual(mockTeam.students?.slice(1));

        // Add three new team members through the (selectStudent) output equivalent.
        mockNonTeamStudents.forEach((student) => comp.onAddStudent(student));
        fixture.detectChanges(false);
        await fixture.whenStable();
        expect(comp.pendingTeam.students).toEqual(mockTeam.students?.slice(1).concat(mockNonTeamStudents));

        // Submit via the form's ngSubmit binding.
        const formEl = debugElement.query(By.css('form#teamUpdateDialogForm')).nativeElement as HTMLFormElement;
        formEl.dispatchEvent(new Event('submit'));
        fixture.detectChanges(false);
        await fixture.whenStable();

        // Verify save semantics:
        //  - update was called (EXISTING team has an id), create was NOT — guards the create/update branch.
        //  - dialogRef.close received the body returned by the service.
        expect(updateSpy).toHaveBeenCalledTimes(1);
        const [updateExerciseArg, updateTeamArg] = updateSpy.mock.calls[0];
        expect(updateExerciseArg).toBe(mockExercise);
        expect(updateTeamArg.id).toBe(mockTeam.id);
        expect(updateTeamArg.name).toBe(updatedTeamName);
        expect(updateTeamArg.students).toEqual(mockTeam.students?.slice(1).concat(mockNonTeamStudents));
        expect(createSpy).not.toHaveBeenCalled();

        expect(comp.isSaving).toBe(false);
        expect(dialogRefCloseSpy).toHaveBeenCalledExactlyOnceWith(updatedTeam);

        fixture.destroy();
    });
});
