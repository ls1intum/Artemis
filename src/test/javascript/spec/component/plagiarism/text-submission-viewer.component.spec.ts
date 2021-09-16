import { SimpleChange } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateService } from '@ngx-translate/core';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { of } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { MockTranslateService, TranslateTestingModule } from '../../helpers/mocks/service/mock-translate.service';
import { ArtemisPlagiarismModule } from 'app/exercises/shared/plagiarism/plagiarism.module';
import { TextSubmissionViewerComponent } from 'app/exercises/shared/plagiarism/plagiarism-split-view/text-submission-viewer/text-submission-viewer.component';
import { CodeEditorRepositoryFileService } from 'app/exercises/programming/shared/code-editor/service/code-editor-repository.service';
import { TextSubmissionService } from 'app/exercises/text/participate/text-submission.service';
import { ExerciseType } from 'app/entities/exercise.model';
import { TextExercise } from 'app/entities/text-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { FileType } from 'app/exercises/programming/shared/code-editor/model/code-editor.model';
import { PlagiarismSubmission } from 'app/exercises/shared/plagiarism/types/PlagiarismSubmission';
import { TextSubmissionElement } from 'app/exercises/shared/plagiarism/types/text/TextSubmissionElement';

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
            imports: [ArtemisTestModule, ArtemisPlagiarismModule, TranslateTestingModule],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: SessionStorageService, useClass: MockSyncStorage },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(TextSubmissionViewerComponent);
        comp = fixture.componentInstance;
        repositoryService = fixture.debugElement.injector.get(CodeEditorRepositoryFileService);
        textSubmissionService = fixture.debugElement.injector.get(TextSubmissionService);
    });

    it('fetches a text submission', () => {
        comp.exercise = { type: ExerciseType.TEXT } as TextExercise;
        jest.spyOn(textSubmissionService, 'getTextSubmission').mockReturnValue(of({ text: 'Test' }));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(textSubmissionService.getTextSubmission).toHaveBeenCalledWith(2);
        expect(comp.isProgrammingExercise).toBe(false);
    });

    it('fetches a programming submission', () => {
        comp.exercise = { type: ExerciseType.PROGRAMMING } as ProgrammingExercise;
        jest.spyOn(repositoryService, 'getRepositoryContent').mockReturnValue(of([]));

        comp.ngOnChanges({
            plagiarismSubmission: { currentValue: { submissionId: 2 } } as SimpleChange,
        });

        expect(repositoryService.getRepositoryContent).toHaveBeenCalled();
        expect(comp.isProgrammingExercise).toBe(true);
    });

    it('filters files of type FILE', () => {
        const filtered = comp.filterFiles(files);

        expect(filtered).toHaveLength(3);
    });

    it('handles file selection', () => {
        comp.plagiarismSubmission = { submissionId: 1 } as PlagiarismSubmission<TextSubmissionElement>;

        const fileName = Object.keys(files)[1];
        const expectedHeaders = new Headers([['content-type', 'text/plain']]);
        jest.spyOn(repositoryService, 'getFileHeaders').mockReturnValue(of({ headers: expectedHeaders }));
        jest.spyOn(repositoryService, 'getFile').mockReturnValue(of({ fileContent: 'Test' }));

        comp.handleFileSelect(fileName);

        expect(repositoryService.getFile).toHaveBeenCalledWith(fileName);
        expect(comp.currentFile).toEqual(fileName);
    });

    it('handles binary file selection', () => {
        comp.plagiarismSubmission = { submissionId: 1 } as PlagiarismSubmission<TextSubmissionElement>;

        const fileName = Object.keys(files)[1];
        const expectedHeaders = new Headers([['content-type', 'audio/mpeg']]);
        jest.spyOn(repositoryService, 'getFileHeaders').mockReturnValue(of({ headers: expectedHeaders }));
        jest.spyOn(repositoryService, 'getFile').mockReturnValue(of({ fileContent: 'Test' }));

        comp.handleFileSelect(fileName);

        expect(repositoryService.getFile).toBeCalledTimes(0);
        expect(comp.currentFile).toEqual(fileName);
    });

    it('inserts a token', () => {
        const base = 'This is a test';
        const token = '<token>';
        const position = 4;
        const expectedResult = 'This<token> is a test';

        const result = comp.insertToken(base, token, position);

        expect(result).toEqual(expectedResult);
    });

    it('appends a token', () => {
        const base = 'This is a test';
        const token = '<token>';
        const position = 20;
        const expectedResult = 'This is a test<token>';

        const result = comp.insertToken(base, token, position);

        expect(result).toEqual(expectedResult);
    });

    it('inserts match tokens', () => {
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
        const expectedFileContent = `<span class="plagiarism-match">Lorem ipsum dolor</span> sit amet.\n<span class="plagiarism-match">Consetetur sadipscing elitr</span>.`;

        const updatedFileContent = comp.insertMatchTokens(fileContent);

        expect(updatedFileContent).toEqual(expectedFileContent);
    });
});
