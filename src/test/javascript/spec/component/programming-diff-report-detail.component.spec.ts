import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseGitDiffReport } from 'app/entities/hestia/programming-exercise-git-diff-report.model';
import { ProgrammingDiffReportDetailComponent } from 'app/detail-overview-list/components/programming-diff-report-detail/programming-diff-report-detail.component';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockProgrammingExerciseService } from '../helpers/mocks/service/mock-programming-exercise.service';
import { ProgrammingExerciseParticipationService } from 'app/exercises/programming/manage/services/programming-exercise-participation.service';
import { MockProgrammingExerciseParticipationService } from '../helpers/mocks/service/mock-programming-exercise-participation.service';

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

        modalService = fixture.debugElement.injector.get(NgbModal);
        component = fixture.componentInstance;
    });

    it('should open git diff modal', () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        component.showGitDiff({} as unknown as ProgrammingExerciseGitDiffReport);
        expect(modalSpy).toHaveBeenCalledOnce();
    });

    it('should not open git diff modal', () => {
        const modalSpy = jest.spyOn(modalService, 'open');
        component.showGitDiff(undefined);
        expect(modalSpy).not.toHaveBeenCalled();
    });
});
