import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';
import { TranslateService } from '@ngx-translate/core';
import { Team } from 'app/entities/team.model';
import { TeamsImportFromFileFormComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-from-file-form.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { restore, SinonSpy, SinonStub, spy, stub } from 'sinon';
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

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule({
                imports: [],
                declarations: [TeamsImportFromFileFormComponent, MockComponent(HelpIconComponent), MockComponent(FaIconComponent)],
                providers: [MockProvider(TranslateService)],
            }).compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportFromFileFormComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        changeDetector = debugElement.injector.get(ChangeDetectorRef);
    });
    describe('importing file', () => {
        beforeEach(() => {
            resetComponent();
        });
        afterEach(() => {
            restore();
        });
        it('should convert and call teamsChanged with converted teams', () => {
            const setImportStub: SinonSpy = spy(comp, 'setImportFile');
            const inputElement = debugElement.query(By.css('input')).nativeElement;
            inputElement.dispatchEvent(new Event('change'));
            expect(setImportStub).toHaveBeenCalledTimes(1);
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
        let convertTeamsStub: SinonStub;
        let teams: Team[];
        let reader: FileReader;
        let getElementStub: SinonStub;
        const element = document.createElement('input');
        let control: any;
        beforeEach(() => {
            resetComponent();
            convertTeamsStub = stub(comp, 'convertTeams').returns(mockFileTeamsConverted);
            comp.teamsChanged.subscribe((value: Team[]) => (teams = value));
            control = { ...element, value: 'test' };
            getElementStub = stub(document, 'getElementById').returns(control);
        });
        afterEach(() => {
            restore();
        });
        it('should parse json file and send converted teams', () => {
            reader = { ...reader, result: JSON.stringify(mockFileStudents), onload: null };
            comp.importFile = new File([''], 'file.json', { type: 'application/json' });
            comp.importFileName = 'file.json';
            expect(control.value).toEqual('test');
            comp.onFileLoadImport(reader);
            expect(convertTeamsStub).toHaveBeenCalledTimes(1);
            expect(comp.importedTeams).toStrictEqual(mockFileStudents);
            expect(comp.sourceTeams).toStrictEqual(mockFileTeamsConverted);
            expect(teams).toStrictEqual(mockFileTeamsConverted);
            expect(comp.loading).toEqual(false);
            expect(comp.importFile).toEqual(undefined);
            expect(comp.importFileName).toEqual('');
            expect(getElementStub).toHaveBeenCalledTimes(1);
            expect(control.value).toEqual('');
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
            expect(control.value).toEqual('test');
            await comp.onFileLoadImport(reader);
            expect(convertTeamsStub).toHaveBeenCalledTimes(1);
            expect(comp.importedTeams).toEqual(mockFileStudents);
            expect(comp.sourceTeams).toStrictEqual(mockFileTeamsConverted);
            expect(teams).toStrictEqual(mockFileTeamsConverted);
            expect(comp.loading).toEqual(false);
            expect(comp.importFile).toEqual(undefined);
            expect(comp.importFileName).toEqual('');
            expect(getElementStub).toHaveBeenCalledTimes(1);
            expect(control.value).toEqual('');
        });
    });

    describe('setImportFile', () => {
        let changeDetectorDetectChangesStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            changeDetectorDetectChangesStub = stub(changeDetector.constructor.prototype, 'detectChanges');
        });
        afterEach(() => {
            restore();
        });
        it('should set import file correctly', () => {
            const file = new File(['content'], 'testFileName', { type: 'text/plain' });
            const ev = { target: { files: [file] } };
            comp.setImportFile(ev);
            expect(comp.importFile).toStrictEqual(file);
            expect(comp.importFileName).toEqual('testFileName');
            expect(changeDetectorDetectChangesStub).toHaveBeenCalledTimes(1);
        });
        it('should set import file correctly', () => {
            const ev = { target: { files: [] } };
            comp.setImportFile(ev);
            expect(comp.importFile).toEqual(undefined);
            expect(comp.importFileName).toEqual('');
            expect(changeDetectorDetectChangesStub).not.toHaveBeenCalled();
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
            try {
                comp.convertTeams(invalidFileStudents);
            } catch (e) {
                expect(e.stack).not.toBe(null);
            }
        });
    });
});
