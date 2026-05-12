import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { TeamsImportFromFileFormComponent } from 'app/exercise/team/teams-import-dialog/teams-import-from-file-form.component';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { mockFileStudents, mockFileTeamsConverted } from 'test/helpers/mocks/service/mock-team.service';
import { unparse } from 'papaparse';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

/**
 * `fixture.detectChanges(...)` calls below pass `false` to skip the zoneless dev-mode
 * "expression changed after it was checked" verification pass. The component keeps a few
 * plain (non-signal) mutable fields (`sourceTeams`, `importedTeams`, `importFile`,
 * `importFileName`) which the verification pass does not track. Skipping verification
 * preserves the DOM-state assertions while letting tests exercise the component end-to-end.
 */
describe('TeamsImportFromFileFormComponent', () => {
    setupTestBed({ zoneless: true });

    let comp: TeamsImportFromFileFormComponent;
    let fixture: ComponentFixture<TeamsImportFromFileFormComponent>;

    function resetComponent() {
        comp.sourceTeams = undefined;
        comp.importedTeams = [];
        comp.importFile = undefined;
        comp.importFileName = '';
        comp.loading.set(false);
    }

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TeamsImportFromFileFormComponent);
        comp = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    describe('importing file', () => {
        beforeEach(() => {
            resetComponent();
        });

        it('should convert and call teamsChanged with converted teams', () => {
            const setImportStub = vi.spyOn(comp, 'setImportFile');
            const inputElement = fixture.debugElement.query(By.css('input')).nativeElement;
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
        let convertTeamsStub: ReturnType<typeof vi.spyOn>;
        let teams: Team[];
        let reader: FileReader;
        let getElementStub: ReturnType<typeof vi.spyOn>;
        const element = document.createElement('input');
        let control = { ...element, value: 'test' };

        beforeEach(() => {
            resetComponent();
            convertTeamsStub = vi.spyOn(comp, 'convertTeams').mockReturnValue(mockFileTeamsConverted);
            comp.teamsChanged.subscribe((value: Team[]) => (teams = value));
            control = { ...element, value: 'test' };
            // @ts-ignore
            getElementStub = vi.spyOn(document, 'getElementById').mockReturnValue(control);
        });

        it('should parse json file and send converted teams', async () => {
            // @ts-ignore
            reader = { ...reader, result: JSON.stringify(mockFileStudents), onload: null };
            comp.importFile = new File([''], 'file.json', { type: 'application/json' });
            comp.importFileName = 'file.json';
            expect(control.value).toBe('test');

            await comp.onFileLoadImport(reader);

            expect(convertTeamsStub).toHaveBeenCalledOnce();
            expect(comp.importedTeams).toEqual(mockFileStudents);
            expect(comp.sourceTeams).toStrictEqual(mockFileTeamsConverted);
            expect(teams).toStrictEqual(mockFileTeamsConverted);
            expect(comp.loading()).toBe(false);
            expect(comp.importFile).toBeUndefined();
            expect(comp.importFileName).toBe('');
            expect(getElementStub).toHaveBeenCalledOnce();
            expect(control.value).toBe('');
        });

        it('should parse csv file and send converted teams', async () => {
            // @ts-ignore
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
        beforeEach(() => {
            resetComponent();
        });

        it('should set import file correctly', () => {
            const file = new File(['content'], 'testFileName', { type: 'text/plain' });
            const ev = { target: { files: [file] } };
            comp.setImportFile(ev);
            expect(comp.importFile).toStrictEqual(file);
            expect(comp.importFileName).toBe('testFileName');
            expect(comp.loading()).toBe(true);
        });

        it('should set import file correctly for empty file', () => {
            const ev = { target: { files: [] } };
            comp.setImportFile(ev);
            expect(comp.importFile).toBeUndefined();
            expect(comp.importFileName).toBe('');
            expect(comp.loading()).toBe(false);
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
            expect(() => comp.convertTeams(invalidFileStudents)).toThrow();
        });
    });
});
