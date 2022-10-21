import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ExerciseHint } from 'app/entities/hestia/exercise-hint.model';
import { ExerciseHintButtonOverlayComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-button-overlay.component';
import { ExerciseHintStudentDialogComponent } from 'app/exercises/shared/exercise-hint/participate/exercise-hint-student-dialog.component';
import { MockNgbModalService } from '../../../helpers/mocks/service/mock-ngb-modal.service';

import { ArtemisTestModule } from '../../../test.module';

describe('Exercise Hint Button Overlay Component', () => {
    let comp: ExerciseHintButtonOverlayComponent;
    let fixture: ComponentFixture<ExerciseHintButtonOverlayComponent>;

    let modalService: NgbModal;

    const availableExerciseHint = new ExerciseHint();
    availableExerciseHint.id = 1;
    const activatedExerciseHint = new ExerciseHint();
    activatedExerciseHint.id = 2;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            providers: [{ provide: NgbModal, useClass: MockNgbModalService }],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExerciseHintButtonOverlayComponent);
                comp = fixture.componentInstance;

                modalService = TestBed.inject(NgbModal);

                comp.availableExerciseHints = [availableExerciseHint];
                comp.activatedExerciseHints = [activatedExerciseHint];
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open modal with exercise hints', () => {
        const componentInstance = { availableExerciseHints: [], activatedExerciseHints: [] };
        const result = new Promise((resolve) => resolve(true));
        const openModalSpy = jest.spyOn(modalService, 'open').mockReturnValue(<NgbModalRef>{
            componentInstance,
            result,
        });

        comp.openModal();

        expect(openModalSpy).toHaveBeenCalledOnce();
        expect(openModalSpy).toHaveBeenCalledWith(ExerciseHintStudentDialogComponent, { size: 'lg', backdrop: 'static' });
        expect(componentInstance.availableExerciseHints).toEqual([availableExerciseHint]);
        expect(componentInstance.activatedExerciseHints).toEqual([activatedExerciseHint]);
    });
});
