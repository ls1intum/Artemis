import { vi } from 'vitest';
import { signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProgrammingDiffReportDetailComponent } from 'app/shared-ui/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { ProgrammingDiffReportDetail } from 'app/shared-ui/detail-overview-list/detail.model';
import { DetailType } from 'app/shared-ui/detail-overview-list/detail-overview-list.component';
import { RepositoryDiffInformation } from 'app/programming/shared/utils/diff.utils';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';

/**
 * Builds a signal-backed detail input matching {@link ProgrammingDiffReportDetail}. The fields are signals so the
 * detail component reacts to the diff being computed asynchronously after the section has already rendered.
 */
function diffDetail(overrides: {
    repositoryDiffInformation?: RepositoryDiffInformation;
    templateFileContentByPath?: Map<string, string>;
    solutionFileContentByPath?: Map<string, string>;
    lineChangesLoading?: boolean;
}): ProgrammingDiffReportDetail {
    return {
        type: DetailType.ProgrammingDiffReport,
        data: {
            repositoryDiffInformation: signal(overrides.repositoryDiffInformation),
            templateFileContentByPath: signal(overrides.templateFileContentByPath ?? new Map<string, string>()),
            solutionFileContentByPath: signal(overrides.solutionFileContentByPath ?? new Map<string, string>()),
            lineChangesLoading: signal(overrides.lineChangesLoading ?? false),
        },
    } as ProgrammingDiffReportDetail;
}

describe('ProgrammingDiffReportDetailComponent', () => {
    let component: ProgrammingDiffReportDetailComponent;
    let fixture: ComponentFixture<ProgrammingDiffReportDetailComponent>;
    let dialogService: DialogService;
    let mockDialogRef: DynamicDialogRef;

    beforeEach(async () => {
        mockDialogRef = {
            close: vi.fn(),
        } as any;

        await TestBed.configureTestingModule({
            imports: [ProgrammingDiffReportDetailComponent],
            providers: [
                { provide: DialogService, useValue: { open: vi.fn().mockReturnValue(mockDialogRef) } },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingDiffReportDetailComponent);

        dialogService = TestBed.inject(DialogService);
        component = fixture.componentInstance;
    });

    it('should open git diff modal when repository diff information exists', () => {
        const dialogSpy = vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        fixture.componentRef.setInput(
            'detail',
            diffDetail({
                repositoryDiffInformation: {
                    totalLineChange: {
                        addedLineCount: 10,
                        removedLineCount: 5,
                    },
                } as RepositoryDiffInformation,
                templateFileContentByPath: new Map([['file1.txt', 'content1']]),
                solutionFileContentByPath: new Map([['file1.txt', 'content2']]),
            }),
        );

        component.showGitDiff();

        expect(dialogSpy).toHaveBeenCalled();
        expect(dialogSpy).toHaveBeenCalledWith(
            expect.any(Function),
            expect.objectContaining({
                // Guard the close-affordance fix: PrimeNG only renders the X and binds Escape when closable is true.
                closable: true,
                data: expect.objectContaining({
                    repositoryDiffInformation: expect.objectContaining({
                        totalLineChange: expect.objectContaining({
                            addedLineCount: 10,
                            removedLineCount: 5,
                        }),
                    }),
                    diffForTemplateAndSolution: true,
                }),
            }),
        );
    });

    it('should not open git diff modal when repository diff information is missing', () => {
        const dialogSpy = vi.spyOn(dialogService, 'open');
        fixture.componentRef.setInput('detail', diffDetail({ repositoryDiffInformation: undefined }));

        component.showGitDiff();
        expect(dialogSpy).not.toHaveBeenCalled();
    });

    it('should calculate line counts correctly', () => {
        fixture.componentRef.setInput(
            'detail',
            diffDetail({
                repositoryDiffInformation: {
                    totalLineChange: {
                        addedLineCount: 15,
                        removedLineCount: 8,
                    },
                } as RepositoryDiffInformation,
            }),
        );

        expect(component.addedLineCount()).toBe(15);
        expect(component.removedLineCount()).toBe(8);
    });

    it('should close an open dialog on destroy', () => {
        vi.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        fixture.componentRef.setInput(
            'detail',
            diffDetail({ repositoryDiffInformation: { totalLineChange: { addedLineCount: 1, removedLineCount: 0 } } as RepositoryDiffInformation }),
        );

        component.showGitDiff();
        component.ngOnDestroy();

        expect(mockDialogRef.close).toHaveBeenCalledOnce();
    });

    // Regression test for the zoneless change-detection bug: the diff is computed asynchronously after the detail
    // section has already rendered. Updating the signal-backed data must schedule change detection on its own.
    // Previously the data was a plain object mutated in place, so the view kept spinning until an unrelated event
    // (e.g. a window resize) triggered a change-detection pass.
    it('should render the diff stats reactively once the asynchronously computed diff arrives', async () => {
        const repositoryDiffInformation = signal<RepositoryDiffInformation | undefined>(undefined);
        const lineChangesLoading = signal(true);
        fixture.componentRef.setInput('detail', {
            type: DetailType.ProgrammingDiffReport,
            data: {
                repositoryDiffInformation,
                templateFileContentByPath: signal(new Map<string, string>()),
                solutionFileContentByPath: signal(new Map<string, string>()),
                lineChangesLoading,
            },
        } as ProgrammingDiffReportDetail);

        await fixture.whenStable();
        const host = fixture.nativeElement as HTMLElement;
        // While the diff is still loading, only the spinner is shown (no line-stat yet).
        expect(host.querySelector('svg[data-icon="spinner"]')).not.toBeNull();
        expect(host.querySelector('jhi-git-diff-line-stat')).toBeNull();

        // Simulate the async computation completing WITHOUT re-assigning the input; rely purely on the
        // zoneless scheduler picking up the signal change (no manual detectChanges).
        repositoryDiffInformation.set({
            diffInformations: [],
            totalLineChange: { addedLineCount: 12, removedLineCount: 3 },
        } as RepositoryDiffInformation);
        lineChangesLoading.set(false);

        await fixture.whenStable();

        // The spinner is gone and the line-stat is rendered with the freshly computed counts.
        expect(host.querySelector('svg[data-icon="spinner"]')).toBeNull();
        expect(host.querySelector('jhi-git-diff-line-stat')).not.toBeNull();
        expect(component.lineChangesLoading()).toBe(false);
        expect(component.addedLineCount()).toBe(12);
        expect(component.removedLineCount()).toBe(3);
    });
});
