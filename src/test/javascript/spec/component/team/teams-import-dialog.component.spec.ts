import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, TestBed, tick, waitForAsync } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { User } from 'app/core/user/user.model';
import { AlertService } from 'app/core/util/alert.service';
import { Exercise } from 'app/entities/exercise.model';
import { Team, TeamImportStrategyType } from 'app/entities/team.model';
import { TeamExerciseSearchComponent } from 'app/exercises/shared/team/team-exercise-search/team-exercise-search.component';
import { TeamStudentsListComponent } from 'app/exercises/shared/team/team-students-list/team-students-list.component';
import { TeamService } from 'app/exercises/shared/team/team.service';
import { TeamsImportDialogComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-dialog.component';
import { TeamsImportFromFileFormComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-from-file-form.component';
import { AlertErrorComponent } from 'app/shared/alert/alert-error.component';
import { AlertComponent } from 'app/shared/alert/alert.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import * as chai from 'chai';
import { flatMap } from 'lodash-es';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { restore, SinonSpy, SinonStub, spy, stub } from 'sinon';
import sinonChai from 'sinon-chai';
import { mockExercise, mockSourceExercise, mockSourceTeams, mockSourceTeamStudents, mockTeam, mockTeams, mockTeamStudents } from '../../helpers/mocks/service/mock-team.service';
import { ArtemisTestModule } from '../../test.module';
import { TranslateDirective } from 'app/shared/language/translate.directive';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsImportDialogComponent', () => {
    let comp: TeamsImportDialogComponent;
    let fixture: ComponentFixture<TeamsImportDialogComponent>;
    let ngbActiveModal: NgbActiveModal;
    let alertService: AlertService;
    let teamService: TeamService;

    const teams: Team[] = mockTeams;
    const logins = flatMap(mockTeams, (team) => team.students?.map((student) => student.login));
    const registrationNumbers = flatMap(mockTeams, (team) => team.students?.map((student) => student.visibleRegistrationNumber));
    const exercise: Exercise = mockExercise;

    function resetComponent() {
        comp.teams = teams;
        comp.exercise = exercise;
        comp.searchingExercises = false;
        comp.searchingExercisesFailed = false;
        comp.searchingExercisesNoResultsForQuery = undefined;
        comp.loadingSourceTeams = false;
        comp.loadingSourceTeamsFailed = false;
        comp.importStrategy = undefined;
        comp.isImporting = false;
        comp.showImportFromExercise = true;
        comp.teamShortNamesAlreadyExistingInExercise = [];
        comp.sourceTeamsFreeOfConflicts = [];
        comp.sourceTeams = undefined;
        comp.sourceExercise = undefined;
        comp.studentsAppearInMultipleTeams = false;
        comp.conflictingLoginsSet = new Set();
        comp.conflictingRegistrationNumbersSet = new Set();
    }

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [ArtemisTestModule, FormsModule],
                declarations: [
                    TeamsImportDialogComponent,
                    MockComponent(TeamsImportFromFileFormComponent),
                    MockDirective(DeleteButtonDirective),
                    MockDirective(TranslateDirective),
                    MockPipe(ArtemisTranslatePipe),
                    MockComponent(AlertComponent),
                    MockComponent(AlertErrorComponent),
                    MockComponent(TeamExerciseSearchComponent),
                    MockComponent(TeamStudentsListComponent),
                    MockComponent(HelpIconComponent),
                ],
                providers: [MockProvider(TeamService)],
            }).compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportDialogComponent);
        comp = fixture.componentInstance;
        ngbActiveModal = TestBed.inject(NgbActiveModal);
        alertService = TestBed.inject(AlertService);
        teamService = TestBed.inject(TeamService);
    });

    describe('OnInit', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('should compute potential conflicts based on existing teams', () => {
            const potentialConflictSpy: SinonSpy = spy(comp, 'computePotentialConflictsBasedOnExistingTeams');
            comp.ngOnInit();
            expect(potentialConflictSpy).to.have.been.called;
        });
    });

    describe('loadSourceTeams', () => {
        let teamServiceStub: SinonStub;
        let computeSourceStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            teamServiceStub = stub(teamService, 'findAllByExerciseId');
            computeSourceStub = stub(comp, 'computeSourceTeamsFreeOfConflicts');
            teamServiceStub.returns(of(new HttpResponse<Team[]>({ body: mockSourceTeams })));
        });

        afterEach(() => {
            restore();
        });

        it('should load teams of given exercise if find was successful', () => {
            const sourceExercise = mockSourceExercise;
            comp.sourceTeams = [];
            comp.loadSourceTeams(sourceExercise);
            expect(comp.loadingSourceTeams).to.equal(false);
            expect(comp.loadingSourceTeamsFailed).to.equal(false);
            expect(teamServiceStub).to.have.been.calledWithExactly(sourceExercise.id);
            expect(comp.sourceTeams).to.deep.equal(mockSourceTeams);
            expect(computeSourceStub).to.have.been.called;
        });
        it('should not load teams of given exercise if find failed', () => {
            teamServiceStub.returns(throwError({ status: 404 }));
            const sourceExercise = mockSourceExercise;
            comp.sourceTeams = [];
            comp.loadSourceTeams(sourceExercise);
            expect(comp.sourceTeams).to.equal(undefined);
            expect(comp.loadingSourceTeams).to.equal(false);
            expect(comp.loadingSourceTeamsFailed).to.equal(true);
            expect(teamServiceStub).to.have.been.calledWithExactly(sourceExercise.id);
            expect(computeSourceStub).to.not.have.been.called;
        });
    });

    describe('loadSourceTeams', () => {
        let loadSourceStub: SinonStub;
        let initImportStrategy: SinonStub;
        beforeEach(() => {
            resetComponent();
            loadSourceStub = stub(comp, 'loadSourceTeams');
            initImportStrategy = stub(comp, 'initImportStrategy');
        });

        afterEach(() => {
            restore();
        });

        it('should load selected exercise', () => {
            const sourceExercise = mockSourceExercise;
            comp.onSelectSourceExercise(sourceExercise);
            expect(comp.sourceExercise).to.deep.equal(sourceExercise);
            expect(initImportStrategy).to.have.been.called;
            expect(loadSourceStub).to.have.been.calledWithExactly(sourceExercise);
        });
    });

    describe('initImportStrategy', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should set import strategy to default if there no teams', () => {
            comp.teams = [];
            comp.initImportStrategy();
            expect(comp.importStrategy).to.equal(comp.defaultImportStrategy);
        });
        it('should set import strategy to undefined if there are teams', () => {
            comp.initImportStrategy();
            expect(comp.importStrategy).to.equal(undefined);
        });
    });

    describe('computePotentialConflictsBasedOnExistingTeams', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should fill existing arrays current team values', () => {
            comp.computePotentialConflictsBasedOnExistingTeams();
            const shortNames = teams.map((team) => team.shortName);
            expect(comp.teamShortNamesAlreadyExistingInExercise).to.deep.equal(shortNames);
            expect(comp.conflictingLoginsSet).to.deep.equal(new Set(logins));
            expect(comp.conflictingRegistrationNumbersSet).to.deep.equal(new Set(registrationNumbers));
        });
    });

    describe('computeSourceTeamsFreeOfConflicts', () => {
        let sourceFreeStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            sourceFreeStub = stub(comp, 'isSourceTeamFreeOfAnyConflicts');
            sourceFreeStub.returns(true);
            sourceFreeStub.withArgs(mockSourceTeams[1]).returns(false);
        });

        afterEach(() => {
            restore();
        });
        it('should filter source teams according to conflict', () => {
            comp.sourceTeams = mockSourceTeams;
            comp.computeSourceTeamsFreeOfConflicts();
            expect(comp.sourceTeamsFreeOfConflicts).to.deep.equal([mockSourceTeams[0], mockSourceTeams[2]]);
            expect(sourceFreeStub).to.have.been.callCount(mockSourceTeams.length);
        });
    });

    describe('isSourceTeamFreeOfAnyConflicts', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('returns false if short name is in already existing short names', () => {
            comp.teamShortNamesAlreadyExistingInExercise = [mockTeam.shortName!];
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(false);
        });

        it('returns true if short name is not in already existing short names', () => {
            comp.teamShortNamesAlreadyExistingInExercise = [];
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(true);
        });

        it('Import from exercise: returns false if one of the students login is in already existing students', () => {
            comp.conflictingLoginsSet = new Set([mockTeamStudents[0].login!]);
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(false);
        });

        it('Import from exercise: returns true if none of the students login is in already existing students', () => {
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(true);
        });

        it('Import from file: returns false if one of the students login is in already existing students', () => {
            comp.conflictingLoginsSet = new Set([mockTeamStudents[0].login!]);
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(false);
        });

        it('Import from exercise: returns true if one of the students registration number is in already existing students', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(true);
        });

        it('Import from file: returns false if one of the students registration number is in already existing students', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(false);
        });

        it('Import from exercise: returns true if one of the students registration number is in already other source teams', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            comp.studentsAppearInMultipleTeams = true;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(true);
        });

        it('Import from file: returns false if one of the students registration number is in already other source teams', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            comp.studentsAppearInMultipleTeams = true;
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(false);
        });

        it('Import from file: returns false if one of the students login is in already other source teams', () => {
            comp.conflictingLoginsSet = new Set([mockTeamStudents[0].login!]);
            comp.studentsAppearInMultipleTeams = true;
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(false);
        });

        it('Import from file: returns true if no student is in multiple teams', () => {
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).to.equal(true);
        });
    });

    describe('numberOfConflictFreeSourceTeams', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return length of source teams free of conflict', () => {
            expect(comp.numberOfConflictFreeSourceTeams).to.equal(0);
            comp.sourceTeamsFreeOfConflicts = mockTeams;
            expect(comp.numberOfConflictFreeSourceTeams).to.equal(mockTeams.length);
        });
    });

    describe('numberOfTeamsToBeDeleted', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return 0 if import strategy is CREATE_ONLY', () => {
            comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            expect(comp.numberOfTeamsToBeDeleted).to.equal(0);
        });
        it('should return length of teams if import strategy is PURGE_EXISTING', () => {
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
            expect(comp.numberOfTeamsToBeDeleted).to.equal(mockTeams.length);
        });
    });

    describe('numberOfTeamsToBeImported', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return conflict free teams number if import strategy is CREATE_ONLY', () => {
            comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            comp.sourceTeamsFreeOfConflicts = mockSourceTeams;
            expect(comp.numberOfTeamsToBeImported).to.equal(mockSourceTeams.length);
        });
        it('should return length of source teams if import strategy is PURGE_EXISTING', () => {
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
            expect(comp.numberOfTeamsToBeImported).to.equal(mockSourceTeams.length);
        });
    });

    describe('numberOfTeamsAfterImport', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return current teams + conflict free teams number if import strategy is CREATE_ONLY', () => {
            comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            comp.sourceTeamsFreeOfConflicts = mockSourceTeams;
            expect(comp.numberOfTeamsAfterImport).to.equal(mockSourceTeams.length + mockTeams.length);
        });
        it('should return length of source teams if import strategy is PURGE_EXISTING', () => {
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
            expect(comp.numberOfTeamsAfterImport).to.equal(mockSourceTeams.length);
        });
    });

    describe('showImportStrategyChoices', () => {
        beforeEach(() => {
            resetComponent();
            comp.sourceExercise = mockSourceExercise;
            comp.sourceTeams = mockSourceTeams;
        });
        it('Import from exercise: should return false if there is no sourceExercise', () => {
            comp.sourceExercise = undefined;
            expect(comp.showImportStrategyChoices).to.equal(false);
        });
        it('Import from exercise: should return true if there is a sourceExercise and source team', () => {
            expect(comp.showImportStrategyChoices).to.equal(true);
        });
        it('should return false if there is no source team', () => {
            comp.sourceTeams = [];
            expect(comp.showImportStrategyChoices).to.equal(false);
        });
        it('should return false if there is no existing team', () => {
            comp.teams = [];
            expect(comp.showImportStrategyChoices).to.equal(false);
        });
        it('Import from file: should return false if source teams undefined', () => {
            comp.sourceTeams = undefined;
            comp.showImportFromExercise = false;
            expect(comp.showImportStrategyChoices).to.equal(false);
        });
        it('Import from file: should return true if source exercise undefined', () => {
            comp.sourceExercise = undefined;
            comp.showImportFromExercise = false;
            expect(comp.showImportStrategyChoices).to.equal(true);
        });
    });

    describe('updateImportStrategy', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should set import strategy to given import strategy', () => {
            expect(comp.importStrategy).to.equal(undefined);
            comp.updateImportStrategy(TeamImportStrategyType.CREATE_ONLY);
            expect(comp.importStrategy).to.equal(TeamImportStrategyType.CREATE_ONLY);
            comp.updateImportStrategy(TeamImportStrategyType.PURGE_EXISTING);
            expect(comp.importStrategy).to.equal(TeamImportStrategyType.PURGE_EXISTING);
        });
    });

    describe('showImportPreviewNumbers', () => {
        describe('import from exercise', () => {
            beforeEach(() => {
                resetComponent();
                comp.sourceExercise = undefined;
                comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            });
            it('Import from exercise: should return false if there is no sourceExercise', () => {
                expect(comp.showImportPreviewNumbers).to.equal(false);
            });
            it('Import from exercise: should return true if there is a sourceExercise and source team', () => {
                comp.sourceExercise = mockSourceExercise;
                comp.sourceTeams = mockSourceTeams;
                expect(comp.showImportPreviewNumbers).to.equal(true);
            });
            it('should return false if there is no source team', () => {
                expect(comp.showImportPreviewNumbers).to.equal(false);
            });
            it('Import from exercise: should return false if there is no import strategy', () => {
                expect(comp.showImportPreviewNumbers).to.equal(false);
            });
        });
        describe('import from exercise', () => {
            beforeEach(() => {
                resetComponent();
                comp.sourceExercise = undefined;
                comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
                comp.showImportFromExercise = false;
            });
            it('Import from file: should return false if there is no import strategy', () => {
                expect(comp.showImportPreviewNumbers).to.equal(false);
            });
            it('Import from file: should return false if no students in multiple teams and no import strategy', () => {
                comp.importStrategy = undefined;
                expect(comp.showImportPreviewNumbers).to.equal(false);
            });
            it('Import from file: should return true if there are students appear in multiple teams and conflicting registration numbers', () => {
                comp.conflictingRegistrationNumbersSet = new Set(['1', '2']);
                comp.studentsAppearInMultipleTeams = true;
                comp.importStrategy = undefined;
                expect(comp.showImportPreviewNumbers).to.equal(true);
            });
            it('Import from file: should return true if there are students appear in multiple teams and conflicting logins', () => {
                comp.conflictingLoginsSet = new Set(['l1', 'l2']);
                comp.studentsAppearInMultipleTeams = true;
                comp.importStrategy = undefined;
                expect(comp.showImportPreviewNumbers).to.equal(true);
            });
        });
    });

    describe('isSubmitDisabled', () => {
        beforeEach(() => {
            resetComponent();
            comp.sourceExercise = mockSourceExercise;
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
        });
        it('should return false', () => {
            expect(comp.isSubmitDisabled).to.equal(false);
        });
        it('Import from exercise: should return true if importing', () => {
            comp.isImporting = true;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
        it('Import from exercise: should return true if it has source exercise', () => {
            comp.sourceExercise = undefined;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
        it('Import from exercise: should return true if it has source teams', () => {
            comp.sourceTeams = undefined;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
        it('Import from exercise: should return true if it has import strategy', () => {
            comp.importStrategy = undefined;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
        it('Import from file: should return false if importing', () => {
            comp.isImporting = true;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).to.equal(false);
        });
        it('Import from file: should return false if it has no source exercise', () => {
            comp.sourceExercise = undefined;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).to.equal(false);
        });
        it('Import from file: should return true if it has source teams', () => {
            comp.sourceTeams = undefined;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
        it('Import from file: should return true if it has import strategy', () => {
            comp.importStrategy = undefined;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
        it('Import from file: should return true if there same registration number is in two teams', () => {
            comp.conflictingRegistrationNumbersSet = new Set(['1', '2']);
            comp.studentsAppearInMultipleTeams = true;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).to.equal(true);
        });
    });

    describe('clear', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return false', () => {
            comp.clear();
            expect(ngbActiveModal.dismiss).to.have.been.calledWith('cancel');
        });
    });

    describe('purgeAndImportTeams', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return false', () => {
            const importTeamsStub = stub(comp, 'importTeams');
            comp.purgeAndImportTeams();
            expect(importTeamsStub).to.have.been.called;
            restore();
        });
    });

    describe('importTeams', () => {
        let importFromSourceExerciseStub: SinonStub;
        let importTeamsStub: SinonStub;
        let onSuccessStub: SinonStub;
        let onErrorStub: SinonStub;
        let fromExerciseResponse: HttpResponse<Team[]>;
        let fromFileResponse: HttpResponse<Team[]>;
        beforeEach(() => {
            resetComponent();
            fromExerciseResponse = new HttpResponse<Team[]>({ body: mockSourceTeams });
            importFromSourceExerciseStub = stub(teamService, 'importTeamsFromSourceExercise');
            importFromSourceExerciseStub.returns(of(fromExerciseResponse));
            fromFileResponse = new HttpResponse<Team[]>({ body: [...mockSourceTeams, mockTeam] });
            importTeamsStub = stub(teamService, 'importTeams');
            importTeamsStub.returns(of(fromFileResponse));
            onSuccessStub = stub(comp, 'onSaveSuccess');
            onErrorStub = stub(comp, 'onSaveError');
            comp.sourceExercise = mockSourceExercise;
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
        });
        afterEach(() => {
            restore();
        });
        it('should not call team service if submit disabled', () => {
            comp.importStrategy = undefined;
            comp.importTeams();
            expect(importFromSourceExerciseStub).to.not.have.been.called;
            expect(importTeamsStub).to.not.have.been.called;
            expect(onSuccessStub).to.not.have.been.called;
            expect(onErrorStub).to.not.have.been.called;
            expect(comp.isImporting).to.equal(false);
        });
        it('should call importTeamsFromSourceExercise if show import from exercise and call save success', () => {
            comp.importTeams();
            expect(importFromSourceExerciseStub).to.have.been.calledWithExactly(comp.exercise, comp.sourceExercise, comp.importStrategy);
            expect(importTeamsStub).to.not.have.been.called;
            expect(onSuccessStub).to.have.been.calledWithExactly(fromExerciseResponse);
            expect(onErrorStub).to.not.have.been.called;
            expect(comp.isImporting).to.equal(true);
        });
        it('should call importTeamsFromSourceExercise if show import from exercise and call save error on Error', () => {
            const error = { status: 404 };
            importFromSourceExerciseStub.returns(throwError(error));
            comp.importTeams();
            expect(importFromSourceExerciseStub).to.have.been.calledWithExactly(comp.exercise, comp.sourceExercise, comp.importStrategy);
            expect(importTeamsStub).to.not.have.been.called;
            expect(onSuccessStub).to.not.have.been.called;
            expect(onErrorStub).to.have.been.calledWithExactly(error);
            expect(comp.isImporting).to.equal(true);
        });
        it('should call importTeamsFromFile if not show import from exercise and call save success', () => {
            comp.showImportFromExercise = false;
            comp.importTeams();
            expect(importFromSourceExerciseStub).to.not.have.been.called;
            expect(importTeamsStub).to.have.been.calledWithExactly(comp.exercise, comp.sourceTeams, comp.importStrategy);
            expect(onSuccessStub).to.have.been.calledWithExactly(fromFileResponse);
            expect(onErrorStub).to.not.have.been.called;
            expect(comp.isImporting).to.equal(false);
        });
        it('should call importTeamsFromFile if not show import from exercise and call save error on Error', () => {
            const error = { status: 404 };
            comp.showImportFromExercise = false;
            importTeamsStub.returns(throwError(error));
            comp.importTeams();
            expect(importFromSourceExerciseStub).to.not.have.been.called;
            expect(importTeamsStub).to.have.been.calledWithExactly(comp.exercise, comp.sourceTeams, comp.importStrategy);
            expect(onSuccessStub).to.not.have.been.called;
            expect(onErrorStub).to.have.been.calledWithExactly(error);
            expect(comp.isImporting).to.equal(false);
        });
    });

    describe('onTeamsChanged', () => {
        let initImportStub: SinonStub;
        let computeSourceFreeOfConflictsStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            initImportStub = stub(comp, 'initImportStrategy');
            computeSourceFreeOfConflictsStub = stub(comp, 'computeSourceTeamsFreeOfConflicts');
        });
        afterEach(() => {
            restore();
        });
        it('change component files and convert file teams to normal teams', () => {
            comp.onTeamsChanged(mockSourceTeams);
            expect(initImportStub).to.have.been.called;
            expect(comp.sourceTeams).to.deep.equal(mockSourceTeams);
            expect(comp.conflictingRegistrationNumbersSet).to.deep.equal(new Set(registrationNumbers));
            expect(comp.conflictingLoginsSet).to.deep.equal(new Set(logins));
            expect(computeSourceFreeOfConflictsStub).to.have.been.called;
        });
        it('adds registration number if a student is in two or more teams', () => {
            comp.onTeamsChanged([...mockSourceTeams, ...mockSourceTeams]);
            expect(comp.problematicRegistrationNumbers).to.deep.equal([...mockSourceTeamStudents.map((student) => student.visibleRegistrationNumber), ...registrationNumbers]);
        });
        it('adds login if a student is in two or more teams', () => {
            comp.onTeamsChanged([...mockSourceTeams, ...mockSourceTeams]);
            expect(comp.problematicLogins).to.deep.equal([...mockSourceTeamStudents.map((student) => student.login), ...logins]);
        });
    });

    describe('onSaveSuccess', () => {
        let alertServiceStub: SinonStub;
        let response: HttpResponse<Team[]>;
        beforeEach(() => {
            resetComponent();
            response = new HttpResponse<Team[]>({ body: mockSourceTeams });
            alertServiceStub = stub(alertService, 'success');
        });

        it('change component files and convert file teams to normal teams', fakeAsync(() => {
            comp.isImporting = true;
            comp.onSaveSuccess(response);
            tick(500);
            expect(ngbActiveModal.close).to.have.been.calledWithExactly(mockSourceTeams);
            expect(comp.isImporting).to.equal(false);
            expect(alertServiceStub).to.have.been.calledWith('artemisApp.team.importSuccess', { numberOfImportedTeams: comp.numberOfTeamsToBeImported });
        }));
    });

    describe('onSaveError', () => {
        let alertServiceStub: SinonStub;
        let response: HttpErrorResponse;
        beforeEach(() => {
            resetComponent();
            alertServiceStub = stub(alertService, 'error');
        });
        afterEach(() => {
            restore();
        });
        it('call alert service', () => {
            response = new HttpErrorResponse({ error: {} });
            comp.isImporting = true;
            comp.onSaveError(response);
            expect(comp.isImporting).to.equal(false);
            expect(alertServiceStub).to.have.been.calledWith('artemisApp.team.importError');
        });
        it('call alert service if students not found', () => {
            const notFoundRegistrationNumbers = ['1', '2', '3'];
            const notFoundLogins = ['l1', 'l2', 'l3'];
            response = new HttpErrorResponse({ error: { errorKey: 'studentsNotFound', params: { registrationNumbers: notFoundRegistrationNumbers, logins: notFoundLogins } } });
            comp.isImporting = true;
            comp.onSaveError(response);
            expect(comp.isImporting).to.equal(false);
            expect(alertServiceStub).to.have.been.calledWithExactly('artemisApp.team.errors.registrationNumbersNotFound', { registrationNumbers: notFoundRegistrationNumbers });
            expect(alertServiceStub).to.have.been.calledWithExactly('artemisApp.team.errors.loginsNotFound', { logins: notFoundLogins });
        });
        it('call alert service if students appear multiple times', () => {
            const students = [
                { first: 'l1', second: '1' },
                { first: 'l2', second: '2' },
                { first: 'l3', second: '3' },
            ];
            const message = 'l1:1,l2:2,l3:3';
            response = new HttpErrorResponse({ error: { errorKey: 'studentsAppearMultipleTimes', params: { students } } });
            comp.isImporting = true;
            comp.onSaveError(response);
            expect(comp.isImporting).to.equal(false);
            expect(alertServiceStub).to.have.been.calledWithExactly('artemisApp.team.errors.studentsAppearMultipleTimes', { students: message });
        });
    });

    describe('setShowImportFromExercise', () => {
        let initImportStrategyStub: SinonStub;
        const expectValuesToBeReset = () => {
            expect(comp.sourceTeams).to.equal(undefined);
            expect(comp.sourceExercise).to.equal(undefined);
            expect(comp.isImporting).to.equal(false);
            expect(comp.conflictingLoginsSet).to.deep.equal(new Set(logins));
            expect(comp.conflictingRegistrationNumbersSet).to.deep.equal(new Set(registrationNumbers));
            expect(initImportStrategyStub).to.have.been.called;
        };
        beforeEach(() => {
            resetComponent();
            initImportStrategyStub = stub(comp, 'initImportStrategy');
            comp.sourceTeams = mockSourceTeams;
            comp.sourceExercise = mockSourceExercise;
            comp.isImporting = true;
            comp.studentsAppearInMultipleTeams = true;
            comp.conflictingRegistrationNumbersSet = new Set(['1']);
            comp.conflictingLoginsSet = new Set(['l1']);
        });
        afterEach(() => {
            restore();
        });
        it('should set show import from exercise to true', () => {
            comp.showImportFromExercise = false;
            comp.setShowImportFromExercise(true);
            expect(comp.showImportFromExercise).to.equal(true);
            expectValuesToBeReset();
        });
        it('should set show import from exercise to false', () => {
            comp.setShowImportFromExercise(false);
            expect(comp.showImportFromExercise).to.equal(false);
            expectValuesToBeReset();
        });
    });

    describe('sampleTeamForLegend', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return a sample team', () => {
            const team = new Team();
            team.students = [{ ...new User(1, 'ga12abc', 'John', 'Doe', 'john.doe@tum.de'), name: 'John Doe' }];
            expect(comp.sampleTeamForLegend).to.deep.equal(team);
        });
    });

    describe('sampleErrorStudentLoginsForLegend', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return a logins of sample team', () => {
            expect(comp.sampleErrorStudentLoginsForLegend).to.deep.equal(['ga12abc']);
        });
    });

    describe('showLegend', () => {
        beforeEach(() => {
            resetComponent();
            comp.sourceTeams = mockSourceTeams;
        });
        it('should return false no source teams', () => {
            comp.sourceTeams = undefined;
            expect(comp.showLegend).to.equal(false);
        });
        it('should return false source teams length is equal to conflict free teams length', () => {
            comp.sourceTeamsFreeOfConflicts = mockSourceTeams;
            expect(comp.showLegend).to.equal(false);
        });
        it('should return true source teams length not equal to conflict free teams length', () => {
            comp.sourceTeamsFreeOfConflicts = [];
            expect(comp.showLegend).to.equal(true);
        });
    });

    describe('problematicRegistrationNumbers', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return union of registration number arrays', () => {
            const conflictingRegistrationNumbers = ['1', '2', '3'];
            comp.conflictingRegistrationNumbersSet = new Set(conflictingRegistrationNumbers);
            expect(comp.problematicRegistrationNumbers).to.deep.equal(conflictingRegistrationNumbers);
        });
    });

    describe('problematicLogins', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return array of conflicting logins set', () => {
            const conflictingLogins = ['l1', 'l2', 'l3'];
            comp.conflictingLoginsSet = new Set(conflictingLogins);
            expect(comp.problematicLogins).to.deep.equal(conflictingLogins);
        });
    });
});
