import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, fakeAsync, flush, TestBed, tick } from '@angular/core/testing';
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
import { flatMap } from 'lodash-es';
import { MockComponent, MockDirective, MockModule, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { mockExercise, mockSourceExercise, mockSourceTeams, mockSourceTeamStudents, mockTeam, mockTeams, mockTeamStudents } from '../../helpers/mocks/service/mock-team.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { MockActiveModal } from '../../helpers/mocks/service/mock-active-modal.service';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

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

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [MockModule(FormsModule)],
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
                MockComponent(FaIconComponent),
            ],
            providers: [
                MockProvider(TeamService),
                {
                    provide: NgbActiveModal,
                    useClass: MockActiveModal,
                },
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamsImportDialogComponent);
                comp = fixture.componentInstance;
                ngbActiveModal = TestBed.inject(NgbActiveModal);
                alertService = TestBed.inject(AlertService);
                teamService = TestBed.inject(TeamService);
                resetComponent();
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('OnInit', () => {
        it('should compute potential conflicts based on existing teams', () => {
            const potentialConflictSpy: jest.SpyInstance = jest.spyOn(comp, 'computePotentialConflictsBasedOnExistingTeams');
            comp.ngOnInit();
            expect(potentialConflictSpy).toHaveBeenCalled();
        });
    });

    describe('loadSourceTeams', () => {
        let teamServiceStub: jest.SpyInstance;
        let computeSourceStub: jest.SpyInstance;
        beforeEach(() => {
            teamServiceStub = jest.spyOn(teamService, 'findAllByExerciseId');
            computeSourceStub = jest.spyOn(comp, 'computeSourceTeamsFreeOfConflicts');
            teamServiceStub.mockReturnValue(of(new HttpResponse<Team[]>({ body: mockSourceTeams })));
        });

        it('should load teams of given exercise if find was successful', () => {
            const sourceExercise = mockSourceExercise;
            comp.sourceTeams = [];
            comp.loadSourceTeams(sourceExercise);
            expect(comp.loadingSourceTeams).toEqual(false);
            expect(comp.loadingSourceTeamsFailed).toEqual(false);
            expect(teamServiceStub).toHaveBeenCalledWith(sourceExercise.id);
            expect(comp.sourceTeams).toEqual(mockSourceTeams);
            expect(computeSourceStub).toHaveBeenCalled();
        });
        it('should not load teams of given exercise if find failed', () => {
            teamServiceStub.mockReturnValue(throwError({ status: 404 }));
            const sourceExercise = mockSourceExercise;
            comp.sourceTeams = [];
            comp.loadSourceTeams(sourceExercise);
            expect(comp.sourceTeams).toEqual(undefined);
            expect(comp.loadingSourceTeams).toEqual(false);
            expect(comp.loadingSourceTeamsFailed).toEqual(true);
            expect(teamServiceStub).toHaveBeenCalledWith(sourceExercise.id);
            expect(computeSourceStub).not.toHaveBeenCalled();
        });

        it('should load selected exercise', () => {
            const loadSourceStub = jest.spyOn(comp, 'loadSourceTeams');
            const initImportStrategy = jest.spyOn(comp, 'initImportStrategy');
            const sourceExercise = mockSourceExercise;
            comp.onSelectSourceExercise(sourceExercise);
            expect(comp.sourceExercise).toEqual(sourceExercise);
            expect(initImportStrategy).toHaveBeenCalled();
            expect(loadSourceStub).toHaveBeenCalledWith(sourceExercise);
        });
    });

    describe('initImportStrategy', () => {
        it('should set import strategy to default if there no teams', () => {
            comp.teams = [];
            comp.initImportStrategy();
            expect(comp.importStrategy).toEqual(comp.defaultImportStrategy);
        });
        it('should set import strategy to undefined if there are teams', () => {
            comp.initImportStrategy();
            expect(comp.importStrategy).toEqual(undefined);
        });
    });

    describe('computePotentialConflictsBasedOnExistingTeams', () => {
        it('should fill existing arrays current team values', () => {
            comp.computePotentialConflictsBasedOnExistingTeams();
            const shortNames = teams.map((team) => team.shortName);
            expect(comp.teamShortNamesAlreadyExistingInExercise).toEqual(shortNames);
            expect(comp.conflictingLoginsSet).toEqual(new Set(logins));
            expect(comp.conflictingRegistrationNumbersSet).toEqual(new Set(registrationNumbers));
        });
    });

    describe('computeSourceTeamsFreeOfConflicts', () => {
        let sourceFreeStub: jest.SpyInstance;
        beforeEach(() => {
            sourceFreeStub = jest.spyOn(comp, 'isSourceTeamFreeOfAnyConflicts');
            sourceFreeStub.mockReturnValue(true);
        });
        it('should filter source teams according to conflict', () => {
            comp.sourceTeams = mockSourceTeams;
            comp.computeSourceTeamsFreeOfConflicts();
            expect(comp.sourceTeamsFreeOfConflicts).toEqual([mockSourceTeams[0], mockSourceTeams[1], mockSourceTeams[2]]);
            expect(sourceFreeStub).toHaveBeenCalledTimes(mockSourceTeams.length);
        });
    });

    describe('isSourceTeamFreeOfAnyConflicts', () => {
        it('returns false if short name is in already existing short names', () => {
            comp.teamShortNamesAlreadyExistingInExercise = [mockTeam.shortName!];
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(false);
        });

        it('returns true if short name is not in already existing short names', () => {
            comp.teamShortNamesAlreadyExistingInExercise = [];
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(true);
        });

        it('Import from exercise: returns false if one of the students login is in already existing students', () => {
            comp.conflictingLoginsSet = new Set([mockTeamStudents[0].login!]);
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(false);
        });

        it('Import from exercise: returns true if none of the students login is in already existing students', () => {
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(true);
        });

        it('Import from file: returns false if one of the students login is in already existing students', () => {
            comp.conflictingLoginsSet = new Set([mockTeamStudents[0].login!]);
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(false);
        });

        it('Import from exercise: returns true if one of the students registration number is in already existing students', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(true);
        });

        it('Import from file: returns false if one of the students registration number is in already existing students', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(false);
        });

        it('Import from exercise: returns true if one of the students registration number is in already other source teams', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            comp.studentsAppearInMultipleTeams = true;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(true);
        });

        it('Import from file: returns false if one of the students registration number is in already other source teams', () => {
            comp.conflictingRegistrationNumbersSet = new Set([mockTeamStudents[0].visibleRegistrationNumber!]);
            comp.studentsAppearInMultipleTeams = true;
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(false);
        });

        it('Import from file: returns false if one of the students login is in already other source teams', () => {
            comp.conflictingLoginsSet = new Set([mockTeamStudents[0].login!]);
            comp.studentsAppearInMultipleTeams = true;
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(false);
        });

        it('Import from file: returns true if no student is in multiple teams', () => {
            comp.showImportFromExercise = false;
            expect(comp.isSourceTeamFreeOfAnyConflicts(mockTeam)).toEqual(true);
        });
    });

    describe('numberOfConflictFreeSourceTeams', () => {
        it('should return length of source teams free of conflict', () => {
            expect(comp.numberOfConflictFreeSourceTeams).toEqual(0);
            comp.sourceTeamsFreeOfConflicts = mockTeams;
            expect(comp.numberOfConflictFreeSourceTeams).toEqual(mockTeams.length);
        });
    });

    describe('numberOfTeamsToBeDeleted', () => {
        it('should return 0 if import strategy is CREATE_ONLY', () => {
            comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            expect(comp.numberOfTeamsToBeDeleted).toEqual(0);
        });
        it('should return length of teams if import strategy is PURGE_EXISTING', () => {
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
            expect(comp.numberOfTeamsToBeDeleted).toEqual(mockTeams.length);
        });
    });

    describe('numberOfTeamsToBeImported', () => {
        it('should return conflict free teams number if import strategy is CREATE_ONLY', () => {
            comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            comp.sourceTeamsFreeOfConflicts = mockSourceTeams;
            expect(comp.numberOfTeamsToBeImported).toEqual(mockSourceTeams.length);
        });
        it('should return length of source teams if import strategy is PURGE_EXISTING', () => {
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
            expect(comp.numberOfTeamsToBeImported).toEqual(mockSourceTeams.length);
        });
    });

    describe('numberOfTeamsAfterImport', () => {
        it('should return current teams + conflict free teams number if import strategy is CREATE_ONLY', () => {
            comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            comp.sourceTeamsFreeOfConflicts = mockSourceTeams;
            expect(comp.numberOfTeamsAfterImport).toEqual(mockSourceTeams.length + mockTeams.length);
        });
        it('should return length of source teams if import strategy is PURGE_EXISTING', () => {
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
            expect(comp.numberOfTeamsAfterImport).toEqual(mockSourceTeams.length);
        });
    });

    describe('showImportStrategyChoices', () => {
        beforeEach(() => {
            comp.sourceExercise = mockSourceExercise;
            comp.sourceTeams = mockSourceTeams;
        });
        it('Import from exercise: should return false if there is no sourceExercise', () => {
            comp.sourceExercise = undefined;
            expect(comp.showImportStrategyChoices).toEqual(false);
        });
        it('Import from exercise: should return true if there is a sourceExercise and source team', () => {
            expect(comp.showImportStrategyChoices).toEqual(true);
        });
        it('should return false if there is no source team', () => {
            comp.sourceTeams = [];
            expect(comp.showImportStrategyChoices).toEqual(false);
        });
        it('should return false if there is no existing team', () => {
            comp.teams = [];
            expect(comp.showImportStrategyChoices).toEqual(false);
        });
        it('Import from file: should return false if source teams undefined', () => {
            comp.sourceTeams = undefined;
            comp.showImportFromExercise = false;
            expect(comp.showImportStrategyChoices).toEqual(false);
        });
        it('Import from file: should return true if source exercise undefined', () => {
            comp.sourceExercise = undefined;
            comp.showImportFromExercise = false;
            expect(comp.showImportStrategyChoices).toEqual(true);
        });
    });

    describe('updateImportStrategy', () => {
        it('should set import strategy to given import strategy', () => {
            expect(comp.importStrategy).toEqual(undefined);
            comp.updateImportStrategy(TeamImportStrategyType.CREATE_ONLY);
            expect(comp.importStrategy).toEqual(TeamImportStrategyType.CREATE_ONLY);
            comp.updateImportStrategy(TeamImportStrategyType.PURGE_EXISTING);
            expect(comp.importStrategy).toEqual(TeamImportStrategyType.PURGE_EXISTING);
        });
    });

    describe('showImportPreviewNumbers', () => {
        describe('import from exercise', () => {
            beforeEach(() => {
                comp.sourceExercise = undefined;
                comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
            });
            it('Import from exercise: should return false if there is no sourceExercise', () => {
                expect(comp.showImportPreviewNumbers).toEqual(false);
            });
            it('Import from exercise: should return true if there is a sourceExercise and source team', () => {
                comp.sourceExercise = mockSourceExercise;
                comp.sourceTeams = mockSourceTeams;
                expect(comp.showImportPreviewNumbers).toEqual(true);
            });
            it('should return false if there is no source team', () => {
                expect(comp.showImportPreviewNumbers).toEqual(false);
            });
            it('Import from exercise: should return false if there is no import strategy', () => {
                expect(comp.showImportPreviewNumbers).toEqual(false);
            });
        });
        describe('import from exercise', () => {
            beforeEach(() => {
                comp.sourceExercise = undefined;
                comp.importStrategy = TeamImportStrategyType.CREATE_ONLY;
                comp.showImportFromExercise = false;
            });
            it('Import from file: should return false if there is no import strategy', () => {
                expect(comp.showImportPreviewNumbers).toEqual(false);
            });
            it('Import from file: should return false if no students in multiple teams and no import strategy', () => {
                comp.importStrategy = undefined;
                expect(comp.showImportPreviewNumbers).toEqual(false);
            });
            it('Import from file: should return true if there are students appear in multiple teams and conflicting registration numbers', () => {
                comp.conflictingRegistrationNumbersSet = new Set(['1', '2']);
                comp.studentsAppearInMultipleTeams = true;
                comp.importStrategy = undefined;
                expect(comp.showImportPreviewNumbers).toEqual(true);
            });
            it('Import from file: should return true if there are students appear in multiple teams and conflicting logins', () => {
                comp.conflictingLoginsSet = new Set(['l1', 'l2']);
                comp.studentsAppearInMultipleTeams = true;
                comp.importStrategy = undefined;
                expect(comp.showImportPreviewNumbers).toEqual(true);
            });
        });
    });

    describe('isSubmitDisabled', () => {
        beforeEach(() => {
            comp.sourceExercise = mockSourceExercise;
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
        });
        it('should return false', () => {
            expect(comp.isSubmitDisabled).toEqual(false);
        });
        it('Import from exercise: should return true if importing', () => {
            comp.isImporting = true;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
        it('Import from exercise: should return true if it has source exercise', () => {
            comp.sourceExercise = undefined;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
        it('Import from exercise: should return true if it has source teams', () => {
            comp.sourceTeams = undefined;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
        it('Import from exercise: should return true if it has import strategy', () => {
            comp.importStrategy = undefined;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
        it('Import from file: should return false if importing', () => {
            comp.isImporting = true;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).toEqual(false);
        });
        it('Import from file: should return false if it has no source exercise', () => {
            comp.sourceExercise = undefined;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).toEqual(false);
        });
        it('Import from file: should return true if it has source teams', () => {
            comp.sourceTeams = undefined;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
        it('Import from file: should return true if it has import strategy', () => {
            comp.importStrategy = undefined;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
        it('Import from file: should return true if there same registration number is in two teams', () => {
            comp.conflictingRegistrationNumbersSet = new Set(['1', '2']);
            comp.studentsAppearInMultipleTeams = true;
            comp.showImportFromExercise = false;
            expect(comp.isSubmitDisabled).toEqual(true);
        });
    });

    describe('clear', () => {
        it('should return false', () => {
            const ngbActiveModalSpy = jest.spyOn(ngbActiveModal, 'dismiss');
            comp.clear();
            expect(ngbActiveModalSpy).toHaveBeenCalledWith('cancel');
        });
    });

    describe('purgeAndImportTeams', () => {
        it('should return false', () => {
            const importTeamsStub = jest.spyOn(comp, 'importTeams');
            comp.purgeAndImportTeams();
            expect(importTeamsStub).toHaveBeenCalled();
            jest.restoreAllMocks();
        });
    });

    describe('importTeams', () => {
        let importFromSourceExerciseStub: jest.SpyInstance;
        let importTeamsStub: jest.SpyInstance;
        let onSuccessStub: jest.SpyInstance;
        let onErrorStub: jest.SpyInstance;
        let fromExerciseResponse: HttpResponse<Team[]>;
        let fromFileResponse: HttpResponse<Team[]>;
        beforeEach(() => {
            fromExerciseResponse = new HttpResponse<Team[]>({ body: mockSourceTeams });
            importFromSourceExerciseStub = jest.spyOn(teamService, 'importTeamsFromSourceExercise');
            importFromSourceExerciseStub.mockReturnValue(of(fromExerciseResponse));
            fromFileResponse = new HttpResponse<Team[]>({ body: [...mockSourceTeams, mockTeam] });
            importTeamsStub = jest.spyOn(teamService, 'importTeams');
            importTeamsStub.mockReturnValue(of(fromFileResponse));
            onSuccessStub = jest.spyOn(comp, 'onSaveSuccess');
            onErrorStub = jest.spyOn(comp, 'onSaveError');
            comp.sourceExercise = mockSourceExercise;
            comp.sourceTeams = mockSourceTeams;
            comp.importStrategy = TeamImportStrategyType.PURGE_EXISTING;
        });

        it('should not call team service if submit disabled', () => {
            comp.importStrategy = undefined;
            comp.importTeams();
            expect(importFromSourceExerciseStub).not.toHaveBeenCalled();
            expect(importTeamsStub).not.toHaveBeenCalled();
            expect(onSuccessStub).not.toHaveBeenCalled();
            expect(onErrorStub).not.toHaveBeenCalled();
            expect(comp.isImporting).toEqual(false);
        });
        it('should call importTeamsFromSourceExercise if show import from exercise and call save success', fakeAsync(() => {
            comp.importTeams();
            expect(comp.isImporting).toEqual(true);
            tick();
            expect(importFromSourceExerciseStub).toHaveBeenCalledWith(comp.exercise, comp.sourceExercise, comp.importStrategy);
            expect(importTeamsStub).not.toHaveBeenCalled();
            expect(onSuccessStub).toHaveBeenCalledWith(fromExerciseResponse);
            expect(onErrorStub).not.toHaveBeenCalled();
        }));
        it('should call importTeamsFromSourceExercise if show import from exercise and call save error on Error', () => {
            const error = { status: 404 };
            importFromSourceExerciseStub.mockReturnValue(throwError(error));
            comp.importTeams();
            expect(importFromSourceExerciseStub).toHaveBeenCalledWith(comp.exercise, comp.sourceExercise, comp.importStrategy);
            expect(importTeamsStub).not.toHaveBeenCalled();
            expect(onSuccessStub).not.toHaveBeenCalled();
            expect(onErrorStub).toHaveBeenCalledWith(error);
            expect(comp.isImporting).toEqual(true);
        });
        it('should call importTeamsFromFile if not show import from exercise and call save success', () => {
            comp.showImportFromExercise = false;
            comp.importTeams();
            expect(importFromSourceExerciseStub).not.toHaveBeenCalled();
            expect(importTeamsStub).toHaveBeenCalledWith(comp.exercise, comp.sourceTeams, comp.importStrategy);
            expect(onSuccessStub).toHaveBeenCalledWith(fromFileResponse);
            expect(onErrorStub).not.toHaveBeenCalled();
            expect(comp.isImporting).toEqual(false);
        });
        it('should call importTeamsFromFile if not show import from exercise and call save error on Error', () => {
            const error = { status: 404 };
            comp.showImportFromExercise = false;
            importTeamsStub.mockReturnValue(throwError(error));
            comp.importTeams();
            expect(importFromSourceExerciseStub).not.toHaveBeenCalled();
            expect(importTeamsStub).toHaveBeenCalledWith(comp.exercise, comp.sourceTeams, comp.importStrategy);
            expect(onSuccessStub).not.toHaveBeenCalled();
            expect(onErrorStub).toHaveBeenCalledWith(error);
            expect(comp.isImporting).toEqual(false);
        });
    });

    describe('onTeamsChanged', () => {
        it('change component files and convert file teams to normal teams', () => {
            const initImportStub = jest.spyOn(comp, 'initImportStrategy');
            const computeSourceFreeOfConflictsStub = jest.spyOn(comp, 'computeSourceTeamsFreeOfConflicts');
            comp.onTeamsChanged(mockSourceTeams);
            expect(initImportStub).toHaveBeenCalled();
            expect(comp.sourceTeams).toEqual(mockSourceTeams);
            expect(comp.conflictingRegistrationNumbersSet).toEqual(new Set(registrationNumbers));
            expect(comp.conflictingLoginsSet).toEqual(new Set(logins));
            expect(computeSourceFreeOfConflictsStub).toHaveBeenCalled();
        });
        it('adds registration number if a student is in two or more teams', () => {
            comp.onTeamsChanged([...mockSourceTeams, ...mockSourceTeams]);
            expect(comp.problematicRegistrationNumbers).toEqual([...mockSourceTeamStudents.map((student) => student.visibleRegistrationNumber), ...registrationNumbers]);
        });
        it('adds login if a student is in two or more teams', () => {
            comp.onTeamsChanged([...mockSourceTeams, ...mockSourceTeams]);
            expect(comp.problematicLogins).toEqual([...mockSourceTeamStudents.map((student) => student.login), ...logins]);
        });
    });

    describe('onSaveSuccess', () => {
        it('change component files and convert file teams to normal teams', fakeAsync(() => {
            const response = new HttpResponse<Team[]>({ body: mockSourceTeams });
            const alertServiceStub = jest.spyOn(alertService, 'success');
            const ngbActiveModalSpy = jest.spyOn(ngbActiveModal, 'close');
            comp.isImporting = true;
            comp.onSaveSuccess(response);
            tick(500);
            expect(ngbActiveModalSpy).toHaveBeenCalledWith(mockSourceTeams);
            expect(comp.isImporting).toEqual(false);
            expect(alertServiceStub).toHaveBeenCalledWith('artemisApp.team.importSuccess', { numberOfImportedTeams: comp.numberOfTeamsToBeImported });
            flush();
        }));
    });

    describe('onSaveError', () => {
        let alertServiceStub: jest.SpyInstance;
        let response: HttpErrorResponse;
        beforeEach(() => {
            alertServiceStub = jest.spyOn(alertService, 'error');
        });

        it('call alert service', () => {
            response = new HttpErrorResponse({ error: {} });
            comp.isImporting = true;
            comp.onSaveError(response);
            expect(comp.isImporting).toEqual(false);
            expect(alertServiceStub).toHaveBeenCalledWith('artemisApp.team.importError');
        });
        it('call alert service if students not found', () => {
            const notFoundRegistrationNumbers = ['1', '2', '3'];
            const notFoundLogins = ['l1', 'l2', 'l3'];
            response = new HttpErrorResponse({
                error: {
                    errorKey: 'studentsNotFound',
                    params: { registrationNumbers: notFoundRegistrationNumbers, logins: notFoundLogins },
                },
            });
            comp.isImporting = true;
            comp.onSaveError(response);
            expect(comp.isImporting).toEqual(false);
            expect(alertServiceStub).toHaveBeenCalledWith('artemisApp.team.errors.registrationNumbersNotFound', { registrationNumbers: notFoundRegistrationNumbers });
            expect(alertServiceStub).toHaveBeenCalledWith('artemisApp.team.errors.loginsNotFound', { logins: notFoundLogins });
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
            expect(comp.isImporting).toEqual(false);
            expect(alertServiceStub).toHaveBeenCalledWith('artemisApp.team.errors.studentsAppearMultipleTimes', { students: message });
        });
    });

    describe('setShowImportFromExercise', () => {
        let initImportStrategyStub: jest.SpyInstance;
        const expectValuesToBeReset = () => {
            expect(comp.sourceTeams).toEqual(undefined);
            expect(comp.sourceExercise).toEqual(undefined);
            expect(comp.isImporting).toEqual(false);
            expect(comp.conflictingLoginsSet).toEqual(new Set(logins));
            expect(comp.conflictingRegistrationNumbersSet).toEqual(new Set(registrationNumbers));
            expect(initImportStrategyStub).toHaveBeenCalled();
        };
        beforeEach(() => {
            initImportStrategyStub = jest.spyOn(comp, 'initImportStrategy');
            comp.sourceTeams = mockSourceTeams;
            comp.sourceExercise = mockSourceExercise;
            comp.isImporting = true;
            comp.studentsAppearInMultipleTeams = true;
            comp.conflictingRegistrationNumbersSet = new Set(['1']);
            comp.conflictingLoginsSet = new Set(['l1']);
        });

        it('should set show import from exercise to true', () => {
            comp.showImportFromExercise = false;
            comp.setShowImportFromExercise(true);
            expect(comp.showImportFromExercise).toEqual(true);
            expectValuesToBeReset();
        });
        it('should set show import from exercise to false', () => {
            comp.setShowImportFromExercise(false);
            expect(comp.showImportFromExercise).toEqual(false);
            expectValuesToBeReset();
        });
    });

    describe('sampleTeamForLegend', () => {
        it('should return a sample team', () => {
            const team = new Team();
            team.students = [{ ...new User(1, 'ga12abc', 'John', 'Doe', 'john.doe@tum.de'), name: 'John Doe' }];
            expect(comp.sampleTeamForLegend).toEqual(team);
        });
    });

    describe('sampleErrorStudentLoginsForLegend', () => {
        it('should return a logins of sample team', () => {
            expect(comp.sampleErrorStudentLoginsForLegend).toEqual(['ga12abc']);
        });
    });

    describe('showLegend', () => {
        beforeEach(() => {
            comp.sourceTeams = mockSourceTeams;
        });
        it('should return false no source teams', () => {
            comp.sourceTeams = undefined;
            expect(comp.showLegend).toEqual(false);
        });
        it('should return false source teams length is equal to conflict free teams length', () => {
            comp.sourceTeamsFreeOfConflicts = mockSourceTeams;
            expect(comp.showLegend).toEqual(false);
        });
        it('should return true source teams length not equal to conflict free teams length', () => {
            comp.sourceTeamsFreeOfConflicts = [];
            expect(comp.showLegend).toEqual(true);
        });
    });

    describe('problematicRegistrationNumbers', () => {
        it('should return union of registration number arrays', () => {
            const conflictingRegistrationNumbers = ['1', '2', '3'];
            comp.conflictingRegistrationNumbersSet = new Set(conflictingRegistrationNumbers);
            expect(comp.problematicRegistrationNumbers).toEqual(conflictingRegistrationNumbers);
        });
    });

    describe('problematicLogins', () => {
        it('should return array of conflicting logins set', () => {
            const conflictingLogins = ['l1', 'l2', 'l3'];
            comp.conflictingLoginsSet = new Set(conflictingLogins);
            expect(comp.problematicLogins).toEqual(conflictingLogins);
        });
    });
});
