import { MockComponent, MockDirective, MockPipe } from 'ng-mocks';
import dayjs from 'dayjs/esm';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { Course } from 'app/entities/course.model';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockTranslateValuesDirective } from '../../helpers/mocks/directive/mock-translate-values.directive';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { ProgrammingAssessmentRepoExportButtonComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-button.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingAssessmentRepoExportDialogComponent } from 'app/exercises/programming/assess/repo-export/programming-assessment-repo-export-dialog.component';
import { ButtonComponent } from 'app/shared/components/button.component';

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
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingAssessmentRepoExportButtonComponent,
                MockTranslateValuesDirective,
                MockPipe(ArtemisTranslatePipe),
                MockComponent(ButtonComponent),
                MockDirective(TranslateDirective),
            ],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: NgbModal, useClass: MockNgbModalService },
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
