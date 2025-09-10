import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingDiffReportDetailComponent } from 'app/shared/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { ProgrammingDiffReportDetail } from 'app/shared/detail-overview-list/detail.model';
import { DetailType } from 'app/shared/detail-overview-list/detail-overview-list.component';
import { signal } from '@angular/core';

describe('ProgrammingDiffReportDetailComponent', () => {
    let component: ProgrammingDiffReportDetailComponent;
    let fixture: ComponentFixture<ProgrammingDiffReportDetailComponent>;
    let modalService: NgbModal;
    let mockModalRef: NgbModalRef;

    beforeEach(async () => {
        mockModalRef = {
            close: jest.fn(),
            componentInstance: {
                repositoryDiffInformation: signal(null),
                templateFileContentByPath: signal(null),
                solutionFileContentByPath: signal(null),
            },
        } as any;

        await TestBed.configureTestingModule({
            imports: [ProgrammingDiffReportDetailComponent],
            providers: [
                { provide: NgbModal, useClass: MockNgbModalService },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProgrammingExerciseService, useClass: MockProgrammingExerciseService },
                { provide: ProgrammingExerciseParticipationService, useClass: MockProgrammingExerciseParticipationService },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(ProgrammingDiffReportDetailComponent);

        modalService = TestBed.inject(NgbModal);
        component = fixture.componentInstance;
    });

    it('should open git diff modal when repository diff information exists', () => {
        const modalSpy = jest.spyOn(modalService, 'open').mockReturnValue(mockModalRef);
        component.detail = {
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
        } as ProgrammingDiffReportDetail;

        component.showGitDiff();

        expect(modalSpy).toHaveBeenCalled();
        expect(mockModalRef.componentInstance.repositoryDiffInformation).toBeDefined();
        expect(mockModalRef.componentInstance.templateFileContentByPath).toBeDefined();
        expect(mockModalRef.componentInstance.solutionFileContentByPath).toBeDefined();
    });

    it('should not open git diff modal when repository diff information is missing', () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        component.detail = {
            type: DetailType.ProgrammingDiffReport,
            data: {
                repositoryDiffInformation: undefined,
                templateFileContentByPath: new Map(),
                solutionFileContentByPath: new Map(),
            },
        } as ProgrammingDiffReportDetail;

        component.showGitDiff();
        expect(modalSpy).not.toHaveBeenCalled();
    });

    it('should calculate line counts correctly using getter methods', () => {
        component.detail = {
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
        } as ProgrammingDiffReportDetail;

        expect(component.addedLineCount).toBe(15);
        expect(component.removedLineCount).toBe(8);
    });

    it('should handle ngOnDestroy lifecycle method', () => {
        // Test that ngOnDestroy doesn't throw when modalRef is undefined
        expect(() => component.ngOnDestroy()).not.toThrow();
    });
});
