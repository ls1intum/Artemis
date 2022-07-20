import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Team } from 'app/entities/team.model';
import { TeamsImportFromFileFormComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-from-file-form.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { mockFileStudents, mockFileTeamsConverted } from '../../helpers/mocks/service/mock-team.service';
import { unparse } from 'papaparse';

describe('TeamsImportFromFileFormComponent', () => {
    let comp: TeamsImportFromFileFormComponent;
    let fixture: ComponentFixture<TeamsImportFromFileFormComponent>;
    let debugElement: DebugElement;
    let changeDetector: ChangeDetectorRef;

    function resetComponent() {
        comp.sourceTeams = undefined;
        comp.importedTeams = [];
        comp.importFile = undefined;
        comp.importFileName = '';
        comp.loading = false;
    }

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [],
            declarations: [TeamsImportFromFileFormComponent, MockComponent(HelpIconComponent), MockComponent(FaIconComponent)],
            providers: [MockProvider(TranslateService)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(TeamsImportFromFileFormComponent);
                comp = fixture.componentInstance;
                debugElement = fixture.debugElement;
                changeDetector = debugElement.injector.get(ChangeDetectorRef);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    describe('importing file', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('should convert and call teamsChanged with converted teams', () => {
            const setImportStub = jest.spyOn(comp, 'setImportFile');
            const inputElement = debugElement.query(By.css('input')).nativeElement;
            inputElement.dispatchEvent(new Event('change'));
            expect(setImportStub).toHaveBeenCalledOnce();
        });
    });

    describe('generateFileReader', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('should return file reader when called', () => {
            expect(comp.generateFileReader()).toStrictEqual(new FileReader());
        });
    });

    describe('onFileLoadImport', () => {
        let convertTeamsStub: jest.SpyInstance;
        let teams: Team[];
        let reader: FileReader;
        let getElementStub: jest.SpyInstance;
        const element = document.createElement('input');
        let control = { ...element, value: 'test' };

        beforeEach(() => {
            resetComponent();
            convertTeamsStub = jest.spyOn(comp, 'convertTeams').mockReturnValue(mockFileTeamsConverted);
            comp.teamsChanged.subscribe((value: Team[]) => (teams = value));
            control = { ...element, value: 'test' };
            getElementStub = jest.spyOn(document, 'getElementById').mockReturnValue(control);
        });

        it('should parse json file and send converted teams', () => {
            reader = { ...reader, result: JSON.stringify(mockFileStudents), onload: null };
            comp.importFile = new File([''], 'file.json', { type: 'application/json' });
            comp.importFileName = 'file.json';
            expect(control.value).toBe('test');

            comp.onFileLoadImport(reader);

            expect(convertTeamsStub).toHaveBeenCalledOnce();
            expect(comp.importedTeams).toEqual(mockFileStudents);
            expect(comp.sourceTeams).toStrictEqual(mockFileTeamsConverted);
            expect(teams).toStrictEqual(mockFileTeamsConverted);
            expect(comp.loading).toBeFalse();
            expect(comp.importFile).toBe(undefined);
            expect(comp.importFileName).toBe('');
            expect(getElementStub).toHaveBeenCalledOnce();
            expect(control.value).toBe('');
        });

        it('should parse csv file and send converted teams', async () => {
            reader = {
                ...reader,
                result: unparse(mockFileStudents, {
                    columns: ['registrationNumber', 'username', 'firstName', 'lastName', 'teamName'],
                }),
            };
            comp.importFile = new File([''], 'file.csv', { type: 'text/csv' });
            comp.importFileName = 'file.csv';
            expect(control.value).toBe('test');
            await comp.onFileLoadImport(reader);
        });
    });

    describe('setImportFile', () => {
        let changeDetectorDetectChangesSpy: jest.SpyInstance;

        beforeEach(() => {
            resetComponent();
            changeDetectorDetectChangesSpy = jest.spyOn(changeDetector.constructor.prototype, 'detectChanges');
        });

        it('should set import file correctly', () => {
            const file = new File(['content'], 'testFileName', { type: 'text/plain' });
            const ev = { target: { files: [file] } };
            comp.setImportFile(ev);
            expect(comp.importFile).toStrictEqual(file);
            expect(comp.importFileName).toBe('testFileName');
            expect(changeDetectorDetectChangesSpy).toHaveBeenCalledOnce();
        });

        it('should set import file correctly for empty file', () => {
            const ev = { target: { files: [] } };
            comp.setImportFile(ev);
            expect(comp.importFile).toBe(undefined);
            expect(comp.importFileName).toBe('');
            expect(changeDetectorDetectChangesSpy).not.toHaveBeenCalled();
        });
    });

    describe('convertTeams', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('should convert file teams correctly', () => {
            expect(comp.convertTeams(mockFileStudents)).toEqual(mockFileTeamsConverted);
        });
    });

    describe('Invalid team name throws exception', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('should throw error', () => {
            const invalidFileStudents = [...mockFileStudents];
            invalidFileStudents[0].teamName = '1invalidTeamName';
            expect(() => comp.convertTeams(invalidFileStudents)).toThrowError();
        });
    });
});
