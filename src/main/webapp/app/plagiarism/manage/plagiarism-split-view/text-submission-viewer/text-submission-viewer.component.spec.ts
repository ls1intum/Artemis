import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService } from 'app/shared/service/local-storage.service';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { Subject, of, throwError } from 'rxjs';
import { TextSubmissionViewerComponent } from 'app/plagiarism/manage/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { CodeEditorRepositoryFileService } from 'app/programming/shared/code-editor/services/code-editor-repository.service';
import { TextSubmissionService } from 'app/text/overview/service/text-submission.service';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { DomainChange, DomainType, FileType } from 'app/programming/shared/code-editor/model/code-editor.model';
import { PlagiarismSubmission } from 'app/plagiarism/shared/entities/PlagiarismSubmission';
import { SplitPaneHeaderComponent } from 'app/plagiarism/manage/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { AccountService } from 'app/core/auth/account.service';
import { MockAccountService } from 'test/helpers/mocks/service/mock-account.service';
import { PlagiarismSubmissionElement } from 'app/plagiarism/shared/entities/PlagiarismSubmissionElement';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { PlagiarismFileElement } from '../../../shared/entities/PlagiarismFileElement';

describe('Text Submission Viewer Component', () => {
    let comp: TextSubmissionViewerComponent;
    let fixture: ComponentFixture<TextSubmissionViewerComponent>;
    let repositoryService: CodeEditorRepositoryFileService;
    let textSubmissionService: TextSubmissionService;

    const files = {
        'src/': FileType.FOLDER,
        'src/Main.java': FileType.FILE,
        'src/Utils.java': FileType.FILE,
        'src/Helper.java': FileType.FILE,
        'src/image.png': FileType.FILE,
    };

    beforeEach(async () => {
        TestBed.configureTestingModule({
            declarations: [TextSubmissionViewerComponent, MockComponent(SplitPaneHeaderComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                LocalStorageService,
                SessionStorageService,
                { provide: AccountService, useClass: MockAccountService },
                { provide: TranslateService, useClass: MockTranslateService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextSubmissionViewerComponent);
        comp = fixture.componentInstance;
        repositoryService = TestBed.inject(CodeEditorRepositoryFileService);
        textSubmissionService = TestBed.inject(TextSubmissionService);

        fixture.componentRef.setInput('plagiarismSubmission', { submissionId: 1 } as PlagiarismSubmission);
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        fixture.componentRef.setInput('fileSelectedSubject', new Subject<PlagiarismFileElement>());
        fixture.componentRef.setInput('showFilesSubject', new Subject<boolean>());
        fixture.componentRef.setInput('dropdownHoverSubject', new Subject<PlagiarismFileElement>());
        fixture.componentRef.setInput('matches', new Map());

        await fixture.whenStable();
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('fetches a text submission', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        jest.spyOn(textSubmissionService, 'getTextSubmission').mockReturnValue(of({ text: 'Test' }));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });
        expect(textSubmissionService.getTextSubmission).toHaveBeenCalledWith(2);
        expect(comp.isProgrammingExercise).toBeFalse();
    });

    it('fetches a programming submission', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        jest.spyOn(repositoryService, 'getRepositoryContentForPlagiarismView').mockReturnValue(of({}));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContentForPlagiarismView).toHaveBeenCalledOnce();
        expect(comp.isProgrammingExercise).toBeTrue();
        expect(comp.cannotLoadFiles).toBeFalse();
    });

    it('does not fetch a programming submission', () => {
        jest.spyOn(repositoryService, 'getRepositoryContentForPlagiarismView').mockReturnValue(of({}));
        fixture.componentRef.setInput('hideContent', true);

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContentForPlagiarismView).not.toHaveBeenCalled();
    });

    it('handles a programming submission fetch error', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        jest.spyOn(repositoryService, 'getRepositoryContentForPlagiarismView').mockReturnValue(throwError(() => {}));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContentForPlagiarismView).toHaveBeenCalledOnce();
        expect(comp.cannotLoadFiles).toBeTrue();
    });

    it('sorts and filters the files when fetching a programming submission', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('matches', new Map());

        const filesUnordered = {
            'a/': FileType.FOLDER,
            z: FileType.FILE,
            kContinuedName: FileType.FILE,
            'd/': FileType.FOLDER,
            b_file: FileType.FILE,
            e: FileType.FILE,
        };

        comp.matches()?.set('e', [{ from: new PlagiarismSubmissionElement(), to: new PlagiarismSubmissionElement() }]);
        comp.matches()?.set('kContinuedName', [{ from: new PlagiarismSubmissionElement(), to: new PlagiarismSubmissionElement() }]);

        jest.spyOn(repositoryService, 'getRepositoryContentForPlagiarismView').mockReturnValue(of(filesUnordered));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContentForPlagiarismView).toHaveBeenCalledOnce();
        expect(comp.isProgrammingExercise).toBeTrue();

        // files with matches first, then the ones without match; each section ordered lexicographically
        const expectedFiles = [
            { file: 'e', hasMatch: true },
            { file: 'kContinuedName', hasMatch: true },
            { file: 'b_file', hasMatch: false },
            { file: 'z', hasMatch: false },
        ];
        expect(comp.files).toEqual(expectedFiles);
    });

    it('filters files of type FILE', () => {
        const filtered = comp.filterFiles(files);

        expect(filtered).toHaveLength(4);
        expect(filtered).not.toContain('src/');
    });

    it('handles file selection', async () => {
        const submissionId = 1;

        fixture.changeDetectorRef.detectChanges();

        const fileName = Object.keys(files)[1];

        jest.spyOn(repositoryService, 'getFileForPlagiarismView').mockReturnValue(of({ fileContent: 'if(current>max)' }));

        comp.handleFileSelect(fileName);
        await fixture.whenStable();
        fixture.changeDetectorRef.detectChanges();

        const expectedDomain: DomainChange = [DomainType.PARTICIPATION, { id: submissionId }];
        expect(repositoryService.getFileForPlagiarismView).toHaveBeenCalledWith(fileName, expectedDomain);
        expect(comp.currentFile).toEqual(fileName);
        expect(comp.fileContent).toBe('if(current&gt;max)');
    });

    it('handles binary file selection', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.componentRef.setInput('plagiarismSubmission', { submissionId: 1 } as PlagiarismSubmission);
        fixture.changeDetectorRef.detectChanges();
        const fileName = Object.keys(files)[4];
        jest.spyOn(repositoryService, 'getFileForPlagiarismView').mockReturnValue(of({ fileContent: 'Test' }));

        comp.handleFileSelect(fileName);

        expect(repositoryService.getFileForPlagiarismView).not.toHaveBeenCalled();
        expect(comp.currentFile).toEqual(fileName);
    });

    it('should insert exact match tokens', () => {
        const mockMatches = [
            {
                from: {
                    column: 1,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;
        const expectedFileContent = `<span class="plagiarism-match">Lorem ipsum dolor </span>sit amet.\n<span class="plagiarism-match">Consetetur sadipscing elitr.</span>`;
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should insert full line match tokens', () => {
        const mockMatches = [
            {
                from: {
                    column: 1,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.\nAt vero eos et accusam et justo duo.`;
        const expectedFileContent = `<span class="plagiarism-match">Lorem ipsum dolor sit amet.</span>\n<span class="plagiarism-match">Consetetur sadipscing elitr.</span>\nAt vero eos et accusam et justo duo.`;
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should escape the text if no matches are present', () => {
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue([]);
        const fileContent = 'Lorem ipsum dolor sit amet.\n<test>';
        const expectedFileContent = 'Lorem ipsum dolor sit amet.\n&lt;test&gt;';

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should escape and insert exact tokens', () => {
        const mockMatches = [
            {
                from: {
                    column: 6,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.\n<test> test text for inserting tokens';
        const expectedFileContent =
            'Lorem<span class="plagiarism-match"> ipsum &lt;fake-</span>token&gt;dolor sit amet.\n' +
            '<span class="plagiarism-match">&lt;test&gt; test text for inserti</span>ng tokens';
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should escape and insert full line tokens', () => {
        const mockMatches = [
            {
                from: {
                    column: 6,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.\n<test> test text for inserting tokens';
        const expectedFileContent =
            '<span class="plagiarism-match">Lorem ipsum &lt;fake-token&gt;dolor sit amet.</span>\n' +
            '<span class="plagiarism-match">&lt;test&gt; test text for inserting tokens</span>';
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should insert exact tokens for multiple matches in one line', () => {
        const mockMatches = [
            {
                from: {
                    column: 20,
                    line: 1,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 30,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 5,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.';
        const expectedFileContent = '<span class="plagiarism-match">Lorem ipsu</span>m &lt;fake-t<span class="plagiarism-match">oken&gt;dolor sit a</span>met.';
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should insert full line tokens for multiple matches in one line', () => {
        const mockMatches = [
            {
                from: {
                    column: 20,
                    line: 1,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 30,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 5,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.';
        const expectedFileContent = '<span class="plagiarism-match">Lorem ipsum &lt;fake-token&gt;dolor sit amet.</span>';
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should insert exact tokens for multiple line matches', () => {
        const mockMatches = [
            {
                from: {
                    column: 20,
                    line: 1,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: {
                    column: 30,
                    line: 2,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.\nLorem ipsum <fake-token>dolor sit amet';
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        const expectedFileContent = 'Lorem ipsum &lt;fake-t<span class="plagiarism-match">oken&gt;dolor sit amet.\nLorem ipsum &lt;fake-token&gt;dolor sit a</span>met';

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should return a non-empty string even if matches have undefined "from" and "to" values for exact matches', () => {
        const mockMatches = [
            {
                from: undefined as unknown as PlagiarismSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: undefined as unknown as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT } as Exercise);
        fixture.changeDetectorRef.detectChanges();

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;
        const expectedFileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should return a non-empty string even if matches have undefined "from" and "to" values for full line matches', () => {
        const mockMatches = [
            {
                from: undefined as unknown as PlagiarismSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as PlagiarismSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as PlagiarismSubmissionElement,
                to: undefined as unknown as PlagiarismSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        fixture.componentRef.setInput('exercise', { type: ExerciseType.PROGRAMMING } as Exercise);
        fixture.changeDetectorRef.detectChanges();

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;
        const expectedFileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });
});
