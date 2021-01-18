import { EventEmitter, SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { SplitPaneHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';

describe('Plagiarism Split Pane Header Component', () => {
    let comp: SplitPaneHeaderComponent;
    let fixture: ComponentFixture<SplitPaneHeaderComponent>;

    const files = ['src/Main.java', 'src/Utils.java', 'src/Other.java'];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, ArtemisPlagiarismModule, TranslateTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        fixture = TestBed.createComponent(SplitPaneHeaderComponent);
        comp = fixture.componentInstance;

        comp.files = [];
        comp.studentLogin = 'ts10abc';
        comp.selectFile = new EventEmitter<string>();
    });

    it('resets the active file index on change', () => {
        comp.activeFileIndex = 1;

        comp.ngOnChanges({
            files: { currentValue: files } as SimpleChange,
        });

        expect(comp.activeFileIndex).toEqual(0);
    });

    it('selects the first file on change', () => {
        comp.files = files;
        spyOn(comp.selectFile, 'emit');

        comp.ngOnChanges({
            files: { currentValue: files } as SimpleChange,
        });

        expect(comp.selectFile.emit).toHaveBeenCalledWith(files[0]);
    });

    it('does not find an active file', () => {
        const activeFile = comp.getActiveFile();

        expect(activeFile).toBe(false);
    });

    it('returns the active file', () => {
        comp.files = files;
        const activeFile = comp.getActiveFile();

        expect(activeFile).toBe(files[0]);
    });

    it('handles selection of a file', () => {
        const idx = 1;
        comp.showFiles = true;
        spyOn(comp.selectFile, 'emit');

        comp.handleFileSelect(files[idx], idx);

        expect(comp.activeFileIndex).toEqual(idx);
        expect(comp.showFiles).toBe(false);
        expect(comp.selectFile.emit).toHaveBeenCalledWith(files[idx]);
    });

    it('has no files', () => {
        expect(comp.hasFiles()).toBe(false);
    });

    it('has files', () => {
        comp.files = files;

        expect(comp.hasFiles()).toBe(true);
    });

    it('toggles "show files"', () => {
        comp.showFiles = false;
        comp.files = files;

        comp.toggleShowFiles();

        expect(comp.showFiles).toBe(true);
    });

    it('does not toggle "show files"', () => {
        comp.showFiles = false;

        comp.toggleShowFiles();

        expect(comp.showFiles).toBe(false);
    });
});
