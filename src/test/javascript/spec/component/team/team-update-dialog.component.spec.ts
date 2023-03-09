import { DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, discardPeriodicTasks, fakeAsync, flush, tick } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { By } from '@angular/platform-browser';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { EventManager } from 'app/core/util/event-manager.service';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import * as ace from 'brace';
import { MockComponent, MockPipe } from 'ng-mocks';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { MockTeamService, mockEmptyTeam, mockExercise, mockNonTeamStudents, mockTeam, mockTeamStudents } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';

describe('TeamUpdateDialogComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
    let comp: TeamUpdateDialogComponent;
    let fixture: ComponentFixture<TeamUpdateDialogComponent>;
    let debugElement: DebugElement;
    let ngbActiveModal: NgbActiveModal;

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                TeamUpdateDialogComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(HelpIconComponent),
                MockPipe(RemoveKeysPipe),
                MockComponent(TeamOwnerSearchComponent),
                MockComponent(TeamStudentSearchComponent),
                TranslateDirective,
            ],
            providers: [
                EventManager,
                { provide: TeamService, useClass: MockTeamService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamUpdateDialogComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                ngbActiveModal = TestBed.inject(NgbActiveModal);
            });
    });

    it('Team Update Dialog can be closed and canceled', fakeAsync(() => {
        const dismissSpy = jest.spyOn(ngbActiveModal, 'dismiss');
        const closeButton = debugElement.query(By.css('button.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledOnce();

        const cancelButton = debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(dismissSpy).toHaveBeenCalledTimes(2);

        fixture.destroy();
        flush();
    }));

    it('Team Update Dialog can be used to create team', fakeAsync(() => {
        const closeSpy = jest.spyOn(ngbActiveModal, 'close');
        comp.team = mockEmptyTeam;
        comp.exercise = mockExercise;
        fixture.detectChanges();

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
        fixture.detectChanges();

        // Submit button still disabled since no students were added yet (number of students is less than the min recommended team size)
        expect(submitButton.nativeElement.disabled).toBeTrue();

        // Try proceeding against recommended team size (forcing to create an empty team)
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = true;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).toBeFalse();

        // Undo "proceeding against recommendation"
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = false;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).toBeTrue();

        // Add a student to the team
        const [firstStudent, ...otherStudents] = mockTeamStudents;
        comp.onAddStudent(firstStudent);
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).toBeFalse();

        // Add the rest of the students to the team
        otherStudents.forEach((student) => comp.onAddStudent(student));
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).toBeFalse();

        // Click on save
        debugElement.query(By.css('#teamUpdateDialogForm')).nativeElement.submit();
        fixture.detectChanges();
        fixture.whenStable().then(() => {
            // Check that saving worked and that modal was closed
            expect(comp.team).toEqual(comp.pendingTeam);
            expect(comp.isSaving).toBeFalse();
            expect(closeSpy).toHaveBeenCalledOnce();
            fixture.destroy();
        });
        discardPeriodicTasks();
        flush();
    }));

    it('Team Update Dialog can be used to update team', fakeAsync(() => {
        const closeSpy = jest.spyOn(ngbActiveModal, 'close');
        comp.team = mockTeam;
        comp.exercise = mockExercise;
        fixture.detectChanges();
        tick();

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
        inputs.teamName.nativeElement.focus();
        inputs.teamName.nativeElement.value = updatedTeamName;
        inputs.teamName.nativeElement.dispatchEvent(new Event('input'));
        tick();
        fixture.detectChanges();
        expect(comp.pendingTeam.name).toEqual(updatedTeamName);

        // Submit button should be enabled (since we just changed the team name)
        expect(submitButton.nativeElement.disabled).toBeFalse();

        // Remove one of the existing team members
        const studentRemoveLink = debugElement.query(By.css('.jest-student-remove-link'));
        studentRemoveLink.nativeElement.dispatchEvent(new Event('click'));
        tick();
        fixture.detectChanges();
        expect(comp.pendingTeam.students).toEqual(comp.team.students?.slice(1));

        // Add three new team members
        mockNonTeamStudents.forEach((student) => comp.onAddStudent(student));
        fixture.detectChanges();
        expect(comp.pendingTeam.students).toEqual(comp.team.students?.slice(1).concat(mockNonTeamStudents));

        // Click on save
        debugElement.query(By.css('#teamUpdateDialogForm')).nativeElement.submit();
        fixture.detectChanges();

        // Check that saving worked and that modal was closed
        expect(comp.team).toEqual(comp.pendingTeam);
        expect(comp.isSaving).toBeFalse();
        expect(closeSpy).toHaveBeenCalledOnce();

        fixture.destroy();
        flush();
    }));
});
