import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingDiffReportDetailComponent } from 'app/shared/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseService } from 'app/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from 'test/helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from 'test/helpers/mocks/service/mock-programming-exercise-participation.service';
import { ProgrammingDiffReportDetail } from 'app/shared/detail-overview-list/detail.model';

describe('ProgrammingDiffReportDetailComponent', () => {
    let component: ProgrammingDiffReportDetailComponent;
    let fixture: ComponentFixture<ProgrammingDiffReportDetailComponent>;
    let modalService: NgbModal;

    beforeEach(async () => {
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
        const modalSpy = jest.spyOn(modalService, 'open');
        component.detail = {
            data: {
                repositoryDiffInformation: {
                    totalLineChange: {
                        addedLineCount: 10,
                        removedLineCount: 5,
                    },
                },
            },
        } as ProgrammingDiffReportDetail;

        component.showGitDiff();
        expect(modalSpy).toHaveBeenCalled();
    });

    it('should not open git diff modal when repository diff information is missing', () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        component.detail = {
            data: {
                repositoryDiffInformation: undefined,
            },
        } as ProgrammingDiffReportDetail;

        component.showGitDiff();
        expect(modalSpy).not.toHaveBeenCalled();
    });
});
