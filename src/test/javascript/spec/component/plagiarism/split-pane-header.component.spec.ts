import { EventEmitter, SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { SplitPaneHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgbTooltip, NgbDropdown } from '@ng-bootstrap/ng-bootstrap';
import { NgModel } from '@angular/forms';
import { PlagiarismDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-details/plagiarism-details.component';
import { PlagiarismRunDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { MockPipe, MockDirective, MockComponent } from 'ng-mocks';

describe('Plagiarism Split Pane Header Component', () => {
    let comp: SplitPaneHeaderComponent;
    let fixture: ComponentFixture<SplitPaneHeaderComponent>;

    const files = [
        { file: 'src/Main.java', hasMatch: true },
        { file: 'src/Utils.java', hasMatch: false },
        { file: 'src/Other.java', hasMatch: true },
    ];

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                SplitPaneHeaderComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbDropdown),
                MockDirective(NgbTooltip),
                MockDirective(NgModel),
                MockComponent(PlagiarismDetailsComponent),
                MockComponent(PlagiarismRunDetailsComponent),
                MockComponent(PlagiarismSidebarComponent),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(SplitPaneHeaderComponent);
                comp = fixture.componentInstance;

                comp.files = [];
                comp.studentLogin = 'ts10abc';
                comp.selectFile = new EventEmitter<string>();
            });
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
        jest.spyOn(comp.selectFile, 'emit');

        comp.ngOnChanges({
            files: { currentValue: files } as SimpleChange,
        });

        expect(comp.selectFile.emit).toHaveBeenCalledOnce();
        expect(comp.selectFile.emit).toHaveBeenCalledWith(files[0].file);
    });

    it('does not find an active file', () => {
        const activeFile = comp.hasActiveFile();

        expect(activeFile).toBeFalse();
    });

    it('returns the active file', () => {
        comp.files = files;
        const activeFile = comp.getActiveFile();

        expect(activeFile).toBe(files[0].file);
    });

    it('handles selection of a file', () => {
        const idx = 1;
        comp.showFiles = true;
        jest.spyOn(comp.selectFile, 'emit');

        comp.handleFileSelect(files[idx], idx);

        expect(comp.activeFileIndex).toBe(idx);
        expect(comp.showFiles).toBeFalse();
        expect(comp.selectFile.emit).toHaveBeenCalledOnce();
        expect(comp.selectFile.emit).toHaveBeenCalledWith(files[idx].file);
    });

    it('has no files', () => {
        expect(comp.hasFiles()).toBeFalse();
    });

    it('has files', () => {
        comp.files = files;

        expect(comp.hasFiles()).toBeTrue();
    });

    it('toggles "show files"', () => {
        comp.showFiles = false;
        comp.files = files;

        comp.toggleShowFiles();

        expect(comp.showFiles).toBeTrue();
    });

    it('does not toggle "show files"', () => {
        comp.showFiles = false;

        comp.toggleShowFiles();

        expect(comp.showFiles).toBeFalse();
    });
});
