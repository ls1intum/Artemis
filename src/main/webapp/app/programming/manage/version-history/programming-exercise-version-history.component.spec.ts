import { Component, input, output } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { of, throwError } from 'rxjs';
import dayjs from 'dayjs/esm';
import { ProgrammingExerciseVersionHistoryComponent } from 'app/programming/manage/version-history/programming-exercise-version-history.component';
import { ExerciseVersionHistoryService } from 'app/exercise/version-history/shared/exercise-version-history.service';
import { expect, vi } from 'vitest';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MessageModule } from 'primeng/message';
import { SkeletonModule } from 'primeng/skeleton';
import { Tab, TabList, Tabs } from 'primeng/tabs';

@Component({
    selector: 'jhi-exercise-version-history-layout',
    template: '<ng-content />',
    standalone: true,
})
class MockExerciseVersionHistoryLayoutComponent {}

@Component({
    selector: 'jhi-exercise-version-history-timeline',
    template: '',
    standalone: true,
})
class MockExerciseVersionHistoryTimelineComponent {
    readonly versions = input<any[]>([]);
    readonly selectedVersionId = input<number | undefined>();
    readonly hasMore = input(false);
    readonly loading = input(false);
    readonly loadingMore = input(false);
    readonly totalItems = input(0);
    readonly selectVersion = output<number>();
    readonly loadMore = output<void>();
}

@Component({
    selector: 'jhi-exercise-version-shared-snapshot-metadata',
    template: '',
    standalone: true,
})
class MockExerciseVersionSharedSnapshotMetadataComponent {
    readonly snapshot = input.required<any>();
    readonly previousSnapshot = input<any>();
    readonly viewMode = input<'full' | 'changes'>('full');
}

@Component({
    selector: 'jhi-programming-exercise-version-programming-metadata',
    template: '',
    standalone: true,
})
class MockProgrammingExerciseVersionProgrammingMetadataComponent {
    readonly exerciseId = input<number | undefined>();
    readonly programmingData = input<any>();
    readonly previousProgrammingData = input<any>();
    readonly viewMode = input<'full' | 'changes'>('full');
}

describe('ProgrammingExerciseVersionHistoryComponent', () => {
    setupTestBed({ zoneless: true });

    let fixture: ComponentFixture<ProgrammingExerciseVersionHistoryComponent>;
    let component: ProgrammingExerciseVersionHistoryComponent;

    const serviceMock = {
        getVersions: vi.fn(),
        getSnapshot: vi.fn(),
    };

    beforeEach(async () => {
        serviceMock.getVersions.mockReturnValue(
            of({
                versions: [
                    { id: 9, author: { login: 'ed1', name: 'Editor One' }, createdDate: dayjs('2026-03-04T11:00:00Z') },
                    { id: 8, author: { login: 'ed2', name: 'Editor Two' }, createdDate: dayjs('2026-03-03T11:00:00Z') },
                ],
                nextPage: undefined,
                totalItems: 2,
            }),
        );
        serviceMock.getSnapshot.mockImplementation((_: number, versionId: number) =>
            of({ id: versionId, title: `Snapshot ${versionId}`, programmingData: { programmingLanguage: 'JAVA' as any } }),
        );

        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseVersionHistoryComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({ exerciseId: '42' }),
                            params: { exerciseId: '42', courseId: '1' },
                        },
                        parent: null,
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExerciseVersionHistoryService, useValue: serviceMock },
            ],
        })
            .overrideComponent(ProgrammingExerciseVersionHistoryComponent, {
                set: {
                    imports: [
                        TranslateDirective,
                        MessageModule,
                        SkeletonModule,
                        Tabs,
                        TabList,
                        Tab,
                        MockExerciseVersionHistoryLayoutComponent,
                        MockExerciseVersionHistoryTimelineComponent,
                        MockExerciseVersionSharedSnapshotMetadataComponent,
                        MockProgrammingExerciseVersionProgrammingMetadataComponent,
                    ],
                },
            })
            .compileComponents();

        fixture = TestBed.createComponent(ProgrammingExerciseVersionHistoryComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should load first page and auto-select newest version', () => {
        fixture.detectChanges();

        expect(serviceMock.getVersions).toHaveBeenCalledWith(42, 0, 20);
        expect(serviceMock.getSnapshot).toHaveBeenCalledWith(42, 9);
        expect(component.selectedVersionId()).toBe(9);
        expect(component.viewMode()).toBe('changes');
    });

    it('should load more versions', () => {
        serviceMock.getVersions.mockReturnValueOnce(
            of({
                versions: [
                    { id: 9, author: { login: 'ed1', name: 'Editor One' }, createdDate: dayjs('2026-03-04T11:00:00Z') },
                    { id: 8, author: { login: 'ed2', name: 'Editor Two' }, createdDate: dayjs('2026-03-03T11:00:00Z') },
                ],
                nextPage: 1,
                totalItems: 4,
            }),
        );
        fixture.detectChanges();
        component.onLoadMoreVersions();

        expect(serviceMock.getVersions).toHaveBeenCalledWith(42, 1, 20);
    });

    it('should set error key when snapshot loading fails', () => {
        serviceMock.getSnapshot.mockReturnValue(throwError(() => new Error('boom')));
        fixture.detectChanges();

        expect(component.snapshotError()).toBe('artemisApp.exercise.versionHistory.errors.snapshotLoadFailed');
    });

    it('should allow retrying the same version after a failed snapshot fetch', () => {
        serviceMock.getSnapshot.mockReturnValueOnce(throwError(() => new Error('boom')));
        fixture.detectChanges();

        // version 9 was auto-selected and failed
        expect(component.snapshotError()).toBe('artemisApp.exercise.versionHistory.errors.snapshotLoadFailed');
        serviceMock.getSnapshot.mockClear();

        // re-selecting the same version should retry
        component.onSelectVersion(9);
        expect(serviceMock.getSnapshot).toHaveBeenCalledWith(42, 9);
    });

    it('should set timeline error when version loading fails', () => {
        serviceMock.getVersions.mockReturnValueOnce(throwError(() => new Error('fail')));
        fixture.detectChanges();

        expect(component.timelineError()).toBe('artemisApp.exercise.versionHistory.errors.timelineLoadFailed');
    });

    it('should handle empty versions list', () => {
        serviceMock.getVersions.mockReturnValueOnce(
            of({
                versions: [],
                nextPage: undefined,
                totalItems: 0,
            }),
        );
        fixture.detectChanges();

        expect(component.versions()).toHaveLength(0);
        expect(component.selectedVersionId()).toBeUndefined();
        expect(component.selectedSnapshot()).toBeUndefined();
        expect(component.hasMore()).toBeFalsy();
    });

    it('should not reload snapshot when selecting the same version', () => {
        fixture.detectChanges();
        serviceMock.getSnapshot.mockClear();

        // version 9 is already selected from initial load
        component.onSelectVersion(9);
        expect(serviceMock.getSnapshot).not.toHaveBeenCalled();
    });

    it('should reuse cached snapshots when reselecting a version', () => {
        fixture.detectChanges();
        expect(component.selectedSnapshot()).toEqual({ id: 9, title: 'Snapshot 9', programmingData: { programmingLanguage: 'JAVA' } });

        serviceMock.getSnapshot.mockClear();
        serviceMock.getSnapshot.mockReturnValueOnce(of({ id: 43, title: 'Snapshot B', programmingData: { programmingLanguage: 'KOTLIN' as any } }));
        component.onSelectVersion(7);
        expect(serviceMock.getSnapshot).toHaveBeenCalledWith(42, 7);

        serviceMock.getSnapshot.mockClear();
        component.onSelectVersion(9);
        expect(serviceMock.getSnapshot).not.toHaveBeenCalled();

        component.onSelectVersion(7);
        expect(serviceMock.getSnapshot).not.toHaveBeenCalled();
    });

    it('should switch back to full view when no predecessor exists', () => {
        serviceMock.getVersions.mockReturnValueOnce(
            of({
                versions: [{ id: 1, author: { login: 'ed1', name: 'Editor' }, createdDate: dayjs() }],
                nextPage: undefined,
                totalItems: 1,
            }),
        );
        fixture.detectChanges();

        expect(component.showDiffTab()).toBe(false);
        expect(component.viewMode()).toBe('full');
    });

    it('should not load more when no next page is available', () => {
        serviceMock.getVersions.mockReturnValueOnce(
            of({
                versions: [{ id: 1, author: { login: 'ed1', name: 'Editor' }, createdDate: dayjs() }],
                nextPage: undefined,
                totalItems: 1,
            }),
        );
        fixture.detectChanges();
        serviceMock.getVersions.mockClear();

        component.onLoadMoreVersions();
        expect(serviceMock.getVersions).not.toHaveBeenCalled();
    });
});

describe('ProgrammingExerciseVersionHistoryComponent (missing exerciseId)', () => {
    setupTestBed({ zoneless: true });

    const serviceMock = {
        getVersions: vi.fn(),
        getSnapshot: vi.fn(),
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [ProgrammingExerciseVersionHistoryComponent],
            providers: [
                {
                    provide: ActivatedRoute,
                    useValue: {
                        snapshot: {
                            paramMap: convertToParamMap({}),
                            params: {},
                        },
                        parent: null,
                    },
                },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ExerciseVersionHistoryService, useValue: serviceMock },
            ],
        })
            .overrideComponent(ProgrammingExerciseVersionHistoryComponent, {
                set: {
                    imports: [
                        TranslateDirective,
                        MessageModule,
                        SkeletonModule,
                        MockExerciseVersionHistoryLayoutComponent,
                        MockExerciseVersionHistoryTimelineComponent,
                        MockExerciseVersionSharedSnapshotMetadataComponent,
                        MockProgrammingExerciseVersionProgrammingMetadataComponent,
                    ],
                },
            })
            .compileComponents();
    });

    it('should set timeline error when exercise id is missing', () => {
        const fixture = TestBed.createComponent(ProgrammingExerciseVersionHistoryComponent);
        const component = fixture.componentInstance;
        fixture.detectChanges();

        expect(component.timelineError()).toBe('artemisApp.exercise.versionHistory.errors.invalidExerciseId');
        expect(component.exerciseId()).toBeUndefined();
    });
});
