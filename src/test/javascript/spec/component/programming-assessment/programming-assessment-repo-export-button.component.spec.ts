import dayjs from 'dayjs/esm';
import { ProgrammingExercise } from 'app/entities/programming/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';

describe('ProgrammingAssessmentRepoExportButtonComponent', () => {
    let comp: ProgrammingAssessmentRepoExportButtonComponent;
    let fixture: ComponentFixture<ProgrammingAssessmentRepoExportButtonComponent>;
    let modalService: NgbModal;

    const exerciseId = 42;
    const participationIdList = [1];
    const singleParticipantMode = false;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ProgrammingAssessmentRepoExportButtonComponent);
                comp = fixture.componentInstance;
                modalService = fixture.debugElement.injector.get(NgbModal);

                comp.programmingExercises = [programmingExercise];
                comp.participationIdList = participationIdList;
                comp.singleParticipantMode = singleParticipantMode;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open repo export modal', () => {
        const mockReturnValue = { componentInstance: {} } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openRepoExportDialog(new MouseEvent(''));
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingAssessmentRepoExportDialogComponent, { size: 'lg', keyboard: true });
        expect(modalService.open).toHaveBeenCalledOnce();
    });
});
