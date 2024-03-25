import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { TextSubmissionViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { Exercise, ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { DomainChange, DomainType, FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';
import { HttpHeaders, HttpResponse } from '@angular/common/http';
import { SplitPaneHeaderComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/split-pane-header/split-pane-header.component';
import { MockComponent, MockPipe } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';

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
    };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [TextSubmissionViewerComponent, MockComponent(SplitPaneHeaderComponent), MockPipe(ArtemisTranslatePipe)],
            providers: [
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextSubmissionViewerComponent);
        comp = fixture.componentInstance;
        repositoryService = TestBed.inject(CodeEditorRepositoryFileService);
        textSubmissionService = TestBed.inject(TextSubmissionService);
    });

    afterEach(() => {
        jest.resetAllMocks();
    });

    it('fetches a text submission', () => {
        comp.exercise = { type: ExerciseType.TEXT } as TextExercise;
        jest.spyOn(textSubmissionService, 'getTextSubmission').mockReturnValue(of({ text: 'Test' }));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(textSubmissionService.getTextSubmission).toHaveBeenCalledWith(2);
        expect(comp.isProgrammingExercise).toBeFalse();
    });

    it('fetches a programming submission', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
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
        comp.hideContent = true;

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContentForPlagiarismView).not.toHaveBeenCalled();
    });

    it('handles a programming submission fetch error', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
        jest.spyOn(repositoryService, 'getRepositoryContentForPlagiarismView').mockReturnValue(throwError({}));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContentForPlagiarismView).toHaveBeenCalledOnce();
        expect(comp.cannotLoadFiles).toBeTrue();
    });

    it('sorts and filters the files when fetching a programming submission', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as ProgrammingExercise;

        const filesUnordered = {
            'a/': FileType.FOLDER,
            z: FileType.FILE,
            kContinuedName: FileType.FILE,
            'd/': FileType.FOLDER,
            b_file: FileType.FILE,
            e: FileType.FILE,
        };

        comp.matches = new Map();
        comp.matches.set('e', [{ from: new TextSubmissionElement(), to: new TextSubmissionElement() }]);
        comp.matches.set('kContinuedName', [{ from: new TextSubmissionElement(), to: new TextSubmissionElement() }]);

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

        expect(filtered).toHaveLength(3);
        expect(filtered).not.toContain('src/');
    });

    it('handles file selection', () => {
        const submissionId = 1;
        comp.plagiarismSubmission = { submissionId } as PlagiarismSubmission<TextSubmissionElement>;

        const fileName = Object.keys(files)[1];
        comp.matches = new Map();
        const expectedHeaders = new HttpHeaders().append('content-type', 'text/plain');
        jest.spyOn(repositoryService, 'getFileHeaders').mockReturnValue(of(new HttpResponse<Blob>({ headers: expectedHeaders })));
        jest.spyOn(repositoryService, 'getFileForPlagiarismView').mockReturnValue(of({ fileContent: 'if(current>max)' }));

        comp.handleFileSelect(fileName);

        const expectedDomain: DomainChange = [DomainType.PARTICIPATION, { id: submissionId }];
        expect(repositoryService.getFileForPlagiarismView).toHaveBeenCalledWith(fileName, expectedDomain);
        expect(comp.currentFile).toEqual(fileName);
        expect(comp.fileContent).toBe('if(current&gt;max)');
    });

    it('handles binary file selection', () => {
        comp.plagiarismSubmission = { submissionId: 1 } as PlagiarismSubmission<TextSubmissionElement>;

        const fileName = Object.keys(files)[1];
        const expectedHeaders = new HttpHeaders().append('content-type', 'audio/mpeg');
        jest.spyOn(repositoryService, 'getFileHeaders').mockReturnValue(of(new HttpResponse<Blob>({ headers: expectedHeaders })));
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
                } as TextSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as TextSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;
        const expectedFileContent = `<span class="plagiarism-match">Lorem ipsum dolor </span>sit amet.\n<span class="plagiarism-match">Consetetur sadipscing elitr.</span>`;
        comp.exercise = { type: ExerciseType.TEXT } as unknown as Exercise;

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
                } as TextSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as TextSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.\nAt vero eos et accusam et justo duo.`;
        const expectedFileContent = `<span class="plagiarism-match">Lorem ipsum dolor sit amet.</span>\n<span class="plagiarism-match">Consetetur sadipscing elitr.</span>\nAt vero eos et accusam et justo duo.`;
        comp.exercise = { type: ExerciseType.PROGRAMMING } as unknown as Exercise;

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
                } as TextSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as TextSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.\n<test> test text for inserting tokens';
        const expectedFileContent =
            'Lorem<span class="plagiarism-match"> ipsum &lt;fake-</span>token&gt;dolor sit amet.\n' +
            '<span class="plagiarism-match">&lt;test&gt; test text for inserti</span>ng tokens';
        comp.exercise = { type: ExerciseType.TEXT } as unknown as Exercise;

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
                } as TextSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as TextSubmissionElement,
                to: {
                    column: 23,
                    line: 2,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.\n<test> test text for inserting tokens';
        const expectedFileContent =
            '<span class="plagiarism-match">Lorem ipsum &lt;fake-token&gt;dolor sit amet.</span>\n' +
            '<span class="plagiarism-match">&lt;test&gt; test text for inserting tokens</span>';
        comp.exercise = { type: ExerciseType.PROGRAMMING } as unknown as Exercise;

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
                } as TextSubmissionElement,
                to: {
                    column: 30,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
                to: {
                    column: 5,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.';
        const expectedFileContent = '<span class="plagiarism-match">Lorem ipsu</span>m &lt;fake-t<span class="plagiarism-match">oken&gt;dolor sit a</span>met.';
        comp.exercise = { type: ExerciseType.TEXT } as unknown as Exercise;

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
                } as TextSubmissionElement,
                to: {
                    column: 30,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
                to: {
                    column: 5,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.';
        const expectedFileContent = '<span class="plagiarism-match">Lorem ipsum &lt;fake-token&gt;dolor sit amet.</span>';
        comp.exercise = { type: ExerciseType.PROGRAMMING } as unknown as Exercise;

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
                } as TextSubmissionElement,
                to: {
                    column: 30,
                    line: 2,
                    length: 5,
                } as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        const fileContent = 'Lorem ipsum <fake-token>dolor sit amet.\nLorem ipsum <fake-token>dolor sit amet';
        comp.exercise = { type: ExerciseType.TEXT } as unknown as Exercise;
        const expectedFileContent = 'Lorem ipsum &lt;fake-t<span class="plagiarism-match">oken&gt;dolor sit amet.\nLorem ipsum &lt;fake-token&gt;dolor sit a</span>met';

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should return a non-empty string even if matches have undefined "from" and "to" values for exact matches', () => {
        const mockMatches = [
            {
                from: undefined as unknown as TextSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as TextSubmissionElement,
                to: undefined as unknown as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        comp.exercise = { type: ExerciseType.TEXT } as unknown as Exercise;

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;
        const expectedFileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });

    it('should return a non-empty string even if matches have undefined "from" and "to" values for full line matches', () => {
        const mockMatches = [
            {
                from: undefined as unknown as TextSubmissionElement,
                to: {
                    column: 13,
                    line: 1,
                    length: 5,
                } as TextSubmissionElement,
            },
            {
                from: {
                    column: 1,
                    line: 2,
                    length: 10,
                } as TextSubmissionElement,
                to: undefined as unknown as TextSubmissionElement,
            },
        ];
        jest.spyOn(comp, 'getMatchesForCurrentFile').mockReturnValue(mockMatches);
        comp.exercise = { type: ExerciseType.PROGRAMMING } as unknown as Exercise;

        const fileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;
        const expectedFileContent = `Lorem ipsum dolor sit amet.\nConsetetur sadipscing elitr.`;

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });
});
