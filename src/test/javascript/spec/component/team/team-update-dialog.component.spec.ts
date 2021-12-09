import * as ace from 'brace';
import { ComponentFixture, fakeAsync, flush, TestBed, tick, discardPeriodicTasks } from '@angular/core/testing';
import { DebugElement } from '@angular/core';
import * as chai from 'chai';
import sinonChai from 'sinon-chai';
import { ArtemisTestModule } from '../../test.module';
import { TeamUpdateDialogComponent } from 'app/exercises/shared/team/team-update-dialog/team-update-dialog.component';
import { By } from '@angular/platform-browser';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { mockEmptyTeam, mockExercise, mockNonTeamStudents, mockTeam, MockTeamService, mockTeamStudents } from '../../helpers/mocks/service/mock-team.service';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { EventManager } from 'app/core/util/event-manager.service';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { RemoveKeysPipe } from 'app/shared/pipes/remove-keys.pipe';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamOwnerSearchComponent } from 'app/exercises/shared/team/team-owner-search/team-owner-search.component';
import { TeamStudentSearchComponent } from 'app/exercises/shared/team/team-student-search/team-student-search.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamUpdateDialogComponent', () => {
    // needed to make sure ace is defined
    ace.acequire('ace/ext/modelist.js');
    let comp: TeamUpdateDialogComponent;
    let fixture: ComponentFixture<TeamUpdateDialogComponent>;
    let debugElement: DebugElement;
    let ngbActiveModal: NgbActiveModal;

    beforeEach(async () => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule],
            declarations: [
                TeamUpdateDialogComponent,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(AlertErrorComponent),
                MockComponent(AlertComponent),
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
        const closeButton = debugElement.query(By.css('button.btn-close'));
        expect(closeButton).to.exist;
        closeButton.nativeElement.click();
        expect(ngbActiveModal.dismiss).to.have.been.called;

        const cancelButton = debugElement.query(By.css('button.cancel'));
        expect(cancelButton).to.exist;
        cancelButton.nativeElement.click();
        expect(ngbActiveModal.dismiss).to.have.been.calledTwice;

        fixture.destroy();
        flush();
    }));

    it('Team Update Dialog can be used to create team', fakeAsync(() => {
        comp.team = mockEmptyTeam;
        comp.exercise = mockExercise;
        fixture.detectChanges();

        // Check that title is correct for creating a team
        const modalTitle = debugElement.query(By.css('.modal-title'));
        expect(modalTitle).to.exist;
        expect(modalTitle.nativeElement.textContent.trim()).to.equal(`artemisApp.team.createTeam.label(${mockExercise.title})`);

        // Check that a submit button exists
        const submitButton = debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).to.exist;

        // Check that all necessary elements are present
        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
            ignoreTeamSizeRecommendation: debugElement.query(By.css('#ignoreTeamSizeRecommendation')),
        };
        Object.values(inputs).forEach((input) => expect(input).to.exist);

        // Enter a team name and a team short name
        inputs.teamName.nativeElement.value = 'Team 1';
        inputs.teamShortName.nativeElement.value = 'team1';
        fixture.detectChanges();

        // Submit button still disabled since no students were added yet (number of students is less than the min recommended team size)
        expect(submitButton.nativeElement.disabled).to.be.true;

        // Try proceeding against recommended team size (forcing to create an empty team)
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = true;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).to.be.false;

        // Undo "proceeding against recommendation"
        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = false;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).to.be.true;

        // Add a student to the team
        const [firstStudent, ...otherStudents] = mockTeamStudents;
        comp.onAddStudent(firstStudent);
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).to.be.false;

        // Add the rest of the students to the team
        otherStudents.forEach((student) => comp.onAddStudent(student));
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).to.be.false;

        // Click on save
        debugElement.query(By.css('#teamUpdateDialogForm')).nativeElement.submit();
        fixture.detectChanges();
        fixture.whenStable().then(() => {
            // Check that saving worked and that modal was closed
            expect(comp.team).to.deep.equal(comp.pendingTeam);
            expect(comp.isSaving).to.be.false;
            expect(ngbActiveModal.close).to.have.been.called;
            fixture.destroy();
        });
        discardPeriodicTasks();
        flush();
    }));

    it('Team Update Dialog can be used to update team', fakeAsync(() => {
        comp.team = mockTeam;
        comp.exercise = mockExercise;
        fixture.detectChanges();
        tick();

        // Check that title is correct for updating a team
        const modalTitle = debugElement.query(By.css('.modal-title'));
        expect(modalTitle).to.exist;
        expect(modalTitle.nativeElement.textContent.trim()).to.equal(`artemisApp.team.updateTeam.label(${mockExercise.title})`);

        // Check that a submit button exists
        const submitButton = debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).to.exist;

        // Check that all necessary elements are present
        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
        };
        Object.values(inputs).forEach((input) => expect(input).to.exist);

        // Update the team name
        const updatedTeamName = 'Updated team name';
        inputs.teamName.nativeElement.focus();
        inputs.teamName.nativeElement.value = updatedTeamName;
        inputs.teamName.nativeElement.dispatchEvent(new Event('input'));
        tick();
        fixture.detectChanges();
        expect(comp.pendingTeam.name).to.equal(updatedTeamName);

        // Submit button should be enabled (since we just changed the team name)
        expect(submitButton.nativeElement.disabled).to.be.false;

        // Remove one of the existing team members
        const studentRemoveLink = debugElement.query(By.css('.jest-student-remove-link'));
        studentRemoveLink.nativeElement.dispatchEvent(new Event('click'));
        tick();
        fixture.detectChanges();
        expect(comp.pendingTeam.students).to.deep.equal(comp.team.students?.slice(1));

        // Add three new team members
        mockNonTeamStudents.forEach((student) => comp.onAddStudent(student));
        fixture.detectChanges();
        expect(comp.pendingTeam.students).to.deep.equal(comp.team.students?.slice(1).concat(mockNonTeamStudents));

        // Click on save
        debugElement.query(By.css('#teamUpdateDialogForm')).nativeElement.submit();
        fixture.detectChanges();

        // Check that saving worked and that modal was closed
        expect(comp.team).to.deep.equal(comp.pendingTeam);
        expect(comp.isSaving).to.be.false;
        expect(ngbActiveModal.close).to.have.been.called;

        fixture.destroy();
        flush();
    }));
});
