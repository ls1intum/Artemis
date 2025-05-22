import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from '../../../../shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { ProgrammingExerciseGitDiffReport } from 'app/programming/shared/entities/programming-exercise-git-diff-report.model';
import { ProgrammingExerciseGitDiffEntry } from 'app/programming/shared/entities/programming-exercise-git-diff-entry.model';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { RepositoryDiffInformation } from 'app/shared/monaco-editor/diff-editor/util/monaco-diff-editor.util';
describe('ProgrammingExerciseGitDiffReport Component', () => {
    let comp: GitDiffReportComponent;
    let fixture: ComponentFixture<GitDiffReportComponent>;

    const mockDiffInformation = {
        diffInformations: [
            {
                originalFileContent: 'testing line differences',
                modifiedFileContent: 'testing line diff\nnew line',
                originalPath: 'Example.java',
                modifiedPath: 'Example.java',
                diffReady: false,
                fileStatus: 'unchanged',
                lineChange: {
                    addedLineCount: 2,
                    removedLineCount: 1,
                },
                title: 'Example.java',
            },
            {
                originalFileContent: 'public class Test {\n    private String name;\n}',
                modifiedFileContent: 'public class Test {\n    private String name;\n    private int age;\n}',
                originalPath: 'Test.java',
                modifiedPath: 'Test.java',
                diffReady: false,
                fileStatus: 'unchanged',
                lineChange: {
                    addedLineCount: 1,
                    removedLineCount: 0,
                },
                title: 'Test.java',
            },
            {
                originalFileContent: '',
                modifiedFileContent: 'public class NewFile {\n    public void doSomething() {\n        System.out.println("Hello");\n    }\n}',
                originalPath: 'NewFile.java',
                modifiedPath: 'NewFile.java',
                diffReady: false,
                fileStatus: 'created',
                lineChange: {
                    addedLineCount: 5,
                    removedLineCount: 0,
                },
                title: 'NewFile.java',
            },
        ],
        totalLineChange: {
            addedLineCount: 8,
            removedLineCount: 1,
        },
    } as unknown as RepositoryDiffInformation;

    beforeEach(() => {
        TestBed.configureTestingModule({
            declarations: [GitDiffReportComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffLineStatComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('repositoryDiffInformation', mockDiffInformation);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should record for each path whether the diff is ready', () => {
        fixture.detectChanges();
        // Initialization
        expect(comp.allDiffsReady()).toBeFalse();
        expect(comp.repositoryDiffInformation().diffInformations[0].diffReady).toBeFalse();
        expect(comp.repositoryDiffInformation().diffInformations[1].diffReady).toBeFalse();
        // First file ready
        comp.onDiffReady(mockDiffInformation.diffInformations[0].modifiedPath, true);
        expect(comp.allDiffsReady()).toBeFalse();
        expect(comp.repositoryDiffInformation().diffInformations[0].diffReady).toBeTrue();
        expect(comp.repositoryDiffInformation().diffInformations[1].diffReady).toBeFalse();
        // Second file ready
        comp.onDiffReady(mockDiffInformation.diffInformations[1].modifiedPath, true);
        expect(comp.allDiffsReady()).toBeFalse();
        expect(comp.repositoryDiffInformation().diffInformations[0].diffReady).toBeTrue();
        expect(comp.repositoryDiffInformation().diffInformations[1].diffReady).toBeTrue();
        // Third file ready
        comp.onDiffReady(mockDiffInformation.diffInformations[2].modifiedPath, true);
        expect(comp.allDiffsReady()).toBeTrue();
        expect(comp.repositoryDiffInformation().diffInformations[0].diffReady).toBeTrue();
        expect(comp.repositoryDiffInformation().diffInformations[1].diffReady).toBeTrue();
        expect(comp.repositoryDiffInformation().diffInformations[2].diffReady).toBeTrue();
    });

    it('should correctly identify renamed files', () => {
        const originalFilePath1 = 'src/original-a.java';
        const originalFilePath2 = 'src/original-b.java';
        const renamedFilePath1 = 'src/renamed-without-changes.java';
        const renamedFilePath2 = 'src/renamed-with-changes.java';
        const notRenamedFilePath = 'src/not-renamed.java';
        const entries: ProgrammingExerciseGitDiffEntry[] = [
            { filePath: renamedFilePath1, previousFilePath: originalFilePath1 },
            { filePath: renamedFilePath2, previousFilePath: originalFilePath2, startLine: 1 },
            { filePath: notRenamedFilePath, previousFilePath: notRenamedFilePath, startLine: 1 },
        ];
        const defaultContent = 'some content that might change';
        const modifiedContent = 'some content that has changed';
        fixture.componentRef.setInput('report', { entries } as ProgrammingExerciseGitDiffReport);
        const originalFileContents = new Map<string, string>();
        const modifiedFileContents = new Map<string, string>();
        [originalFilePath1, originalFilePath2, notRenamedFilePath].forEach((path) => {
            originalFileContents.set(path, defaultContent);
            modifiedFileContents.set(path, path === originalFilePath1 ? defaultContent : modifiedContent);
        });
        fixture.componentRef.setInput('templateFileContentByPath', originalFileContents);
        fixture.componentRef.setInput('solutionFileContentByPath', modifiedFileContents);

        fixture.detectChanges();

        expect(comp.renamedFilePaths()).toEqual({ [renamedFilePath1]: originalFilePath1, [renamedFilePath2]: originalFilePath2 });
    });
});
