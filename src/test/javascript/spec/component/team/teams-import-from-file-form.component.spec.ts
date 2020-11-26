import { ChangeDetectorRef, DebugElement } from '@angular/core';
import { ComponentFixture, TestBed, waitForAsync } from '@angular/core/testing';
import { Team } from 'app/entities/team.model';
import { TeamsImportFromFileFormComponent } from 'app/exercises/shared/team/teams-import-dialog/teams-import-from-file-form.component';
import * as chai from 'chai';
import { SinonStub, stub } from 'sinon';
import * as sinonChai from 'sinon-chai';
import { mockFileStudents, mockFileTeamsConverted } from '../../helpers/mocks/service/mock-team.service';
import config from './config';

chai.use(sinonChai);
const expect = chai.expect;

describe('TeamsImportFromFileFormComponent', () => {
    let comp: TeamsImportFromFileFormComponent;
    let fixture: ComponentFixture<TeamsImportFromFileFormComponent>;
    let debugElement: DebugElement;
    let changeDetector: ChangeDetectorRef;

    function resetComponent() {
        comp.sourceTeams = undefined;
        comp.importedTeams = { students: [] };
        comp.importFile = undefined;
        comp.importFileName = '';
        comp.loading = false;
    }

    beforeEach(
        waitForAsync(() => {
            TestBed.configureTestingModule(config).overrideTemplate(TeamsImportFromFileFormComponent, '').compileComponents();
        }),
    );
    beforeEach(() => {
        fixture = TestBed.createComponent(TeamsImportFromFileFormComponent);
        comp = fixture.componentInstance;
        debugElement = fixture.debugElement;
        changeDetector = debugElement.injector.get(ChangeDetectorRef);
    });

    describe('generateFileReader', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should return file reader when called', () => {
            expect(comp.generateFileReader()).to.deep.equal(new FileReader());
        });
    });

    describe('onFileLoadImport', () => {
        let convertTeamsStub: SinonStub;
        let teams: Team[];
        let generateFileReaderStub: SinonStub;
        let reader: FileReader;
        let getElementStub: SinonStub;
        const element = document.createElement('input');
        const control = { ...element, value: 'test' };
        beforeEach(() => {
            resetComponent();
            convertTeamsStub = stub(comp, 'convertTeams').returns(mockFileTeamsConverted);
            comp.teamsChanged.subscribe((value: Team[]) => (teams = value));
            reader = { ...reader, result: JSON.stringify(mockFileStudents), onload: null };
            generateFileReaderStub = stub(comp, 'generateFileReader').returns(reader);
            comp.importFile = new File([''], 'file.txt', { type: 'text/plain' });
            comp.importFileName = 'file.txt';
            getElementStub = stub(document, 'getElementById').returns(control);
        });
        afterEach(() => {
            generateFileReaderStub.restore();
            convertTeamsStub.restore();
            getElementStub.restore();
        });
        it('should parse file and send converted teams', () => {
            expect(control.value).to.equal('test');
            comp.onFileLoadImport(reader);
            expect(comp.importedTeams).to.deep.equal(mockFileStudents);
            expect(comp.sourceTeams).to.deep.equal(mockFileTeamsConverted);
            expect(teams).to.deep.equal(mockFileTeamsConverted);
            expect(comp.loading).to.equal(false);
            expect(comp.importFile).to.equal(undefined);
            expect(comp.importFileName).to.equal('');
            expect(getElementStub).to.have.been.called;
            expect(control.value).to.equal('');
        });
    });

    describe('setImportFile', () => {
        let changeDetectorDetectChangesStub: SinonStub;
        beforeEach(() => {
            resetComponent();
            changeDetectorDetectChangesStub = stub(changeDetector.constructor.prototype, 'detectChanges');
        });
        afterEach(() => {
            changeDetectorDetectChangesStub.restore();
        });
        it('should set import file correctly', () => {
            const file = new File(['content'], 'testFileName', { type: 'text/plain' });
            const ev = { target: { files: [file] } };
            comp.setImportFile(ev);
            expect(comp.importFile).to.deep.equal(file);
            expect(comp.importFileName).to.equal('testFileName');
            expect(changeDetectorDetectChangesStub).to.have.been.called;
        });
        it('should set import file correctly', () => {
            const ev = { target: { files: [] } };
            comp.setImportFile(ev);
            expect(comp.importFile).to.equal(undefined);
            expect(comp.importFileName).to.equal('');
            expect(changeDetectorDetectChangesStub).to.not.have.been.called;
        });
    });

    describe('convertTeams', () => {
        beforeEach(() => {
            resetComponent();
        });
        it('should convert file teams correctly', () => {
            expect(comp.convertTeams(mockFileStudents)).to.deep.equal(mockFileTeamsConverted);
        });
    });
});
