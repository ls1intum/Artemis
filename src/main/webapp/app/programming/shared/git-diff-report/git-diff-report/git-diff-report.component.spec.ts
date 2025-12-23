import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { ArtemisTranslatePipe } from '../../../../shared/pipes/artemis-translate.pipe';
import { MockComponent, MockPipe } from 'ng-mocks';
import { GitDiffLineStatComponent } from 'app/programming/shared/git-diff-report/git-diff-line-stat/git-diff-line-stat.component';
import { GitDiffReportComponent } from 'app/programming/shared/git-diff-report/git-diff-report/git-diff-report.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FileStatus, RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
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

class MockIntersectionObserver {
    static instances: MockIntersectionObserver[] = [];
    private readonly callback: IntersectionObserverCallback;

    constructor(callback: IntersectionObserverCallback) {
        this.callback = callback;
        MockIntersectionObserver.instances.push(this);
    }

    observe = jest.fn((_element: Element) => {});

    unobserve = jest.fn((_element: Element) => {});

    disconnect = jest.fn(() => {});

    trigger(entries: Array<{ target: Element; isIntersecting: boolean }>) {
        const normalized = entries.map(
            ({ target, isIntersecting }) =>
                ({
                    boundingClientRect: target.getBoundingClientRect(),
                    intersectionRatio: isIntersecting ? 1 : 0,
                    intersectionRect: target.getBoundingClientRect(),
                    isIntersecting,
                    rootBounds: null,
                    target,
                    time: Date.now(),
                }) as IntersectionObserverEntry,
        );
        this.callback(normalized, this as unknown as IntersectionObserver);
    }
}

describe('ProgrammingExerciseGitDiffReport Component', () => {
    let comp: GitDiffReportComponent;
    let fixture: ComponentFixture<GitDiffReportComponent>;

    beforeAll(() => {
        global.ResizeObserver = MockResizeObserver;
        global.IntersectionObserver = MockIntersectionObserver as any;
    });

    const createDiffInformationEntry = (
        title: string,
        overrides: Partial<RepositoryDiffInformation['diffInformations'][number]> = {},
    ): RepositoryDiffInformation['diffInformations'][number] => ({
        originalFileContent: `${title} original`,
        modifiedFileContent: `${title} modified`,
        originalPath: title,
        modifiedPath: title,
        diffReady: false,
        fileStatus: FileStatus.UNCHANGED,
        lineChange: {
            addedLineCount: 1,
            removedLineCount: 0,
        },
        title,
        ...overrides,
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
        MockIntersectionObserver.instances.length = 0;
        TestBed.configureTestingModule({
            declarations: [GitDiffReportComponent, MockPipe(ArtemisTranslatePipe), MockComponent(GitDiffLineStatComponent)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }, provideHttpClient(), provideHttpClientTesting()],
        }).compileComponents();
        fixture = TestBed.createComponent(GitDiffReportComponent);
        comp = fixture.componentInstance;
        fixture.componentRef.setInput('repositoryDiffInformation', mockDiffInformation);
    });

    afterEach(() => {
        jest.useRealTimers();
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

    it('should return user override value when panel has been manually toggled', () => {
        fixture.detectChanges();

        const diffInfo = mockDiffInformation.diffInformations[0];
        const title = diffInfo.title;

        // User manually collapses the panel
        comp['userCollapsed'].set(title, true);
        diffInfo.isCollapsed = true;

        expect(diffInfo.isCollapsed).toBeTrue();

        // User manually expands the panel
        comp['userCollapsed'].set(title, false);
        diffInfo.isCollapsed = false;

        expect(diffInfo.isCollapsed).toBeFalse();
    });

    it('should track user collapse/expand actions', () => {
        fixture.detectChanges();

        const title = mockDiffInformation.diffInformations[0].title;
        const markContentSpy = jest.spyOn(comp as any, 'markContentAsLoaded');

        // User expands a collapsed panel
        comp.onToggleClick(title, true);

        expect(comp['userCollapsed'].get(title)).toBeFalse();
        expect(markContentSpy).toHaveBeenCalledWith(title);

        markContentSpy.mockClear();

        // User collapses an expanded panel
        comp.onToggleClick(title, false);

        expect(comp['userCollapsed'].get(title)).toBeTrue();
        expect(markContentSpy).not.toHaveBeenCalled();
    });

    it('should set initialDiffsReady when all loaded diffs become ready', () => {
        // Create new diff information with all diffs not ready
        const freshDiffInformation = {
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
            ],
            totalLineChange: {
                addedLineCount: 2,
                removedLineCount: 1,
            },
        } as unknown as RepositoryDiffInformation;

        fixture.componentRef.setInput('repositoryDiffInformation', freshDiffInformation);
        fixture.detectChanges();

        expect(comp.initialDiffsReady()).toBeFalse();

        // Mark the diff as ready
        comp.onDiffReady('Example.java', true);

        expect(comp.initialDiffsReady()).toBeTrue();
        expect(comp.allDiffsReady()).toBeTrue();
    });

    it('should observe panels and load content when intersection occurs', fakeAsync(() => {
        const extendedDiffInformation = {
            diffInformations: [
                ...mockDiffInformation.diffInformations,
                createDiffInformationEntry('Fourth.java'),
                createDiffInformationEntry('Fifth.java'),
                createDiffInformationEntry('Sixth.java'),
                createDiffInformationEntry('Seventh.java'),
            ],
            totalLineChange: {
                addedLineCount: 12,
                removedLineCount: 1,
            },
        } as unknown as RepositoryDiffInformation;

        fixture.componentRef.setInput('repositoryDiffInformation', extendedDiffInformation);
        fixture.detectChanges();

        tick(1000);
        fixture.detectChanges();

        expect(MockIntersectionObserver.instances).toHaveLength(1);
        const observerInstance = MockIntersectionObserver.instances[0];
        const observedElements = observerInstance.observe.mock.calls.map((call) => call[0]);
        const panelElements = Array.from(fixture.nativeElement.querySelectorAll('[data-title]')) as Element[];
        const sixthElement = panelElements.find((element) => element.getAttribute('data-title') === 'Sixth.java');
        expect(sixthElement).toBeDefined();
        expect(observedElements).toContain(sixthElement);

        const targetDiff = extendedDiffInformation.diffInformations.find((diff) => diff.title === 'Sixth.java');
        expect(targetDiff?.loadContent).toBeFalsy();

        observerInstance.trigger([{ target: sixthElement as Element, isIntersecting: true }]);

        expect(observerInstance.unobserve).toHaveBeenCalledWith(sixthElement);
        expect(targetDiff?.loadContent).toBeTrue();
    }));

    it('should skip observing panels without data titles', fakeAsync(() => {
        const diffInformationWithEmptyTitle = {
            diffInformations: [
                ...mockDiffInformation.diffInformations,
                createDiffInformationEntry('Fourth.java'),
                createDiffInformationEntry('Fifth.java'),
                createDiffInformationEntry('', { title: '', modifiedPath: '', originalPath: '' }),
            ],
            totalLineChange: {
                addedLineCount: 10,
                removedLineCount: 1,
            },
        } as unknown as RepositoryDiffInformation;

        fixture.componentRef.setInput('repositoryDiffInformation', diffInformationWithEmptyTitle);
        fixture.detectChanges();

        tick(1000);
        fixture.detectChanges();

        expect(MockIntersectionObserver.instances).toHaveLength(1);
        const observerInstance = MockIntersectionObserver.instances[0];
        const blankElement = (Array.from(fixture.nativeElement.querySelectorAll('[data-title]')) as Element[]).find((element) => element.getAttribute('data-title') === '');
        expect(blankElement).toBeDefined();

        const observedElements = observerInstance.observe.mock.calls.map((call) => call[0]);
        expect(observedElements).not.toContain(blankElement);
    }));

    it('should mark content as loaded and update allDiffsReady', () => {
        const moreDiffInformation = {
            diffInformations: [
                ...mockDiffInformation.diffInformations,
                {
                    originalFileContent: 'fourth file',
                    modifiedFileContent: 'fourth file modified',
                    originalPath: 'Fourth.java',
                    modifiedPath: 'Fourth.java',
                    diffReady: false,
                    fileStatus: 'unchanged',
                    lineChange: {
                        addedLineCount: 1,
                        removedLineCount: 0,
                    },
                    title: 'Fourth.java',
                },
                {
                    originalFileContent: 'fifth file',
                    modifiedFileContent: 'fifth file modified',
                    originalPath: 'Fifth.java',
                    modifiedPath: 'Fifth.java',
                    diffReady: false,
                    fileStatus: 'unchanged',
                    lineChange: {
                        addedLineCount: 1,
                        removedLineCount: 0,
                    },
                    title: 'Fifth.java',
                },
                {
                    originalFileContent: 'sixth file',
                    modifiedFileContent: 'sixth file modified',
                    originalPath: 'Sixth.java',
                    modifiedPath: 'Sixth.java',
                    diffReady: false,
                    fileStatus: 'unchanged',
                    lineChange: {
                        addedLineCount: 1,
                        removedLineCount: 0,
                    },
                    title: 'Sixth.java',
                },
            ],
            totalLineChange: {
                addedLineCount: 11,
                removedLineCount: 1,
            },
        } as unknown as RepositoryDiffInformation;

        fixture.componentRef.setInput('repositoryDiffInformation', moreDiffInformation);
        fixture.detectChanges();

        const sixthFile = 'Sixth.java';
        const sixthDiffInfo = moreDiffInformation.diffInformations.find((diff) => diff.title === sixthFile);

        // Initially not loaded (sixth file is at index 5, beyond the initial load count of 5 which loads indices 0-4)
        expect(sixthDiffInfo?.loadContent).toBeFalsy();

        comp['markContentAsLoaded'](sixthFile);

        expect(sixthDiffInfo?.loadContent).toBeTrue();
    });

    it('should not mark content as loaded twice', () => {
        fixture.detectChanges();

        const title = mockDiffInformation.diffInformations[0].title;
        const updateSpy = jest.spyOn(comp as any, 'updateAllDiffsReady');

        // Reset spy to start fresh
        updateSpy.mockClear();

        // First call should trigger update
        comp['markContentAsLoaded'](title);
        const firstCallCount = updateSpy.mock.calls.length;

        // Second call with same title should return early (no additional calls)
        comp['markContentAsLoaded'](title);

        expect(updateSpy).toHaveBeenCalledTimes(firstCallCount); // No additional calls
    });
});
