import { expect, vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { DebugElement } from '@angular/core';
import { TeamUpdateDialogComponent } from 'app/exercise/team/team-update-dialog/team-update-dialog.component';
import { By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { MockTeamService, mockEmptyTeam, mockExercise, mockNonTeamStudents, mockTeam, mockTeamStudents } from 'test/helpers/mocks/service/mock-team.service';
import { TeamService } from 'app/exercise/team/team.service';
import { EventManager } from 'app/shared/service/event-manager.service';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
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
    let ngbActiveModal: NgbActiveModal;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
                MockPipe(RemoveKeysPipe),
                MockComponent(TeamOwnerSearchComponent),
                MockComponent(TeamStudentSearchComponent),
                MockDirective(TranslateDirective),
                FormsModule,
                FaIconComponent,
            ],
            providers: [
                EventManager,
                { provide: TeamService, useClass: MockTeamService },
                LocalStorageService,
                SessionStorageService,
                MockProvider(NgbActiveModal),
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamUpdateDialogComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        ngbActiveModal = TestBed.inject(NgbActiveModal);
    });

    it('Team Update Dialog can be closed and canceled', () => {
        const dismissSpy = vi.spyOn(ngbActiveModal, 'dismiss');
        const closeButton = debugElement.query(By.css('button.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledOnce();

        const cancelButton = debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledTimes(2);

        fixture.destroy();
    });

    it('Team Update Dialog can be used to create team', async () => {
        const closeSpy = vi.spyOn(ngbActiveModal, 'close');
        comp.team = mockEmptyTeam;
        comp.exercise = mockExercise;
        fixture.changeDetectorRef.detectChanges();

        // Check that title is correct for creating a team
        const modalTitle = debugElement.query(By.css('.modal-title'));
        expect(modalTitle).not.toBeNull();
        expect(modalTitle.nativeElement.textContent.trim()).toBe(`artemisApp.team.createTeam.label(${mockExercise.title})`);

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

        // Enter a team name and a team short name
        inputs.teamName.nativeElement.value = 'Team 1';
        inputs.teamShortName.nativeElement.value = 'team1';
        fixture.changeDetectorRef.detectChanges();

        // Submit button still disabled since no students were added yet (number of students is less than the min recommended team size)
        expect(submitButton.nativeElement.disabled).toBe(true);

        // Try proceeding against recommended team size (forcing to create an empty team)
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = true;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.changeDetectorRef.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Undo "proceeding against recommendation"
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = false;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.changeDetectorRef.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(true);

        // Add a student to the team
        const [firstStudent, ...otherStudents] = mockTeamStudents;
        comp.onAddStudent(firstStudent);
        fixture.changeDetectorRef.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Add the rest of the students to the team
        otherStudents.forEach((student) => comp.onAddStudent(student));
        fixture.changeDetectorRef.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Click on save
        submitButton.nativeElement.click();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        // Check that saving worked and that modal was closed
        expect(comp.team).toEqual(comp.pendingTeam);
        expect(comp.isSaving).toBe(false);
        expect(closeSpy).toHaveBeenCalledOnce();
        fixture.destroy();
    });

    it('Team Update Dialog can be used to update team', async () => {
        const closeSpy = vi.spyOn(ngbActiveModal, 'close');
        comp.team = mockTeam;
        comp.exercise = mockExercise;
        fixture.changeDetectorRef.detectChanges();

        // Check that title is correct for updating a team
        const modalTitle = debugElement.query(By.css('.modal-title'));
        expect(modalTitle).not.toBeNull();
        expect(modalTitle.nativeElement.textContent.trim()).toBe(`artemisApp.team.updateTeam.label(${mockExercise.title})`);

        // Check that a submit button exists
        const submitButton = debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).not.toBeNull();

        // Check that all necessary elements are present
        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
        };
        Object.values(inputs).forEach((input) => expect(input).not.toBeNull());

        // Update the team name
        const updatedTeamName = 'Updated team name';
        comp.pendingTeam.name = updatedTeamName;
        expect(comp.pendingTeam.name).toEqual(updatedTeamName);

        // Submit button should be enabled (since we just changed the team name)
        expect(submitButton.nativeElement.disabled).toBe(false);

        // Remove one of the existing team members
        const studentRemoveLink = debugElement.query(By.css('.jest-student-remove-link'));
        studentRemoveLink.nativeElement.dispatchEvent(new Event('click'));
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();
        expect(comp.pendingTeam.students).toEqual(comp.team.students?.slice(1));

        // Add three new team members
        mockNonTeamStudents.forEach((student) => comp.onAddStudent(student));
        fixture.changeDetectorRef.detectChanges();
        expect(comp.pendingTeam.students).toEqual(comp.team.students?.slice(1).concat(mockNonTeamStudents));

        // Click on save
        submitButton.nativeElement.click();
        fixture.changeDetectorRef.detectChanges();
        await fixture.whenStable();

        // Check that saving worked and that modal was closed
        expect(comp.team).toEqual(comp.pendingTeam);
        expect(comp.isSaving).toBe(false);
        expect(closeSpy).toHaveBeenCalledOnce();

        fixture.destroy();
    });
});
