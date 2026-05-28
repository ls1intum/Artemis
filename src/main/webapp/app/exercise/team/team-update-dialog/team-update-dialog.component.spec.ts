import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Component, DebugElement, input, output } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { TeamOwnerSearchComponent } from 'app/exercise/team/team-owner-search/team-owner-search.component';
import { TeamStudentSearchComponent } from 'app/exercise/team/team-student-search/team-student-search.component';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Subject } from 'rxjs';
import { User } from 'app/account/user/user.model';
import { Course } from 'app/course/shared/entities/course.model';

@Component({
    selector: 'jhi-team-owner-search',
    template: '',
})
class MockTeamOwnerSearchComponent {
    readonly inputDisabled = input<boolean>(undefined!);
    readonly course = input<Course>(undefined!);
    readonly exercise = input<Exercise>(undefined!);
    readonly team = input<Team>(undefined!);

    readonly selectOwner = output<User>();
    readonly searching = output<boolean>();
    readonly searchQueryTooShort = output<boolean>();
    readonly searchFailed = output<boolean>();
    readonly searchNoResults = output<string | undefined>();
}

@Component({
    selector: 'jhi-team-student-search',
    template: '',
})
class MockTeamStudentSearchComponent {
    readonly course = input<Course>(undefined!);
    readonly exercise = input<Exercise>(undefined!);
    readonly team = input<Team>(undefined!);
    readonly studentsFromPendingTeam = input<User[]>([]);

    readonly selectStudent = output<User>();
    readonly searching = output<boolean>();
    readonly searchQueryTooShort = output<boolean>();
    readonly searchFailed = output<boolean>();
    readonly searchNoResults = output<string | undefined>();
}

describe('TeamUpdateDialogComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamUpdateDialogComponent;
    let fixture: ComponentFixture<TeamUpdateDialogComponent>;
    let debugElement: DebugElement;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: ReturnType<typeof vi.fn>;
    let dialogConfigData: { team?: Team; exercise?: Exercise };

    const createComponent = async (team: Team, exercise: Exercise = mockExercise) => {
        dialogConfigData.team = team;
        dialogConfigData.exercise = exercise;
        fixture = TestBed.createComponent(TeamUpdateDialogComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        fixture.detectChanges();
        await fixture.whenStable();
    };

    beforeEach(async () => {
        dialogConfigData = {};
        dialogRefCloseSpy = vi.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [FormsModule, FaIconComponent, TeamUpdateDialogComponent],
            providers: [
                EventManager,
                { provide: TeamService, useClass: MockTeamService },
                LocalStorageService,
                SessionStorageService,
                { provide: DynamicDialogRef, useValue: dialogRef },
                { provide: DynamicDialogConfig, useValue: { data: dialogConfigData } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        })
            .overrideComponent(TeamUpdateDialogComponent, {
                remove: { imports: [HelpIconComponent, RemoveKeysPipe, TeamOwnerSearchComponent, TeamStudentSearchComponent, TranslateDirective] },
                add: {
                    imports: [
                        MockPipe(ArtemisTranslatePipe),
                        MockComponent(HelpIconComponent),
                        MockPipe(RemoveKeysPipe),
                        MockTeamOwnerSearchComponent,
                        MockTeamStudentSearchComponent,
                        TranslateDirective,
                    ],
                },
            })
            .compileComponents();
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('Team Update Dialog can be closed and canceled', async () => {
        await createComponent(mockEmptyTeam);

        const closeButton = debugElement.query(By.css('button.btn-close'));
        expect(closeButton).not.toBeNull();
        closeButton.nativeElement.click();
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith(undefined);

        const cancelButton = debugElement.query(By.css('button.cancel'));
        expect(cancelButton).not.toBeNull();
        cancelButton.nativeElement.click();
        expect(dialogRefCloseSpy).toHaveBeenCalledTimes(2);
        expect(dialogRefCloseSpy).toHaveBeenNthCalledWith(2, undefined);
    });

    it('Team Update Dialog can be used to create team', async () => {
        await createComponent(mockEmptyTeam);

        const modalTitle = debugElement.query(By.css('.modal-title'));
        expect(modalTitle).not.toBeNull();
        expect(modalTitle.nativeElement.textContent.trim()).toBe(`artemisApp.team.createTeam.label(${mockExercise.title})`);

        const submitButton = debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).not.toBeNull();

        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
            ignoreTeamSizeRecommendation: debugElement.query(By.css('#ignoreTeamSizeRecommendation')),
        };
        Object.values(inputs).forEach((input) => expect(input).not.toBeNull());

        comp.pendingTeam.name = 'Team 1';
        comp.pendingTeam.shortName = 'team1';
        fixture.detectChanges();

        expect(submitButton.nativeElement.disabled).toBe(true);

        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = true;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        await fixture.whenStable();
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(false);

        inputs.ignoreTeamSizeRecommendation.nativeElement.checked = false;
        inputs.ignoreTeamSizeRecommendation.nativeElement.dispatchEvent(new Event('change'));
        await fixture.whenStable();
        fixture.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(true);

        const [firstStudent, ...otherStudents] = mockTeamStudents;
        comp.onAddStudent(firstStudent);
        fixture.changeDetectorRef.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(false);

        otherStudents.forEach((student) => comp.onAddStudent(student));
        fixture.changeDetectorRef.detectChanges();
        expect(submitButton.nativeElement.disabled).toBe(false);

        submitButton.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.team).toEqual(comp.pendingTeam);
        expect(comp.isSaving).toBe(false);
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
    });

    it('Team Update Dialog can be used to update team', async () => {
        await createComponent(mockTeam);

        const modalTitle = debugElement.query(By.css('.modal-title'));
        expect(modalTitle).not.toBeNull();
        expect(modalTitle.nativeElement.textContent.trim()).toBe(`artemisApp.team.updateTeam.label(${mockExercise.title})`);

        const submitButton = debugElement.query(By.css('button[type=submit]'));
        expect(submitButton).not.toBeNull();

        const inputs = {
            teamName: debugElement.query(By.css('#teamName')),
            teamShortName: debugElement.query(By.css('#teamShortName')),
            teamStudents: debugElement.query(By.css('#teamStudents')),
        };
        Object.values(inputs).forEach((input) => expect(input).not.toBeNull());

        const updatedTeamName = 'Updated team name';
        inputs.teamName.nativeElement.focus();
        inputs.teamName.nativeElement.value = updatedTeamName;
        inputs.teamName.nativeElement.dispatchEvent(new Event('input'));
        await fixture.whenStable();
        fixture.detectChanges();
        expect(comp.pendingTeam.name).toEqual(updatedTeamName);

        expect(submitButton.nativeElement.disabled).toBe(false);

        const studentRemoveLink = debugElement.query(By.css('.jest-student-remove-link'));
        studentRemoveLink.nativeElement.dispatchEvent(new Event('click'));
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();
        expect(comp.pendingTeam.students).toEqual(comp.team.students?.slice(1));

        mockNonTeamStudents.forEach((student) => comp.onAddStudent(student));
        fixture.changeDetectorRef.detectChanges();
        expect(comp.pendingTeam.students).toEqual(comp.team.students?.slice(1).concat(mockNonTeamStudents));

        submitButton.nativeElement.click();
        fixture.detectChanges();
        await fixture.whenStable();

        expect(comp.team).toEqual(comp.pendingTeam);
        expect(comp.isSaving).toBe(false);
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
    });
});
