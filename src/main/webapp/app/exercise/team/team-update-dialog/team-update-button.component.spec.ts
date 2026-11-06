import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { TranslateService } from '@ngx-translate/core';
import { TumAetUiButtonDirective, TumAetUiDialogService } from '@tumaet/ui-angular';
import { Subject } from 'rxjs';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { mockExercise } from 'test/helpers/mocks/service/mock-team.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TeamUpdateButtonComponent } from './team-update-button.component';
import { TeamUpdateDialogComponent } from './team-update-dialog.component';

describe('TeamUpdateButtonComponent', () => {
    let fixture: ComponentFixture<TeamUpdateButtonComponent>;
    let dialogClosed: Subject<Team | undefined>;
    let openDialog: ReturnType<typeof vi.fn>;

    beforeEach(() => {
        dialogClosed = new Subject<Team | undefined>();
        openDialog = vi.fn().mockReturnValue({ onClose: dialogClosed });
        TestBed.configureTestingModule({
            imports: [TeamUpdateButtonComponent],
            providers: [
                { provide: TumAetUiDialogService, useValue: { open: openDialog } },
                { provide: TranslateService, useClass: MockTranslateService },
            ],
        });
        fixture = TestBed.createComponent(TeamUpdateButtonComponent);
        fixture.componentRef.setInput('exercise', mockExercise);
        fixture.detectChanges();
    });

    it('switches from blue Create to yellow Edit when an existing team is supplied', () => {
        const button = fixture.debugElement.query(By.directive(TumAetUiButtonDirective)).componentInstance as TumAetUiButtonDirective;
        expect(button.severity()).toBe('primary');
        expect(fixture.nativeElement.textContent).toContain('artemisApp.team.createTeam.label');

        fixture.componentRef.setInput('team', new Team());
        fixture.detectChanges();
        expect(button.severity()).toBe('warn');
        expect(fixture.nativeElement.textContent).toContain('artemisApp.team.updateTeam.label');
    });

    it.each([false, true])('opens the matching dialog and emits only a saved team (existing team: %s)', (editing) => {
        const existingTeam = editing ? new Team() : undefined;
        fixture.componentRef.setInput('team', existingTeam);
        fixture.detectChanges();
        const saved = vi.fn();
        fixture.componentInstance.save.subscribe(saved);
        const bubbledClick = vi.fn();
        fixture.nativeElement.addEventListener('click', bubbledClick);

        fixture.nativeElement.querySelector('button').click();

        expect(bubbledClick).not.toHaveBeenCalled();
        expect(openDialog).toHaveBeenCalledOnce();
        const [component, config] = openDialog.mock.calls[0];
        expect(component).toBe(TeamUpdateDialogComponent);
        expect(config.header).toBe(`${editing ? 'artemisApp.team.updateTeam.label' : 'artemisApp.team.createTeam.label'} (${mockExercise.title})`);
        expect(config.data.exercise).toBe(mockExercise);
        expect(config.data.team).toBeInstanceOf(Team);
        if (editing) {
            expect(config.data.team).toBe(existingTeam);
        }
        expect(config).toMatchObject({ size: 'large', closable: true, closeOnEscape: true, dismissableMask: false });

        dialogClosed.next(undefined);
        expect(saved).not.toHaveBeenCalled();
        const savedTeam = new Team();
        dialogClosed.next(savedTeam);
        expect(saved).toHaveBeenCalledExactlyOnceWith(savedTeam);
    });

    it('omits the exercise title when it is absent', () => {
        fixture.componentRef.setInput('exercise', { ...mockExercise, title: undefined });
        fixture.detectChanges();
        fixture.nativeElement.querySelector('button').click();
        expect(openDialog.mock.calls[0][1].header).toBe('artemisApp.team.createTeam.label');
    });
});
