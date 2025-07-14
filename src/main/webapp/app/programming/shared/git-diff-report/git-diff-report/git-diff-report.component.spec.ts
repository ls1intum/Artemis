import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ArtemisTranslatePipe } from '../../../../shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FileStatus, RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { LocalStorageService } from 'ngx-webstorage';
import { MockLocalStorageService } from 'test/helpers/mocks/service/mock-local-storage.service';
import { captureException } from '@sentry/angular';

// Mock @sentry/angular module
jest.mock('@sentry/angular', () => ({
    captureException: jest.fn(),
}));

class MockResizeObserver {
    observe() {}
    unobserve() {}
    disconnect() {}
}

describe('ProgrammingExerciseGitDiffReport Component', () => {
    let comp: GitDiffReportComponent;
    let fixture: ComponentFixture<GitDiffReportComponent>;

    beforeAll(() => {
        global.ResizeObserver = MockResizeObserver;
    });

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
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: LocalStorageService, useClass: MockLocalStorageService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
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
        const defaultContent = 'some content that might change';
        const modifiedContent = 'some content that has changed';

        const repositoryDiffInformation: RepositoryDiffInformation = {
            diffInformations: [
                {
                    originalFileContent: defaultContent,
                    modifiedFileContent: defaultContent,
                    originalPath: originalFilePath1,
                    modifiedPath: renamedFilePath1,
                    diffReady: false,
                    fileStatus: FileStatus.RENAMED,
                    lineChange: {
                        addedLineCount: 0,
                        removedLineCount: 0,
                    },
                    title: renamedFilePath1,
                },
                {
                    originalFileContent: defaultContent,
                    modifiedFileContent: modifiedContent,
                    originalPath: originalFilePath2,
                    modifiedPath: renamedFilePath2,
                    diffReady: false,
                    fileStatus: FileStatus.RENAMED,
                    lineChange: {
                        addedLineCount: 1,
                        removedLineCount: 0,
                    },
                    title: renamedFilePath2,
                },
                {
                    originalFileContent: defaultContent,
                    modifiedFileContent: defaultContent,
                    originalPath: notRenamedFilePath,
                    modifiedPath: notRenamedFilePath,
                    diffReady: false,
                    fileStatus: FileStatus.UNCHANGED,
                    lineChange: {
                        addedLineCount: 0,
                        removedLineCount: 0,
                    },
                    title: notRenamedFilePath,
                },
            ],
            totalLineChange: {
                addedLineCount: 1,
                removedLineCount: 0,
            },
        };

        fixture.componentRef.setInput('repositoryDiffInformation', repositoryDiffInformation);

        fixture.detectChanges();

        // Assert that the diff information contains the correct number of entries
        expect(comp.repositoryDiffInformation().diffInformations).toHaveLength(3);

        // Assert that renamed files have the correct file status, paths and titles
        const firstDiff = comp.repositoryDiffInformation().diffInformations[0];
        const secondDiff = comp.repositoryDiffInformation().diffInformations[1];
        const thirdDiff = comp.repositoryDiffInformation().diffInformations[2];

        // First renamed file without changes
        expect(firstDiff.fileStatus).toBe(FileStatus.RENAMED);
        expect(firstDiff.originalPath).toBe(originalFilePath1);
        expect(firstDiff.modifiedPath).toBe(renamedFilePath1);
        expect(firstDiff.title).toBe(renamedFilePath1);
        expect(firstDiff.originalFileContent).toBe(firstDiff.modifiedFileContent);

        // Second renamed file with changes
        expect(secondDiff.fileStatus).toBe(FileStatus.RENAMED);
        expect(secondDiff.originalPath).toBe(originalFilePath2);
        expect(secondDiff.modifiedPath).toBe(renamedFilePath2);
        expect(secondDiff.title).toBe(renamedFilePath2);
        expect(secondDiff.originalFileContent).not.toBe(secondDiff.modifiedFileContent);

        // Unchanged file
        expect(thirdDiff.fileStatus).toBe(FileStatus.UNCHANGED);
        expect(thirdDiff.originalPath).toBe(thirdDiff.modifiedPath);
        expect(thirdDiff.originalPath).toBe(notRenamedFilePath);
    });

    it('should handle diff ready event for unknown path and capture exception', () => {
        fixture.detectChanges();

        // Call onDiffReady with a path that doesn't exist in the mock data
        comp.onDiffReady('unknown-path.java', true);

        // Verify that captureException was called
        expect(captureException).toHaveBeenCalledWith('Received diff ready event for unknown title: unknown-path.java');

        // Verify that allDiffsReady is still false since we have valid files that are not ready
        expect(comp.allDiffsReady()).toBeFalse();
    });

    it('should test handleDiffReady method', () => {
        const onDiffReadySpy = jest.spyOn(comp, 'onDiffReady');

        fixture.detectChanges();

        // Call handleDiffReady
        comp.handleDiffReady('Test.java', true);

        // Verify that onDiffReady was called with the same parameters
        expect(onDiffReadySpy).toHaveBeenCalledWith('Test.java', true);
    });

    it('should test computed properties', () => {
        fixture.detectChanges();

        // Test addedLineCount and removedLineCount
        expect(comp.addedLineCount()).toBe(8);
        expect(comp.removedLineCount()).toBe(1);
    });

    it('should test nothingToDisplay computed property when no diff informations', () => {
        const emptyDiffInformation = {
            diffInformations: [],
            totalLineChange: {
                addedLineCount: 0,
                removedLineCount: 0,
            },
        } as RepositoryDiffInformation;

        fixture.componentRef.setInput('repositoryDiffInformation', emptyDiffInformation);
        fixture.detectChanges();

        expect(comp.nothingToDisplay()).toBeTrue();
        expect(comp.addedLineCount()).toBe(0);
        expect(comp.removedLineCount()).toBe(0);
    });

    it('should test commit hash computed properties', () => {
        const leftHash = 'abcdef1234567890abcdef1234567890abcdef12';
        const rightHash = '1234567890abcdef1234567890abcdef12345678';

        fixture.componentRef.setInput('leftCommitHash', leftHash);
        fixture.componentRef.setInput('rightCommitHash', rightHash);
        fixture.detectChanges();

        // Test that leftCommit and rightCommit return the first 10 characters
        expect(comp.leftCommit()).toBe('abcdef1234');
        expect(comp.rightCommit()).toBe('1234567890');
    });

    it('should handle undefined commit hashes', () => {
        fixture.componentRef.setInput('leftCommitHash', undefined);
        fixture.componentRef.setInput('rightCommitHash', undefined);
        fixture.detectChanges();

        // Test that computed properties handle undefined values
        expect(comp.leftCommit()).toBeUndefined();
        expect(comp.rightCommit()).toBeUndefined();
    });

    it('should test optional input properties', () => {
        fixture.componentRef.setInput('diffForTemplateAndSolution', false);
        fixture.componentRef.setInput('diffForTemplateAndEmptyRepository', true);
        fixture.componentRef.setInput('isRepositoryView', true);
        fixture.componentRef.setInput('participationId', 123);
        fixture.detectChanges();

        // Verify the inputs are set correctly
        expect(comp.diffForTemplateAndSolution()).toBeFalse();
        expect(comp.diffForTemplateAndEmptyRepository()).toBeTrue();
        expect(comp.isRepositoryView()).toBeTrue();
        expect(comp.participationId()).toBe(123);
    });
});
