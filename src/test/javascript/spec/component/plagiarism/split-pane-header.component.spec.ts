import { EventEmitter, SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { SplitPaneHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { NgModel } from '@angular/forms';
import { PlagiarismDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-details/plagiarism-details.component';
import { PlagiarismRunDetailsComponent } from 'app/exercises/shared/plagiarism/plagiarism-run-details/plagiarism-run-details.component';
import { PlagiarismSidebarComponent } from 'app/exercises/shared/plagiarism/plagiarism-sidebar/plagiarism-sidebar.component';
import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import { TextPlagiarismFileElement } from 'app/exercises/shared/plagiarism/types/text/TextPlagiarismFileElement';
import { Subject } from 'rxjs';
import { TextSubmissionViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';

describe('SplitPaneHeaderComponent', () => {
    let comp1: SplitPaneHeaderComponent;
    let comp2: SplitPaneHeaderComponent;
    let fixture1: ComponentFixture<SplitPaneHeaderComponent>;
    let fixture2: ComponentFixture<SplitPaneHeaderComponent>;

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
                MockDirective(NgModel),
                MockComponent(PlagiarismDetailsComponent),
                MockComponent(PlagiarismRunDetailsComponent),
                MockComponent(PlagiarismSidebarComponent),
                MockComponent(TextSubmissionViewerComponent),
            ],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        })
            .compileComponents()
            .then(() => {
                const fileSelectedSubject = new Subject<TextPlagiarismFileElement>();
                fixture1 = TestBed.createComponent(SplitPaneHeaderComponent);
                comp1 = fixture1.componentInstance;
                fixture2 = TestBed.createComponent(SplitPaneHeaderComponent);
                comp2 = fixture2.componentInstance;

                comp1.files = [];
                comp1.studentLogin = 'ts10abc';
                comp1.selectFile = new EventEmitter<string>();
                comp1.fileSelectedSubject = fileSelectedSubject;

                comp2.files = [];
                comp2.studentLogin = 'ts20abc';
                comp2.selectFile = new EventEmitter<string>();
                comp2.fileSelectedSubject = fileSelectedSubject;
            });
    });
    it('resets the active file index on change', () => {
        comp1.activeFileIndex = 1;

        comp1.ngOnChanges({
            files: { currentValue: files } as SimpleChange,
        });

        expect(comp1.activeFileIndex).toBe(0);
    });

    it('selects the first file on change', () => {
        comp1.files = files;
        jest.spyOn(comp1.selectFile, 'emit');

        comp1.ngOnChanges({
            files: { currentValue: files } as SimpleChange,
        });

        expect(comp1.selectFile.emit).toHaveBeenCalledOnce();
        expect(comp1.selectFile.emit).toHaveBeenCalledWith(files[0].file);
    });

    it('does not find an active file', () => {
        const activeFile = comp1.hasActiveFile();

        expect(activeFile).toBeFalse();
    });

    it('returns the active file', () => {
        comp1.files = files;
        const activeFile = comp1.getActiveFile();

        expect(activeFile).toBe(files[0].file);
    });

    it('handles selection of a file', () => {
        const idx = 1;
        comp1.showFiles = true;
        jest.spyOn(comp1.selectFile, 'emit');

        comp1.handleFileSelect(files[idx], idx);

        expect(comp1.activeFileIndex).toBe(idx);
        expect(comp1.showFiles).toBeFalse();
        expect(comp1.selectFile.emit).toHaveBeenCalledOnce();
        expect(comp1.selectFile.emit).toHaveBeenCalledWith(files[idx].file);
    });

    it('has no files', () => {
        expect(comp1.hasFiles()).toBeFalse();
    });

    it('has files', () => {
        comp1.files = files;

        expect(comp1.hasFiles()).toBeTrue();
    });

    it('toggles "show files"', () => {
        comp1.showFiles = false;
        comp1.files = files;

        comp1.toggleShowFiles();

        expect(comp1.showFiles).toBeTrue();
    });

    it('does not toggle "show files"', () => {
        comp1.showFiles = false;

        comp1.toggleShowFiles();

        expect(comp1.showFiles).toBeFalse();
    });

    it('should emit selected file through fileSelectedSubject', () => {
        const selectedFile = { idx: 0, file: files[0] };
        let emittedFile: TextPlagiarismFileElement | undefined;
        comp1.fileSelectedSubject.subscribe((file) => {
            emittedFile = file;
        });

        comp1.fileSelectedSubject.next(selectedFile);

        // Assert
        expect(emittedFile).toBe(selectedFile);
    });

    it('should sync file selection when lockFilesEnabled true', () => {
        const idx = 0;
        const selectedFile = { idx: idx, file: files[idx] };
        const lockFilesEnabled = true;

        comp1.isLockFilesEnabled = lockFilesEnabled;
        comp2.isLockFilesEnabled = lockFilesEnabled;

        const handleFileSelectWithoutPropagationSpy = jest.spyOn(comp2, 'handleFileSelectWithoutPropagation');

        fixture1.detectChanges();
        fixture2.detectChanges();

        comp1.handleFileSelect(selectedFile.file, selectedFile.idx);

        fixture1.detectChanges();
        fixture2.detectChanges();

        expect(handleFileSelectWithoutPropagationSpy).toHaveBeenCalledWith(selectedFile.file, selectedFile.idx);
    });

    it('should not sync file selection when lockFilesEnabled true', () => {
        const idx = 0;
        const selectedFile = { idx: idx, file: files[idx] };
        const lockFilesEnabled = false;

        comp1.isLockFilesEnabled = lockFilesEnabled;
        comp2.isLockFilesEnabled = lockFilesEnabled;

        const handleFileSelectWithoutPropagationSpy = jest.spyOn(comp1, 'handleFileSelectWithoutPropagation');
        fixture1.detectChanges();
        fixture2.detectChanges();
        comp1.handleFileSelect(selectedFile.file, selectedFile.idx);
        fixture1.detectChanges();
        fixture2.detectChanges();

        expect(handleFileSelectWithoutPropagationSpy).not.toHaveBeenCalled();
    });
});
