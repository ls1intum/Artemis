import { ComponentFixture, TestBed } from '@angular/core/testing';
import { DialogService, DynamicDialogRef } from 'primeng/dynamicdialog';
import { ProgrammingDiffReportDetailComponent } from 'app/shared/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { ProgrammingDiffReportDetail } from 'app/shared/detail-overview-list/detail.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';

describe('ProgrammingDiffReportDetailComponent', () => {
    let component: ProgrammingDiffReportDetailComponent;
    let fixture: ComponentFixture<ProgrammingDiffReportDetailComponent>;
    let dialogService: DialogService;
    let mockDialogRef: DynamicDialogRef;

    beforeEach(async () => {
        mockDialogRef = {
            close: jest.fn(),
        } as unknown as DynamicDialogRef;

        await TestBed.configureTestingModule({
            imports: [ProgrammingDiffReportDetailComponent],
            providers: [
                DialogService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingDiffReportDetailComponent);

        dialogService = TestBed.inject(DialogService);
        component = fixture.componentInstance;
    });

    it('should open git diff dialog when repository diff information exists', () => {
        const dialogSpy = jest.spyOn(dialogService, 'open').mockReturnValue(mockDialogRef);
        fixture.componentRef.setInput('detail', {
            type: DetailType.ProgrammingDiffReport,
            data: {
                repositoryDiffInformation: {
                    totalLineChange: {
                        addedLineCount: 10,
                        removedLineCount: 5,
                    },
                },
                templateFileContentByPath: new Map([['file1.txt', 'content1']]),
                solutionFileContentByPath: new Map([['file1.txt', 'content2']]),
            },
        } as ProgrammingDiffReportDetail);

        component.showGitDiff();

        expect(dialogSpy).toHaveBeenCalled();
        const passedConfig = dialogSpy.mock.calls[0][1];
        expect(passedConfig?.data?.repositoryDiffInformation).toBeDefined();
        expect(passedConfig?.data?.diffForTemplateAndSolution).toBeTrue();
    });

    it('should not open git diff dialog when repository diff information is missing', () => {
        const dialogSpy = jest.spyOn(dialogService, 'open');
        fixture.componentRef.setInput('detail', {
            type: DetailType.ProgrammingDiffReport,
            data: {
                repositoryDiffInformation: undefined,
                templateFileContentByPath: new Map(),
                solutionFileContentByPath: new Map(),
            },
        } as ProgrammingDiffReportDetail);

        component.showGitDiff();
        expect(dialogSpy).not.toHaveBeenCalled();
    });

    it('should calculate line counts correctly using getter methods', () => {
        fixture.componentRef.setInput('detail', {
            type: DetailType.ProgrammingDiffReport,
            data: {
                repositoryDiffInformation: {
                    totalLineChange: {
                        addedLineCount: 15,
                        removedLineCount: 8,
                    },
                },
                templateFileContentByPath: new Map(),
                solutionFileContentByPath: new Map(),
            },
        } as ProgrammingDiffReportDetail);

        expect(component.addedLineCount).toBe(15);
        expect(component.removedLineCount).toBe(8);
    });

    it('should handle ngOnDestroy lifecycle method', () => {
        // Test that ngOnDestroy doesn't throw when dialogRef is undefined
        expect(() => component.ngOnDestroy()).not.toThrow();
    });
});
