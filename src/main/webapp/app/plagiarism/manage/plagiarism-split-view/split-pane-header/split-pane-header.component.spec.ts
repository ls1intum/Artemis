import { ComponentFixture, TestBed } from '@angular/core/testing';
import { SplitPaneHeaderComponent } from 'app/plagiarism/manage/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { PlagiarismFileElement } from 'app/plagiarism/shared/entities/PlagiarismFileElement';
import { Subject } from 'rxjs';
import { By } from '@angular/platform-browser';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';

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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();
        const fileSelectedSubject = new Subject();
        const showFilesSubject = new Subject<boolean>();
        const dropdownHoverSubject = new Subject();
        fixture1 = TestBed.createComponent(SplitPaneHeaderComponent);
        comp1 = fixture1.componentInstance;
        fixture2 = TestBed.createComponent(SplitPaneHeaderComponent);
        comp2 = fixture2.componentInstance;

        fixture1.componentRef.setInput('studentLogin', 'ts10abc');
        fixture1.componentRef.setInput('fileSelectedSubject', fileSelectedSubject);
        fixture1.componentRef.setInput('showFilesSubject', showFilesSubject);
        fixture1.componentRef.setInput('dropdownHoverSubject', dropdownHoverSubject);
        fixture1.componentRef.setInput('files', files);

        fixture2.componentRef.setInput('studentLogin', 'ts10abc');
        fixture2.componentRef.setInput('fileSelectedSubject', fileSelectedSubject);
        fixture2.componentRef.setInput('showFilesSubject', showFilesSubject);
        fixture2.componentRef.setInput('dropdownHoverSubject', dropdownHoverSubject);
        fixture2.componentRef.setInput('files', files);
    });

    it('selects the first file on change', () => {
        const emitSpy = jest.spyOn(comp1.selectFile, 'emit');
        fixture1.detectChanges();
        expect(emitSpy).toHaveBeenCalledOnce();
        expect(emitSpy).toHaveBeenCalledWith(files[0].file);
    });

    it('does not find an active file', () => {
        fixture2.componentRef.setInput('files', []);
        fixture2.detectChanges();
        const activeFile = comp2.hasActiveFile();

        expect(activeFile).toBeFalse();
    });

    it('returns the active file', () => {
        const activeFile = comp1.getActiveFile();

        expect(activeFile).toBe(files[0].file);
    });

    it('handles selection of a file', () => {
        const idx = 1;
        comp1.showFiles = true;
        jest.spyOn(comp1.selectFile, 'emit');

        comp1.handleFileSelect(files[idx], idx, true);

        expect(comp1.activeFileIndex).toBe(idx);
        expect(comp1.showFiles).toBeFalse();
        expect(comp1.selectFile.emit).toHaveBeenCalledOnce();
        expect(comp1.selectFile.emit).toHaveBeenCalledWith(files[idx].file);
    });

    it('has no files', () => {
        fixture2.componentRef.setInput('files', []);
        expect(comp2.hasFiles()).toBeFalse();
    });

    it('has files', () => {
        expect(comp1.hasFiles()).toBeTrue();
    });

    it('toggles "show files"', () => {
        comp1.showFiles = false;

        comp1.toggleShowFiles(false);

        expect(comp1.showFiles).toBeTrue();
    });

    it('does not toggle "show files"', () => {
        comp2.showFiles = false;

        comp2.toggleShowFiles(false);

        expect(comp1.showFiles).toBeFalse();
    });

    it('should emit selected file through fileSelectedSubject', () => {
        const selectedFile = { idx: 0, file: files[0] };
        let emittedFile: PlagiarismFileElement | undefined;
        comp1.fileSelectedSubject()!.subscribe((file) => {
            emittedFile = file;
        });

        comp1.fileSelectedSubject()!.next(selectedFile);

        // Assert
        expect(emittedFile).toBe(selectedFile);
    });

    it('should sync file selection when lockFilesEnabled true', () => {
        const idx = 0;
        const selectedFile = { idx: idx, file: files[idx] };
        const lockFilesEnabled = true;
        comp1.showFiles = true;
        comp2.showFiles = true;
        fixture1.componentRef.setInput('isLockFilesEnabled', lockFilesEnabled);
        fixture2.componentRef.setInput('isLockFilesEnabled', lockFilesEnabled);

        fixture1.changeDetectorRef.detectChanges();
        fixture2.changeDetectorRef.detectChanges();

        const handleFileSelectWithoutPropagationSpy = jest.spyOn(comp2, 'handleFileSelect');

        comp1.handleFileSelect(selectedFile.file, selectedFile.idx, true);

        fixture1.changeDetectorRef.detectChanges();
        fixture2.changeDetectorRef.detectChanges();

        expect(handleFileSelectWithoutPropagationSpy).toHaveBeenCalledExactlyOnceWith(selectedFile.file, selectedFile.idx, false);
    });

    it('should not sync file selection when lockFilesEnabled false', () => {
        const idx = 0;
        const selectedFile = { idx: idx, file: files[idx] };
        const lockFilesEnabled = false;

        fixture1.componentRef.setInput('isLockFilesEnabled', lockFilesEnabled);
        fixture2.componentRef.setInput('isLockFilesEnabled', lockFilesEnabled);

        const handleFileSelect = jest.spyOn(comp1, 'handleFileSelect');
        const handleFileSelectWithoutPropagationSpy = jest.spyOn(comp2, 'handleFileSelect');
        comp1.handleFileSelect(selectedFile.file, selectedFile.idx, true);
        fixture1.changeDetectorRef.detectChanges();
        fixture2.changeDetectorRef.detectChanges();

        expect(handleFileSelectWithoutPropagationSpy).toHaveBeenCalledTimes(0);
        expect(handleFileSelect).toHaveBeenCalledOnce();
    });

    it('should trigger dropdown hover subject on mouseenter on the first file element', () => {
        comp1.showFiles = true;
        fixture1.changeDetectorRef.detectChanges();

        const fileList = fixture1.debugElement.query(By.css('.split-pane-header-files'));
        expect(fileList).toBeTruthy();

        const fileItems = fileList.queryAll(By.css('.split-pane-header-file'));
        expect(fileItems.length).toBeGreaterThan(0);

        const firstFileItem = fileItems[0];
        const triggerMouseEnterSpy = jest.spyOn(comp1 as any, 'triggerMouseEnter');

        firstFileItem.triggerEventHandler('mouseenter', null);
        expect(triggerMouseEnterSpy).toHaveBeenCalledWith(comp1.files()[0], 0);
    });

    it('should create dropdownHoverSubject on ngOnInit', () => {
        // Arrange
        const mockFile = files[0];
        const mockIdx = 0;

        fixture1.componentRef.setInput('isLockFilesEnabled', true);
        jest.spyOn(comp1 as any, 'handleDropdownHover');

        comp1.ngOnInit();
        comp1.dropdownHoverSubject()!.next({ file: mockFile, idx: mockIdx });

        expect(comp1['handleDropdownHover']).toHaveBeenCalledWith(mockFile, mockIdx);
        expect(comp1.hoveredFileIndex).toBe(mockIdx);
    });

    it('should update showFiles when hasFiles returns true', () => {
        comp1.hasFiles = jest.fn().mockReturnValue(true);
        const initialShowFiles = comp1.showFiles;

        comp1.toggleShowFiles(false);

        expect(comp1.showFiles).not.toBe(initialShowFiles);
    });

    it('should not update showFiles when hasFiles returns false', () => {
        comp1.hasFiles = jest.fn().mockReturnValue(false);
        const initialShowFiles = comp1.showFiles;

        comp1.toggleShowFiles(false);

        expect(comp1.showFiles).toBe(initialShowFiles);
    });

    it('should set hoveredFileIdx to -1 if file does not match and getIndexOf returns -1', () => {
        const mockFile = { file: 'testFile', hasMatch: true };
        const mockIdx = files.length;

        jest.spyOn(comp1 as any, 'getIndexOf').mockReturnValue(-1);

        (comp1 as any).handleDropdownHover(mockFile, mockIdx);

        expect(comp1.hoveredFileIndex).toBe(-1);
    });
});
