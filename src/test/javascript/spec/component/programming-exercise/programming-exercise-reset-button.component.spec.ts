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
import { ProgrammingExerciseResetButtonComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-button.component';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseResetDialogComponent } from 'app/exercises/programming/manage/reset/programming-exercise-reset-dialog.component';
import { ButtonComponent } from 'app/shared/components/button.component';

describe('ProgrammingExerciseResetButtonComponent', () => {
    let comp: ProgrammingExerciseResetButtonComponent;
    let fixture: ComponentFixture<ProgrammingExerciseResetButtonComponent>;
    let modalService: NgbModal;

    const exerciseId = 42;
    const programmingExercise = new ProgrammingExercise(new Course(), undefined);
    programmingExercise.id = exerciseId;
    programmingExercise.releaseDate = dayjs();
    programmingExercise.dueDate = dayjs().add(7, 'days');

    beforeEach(() => {
        return TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                ProgrammingExerciseResetButtonComponent,
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
                fixture = TestBed.createComponent(ProgrammingExerciseResetButtonComponent);
                comp = fixture.componentInstance;
                modalService = fixture.debugElement.injector.get(NgbModal);

                comp.programmingExercise = programmingExercise;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should open reset modal', () => {
        const mockReturnValue = { componentInstance: {} } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openResetDialog(new MouseEvent(''));
        expect(modalService.open).toHaveBeenCalledWith(ProgrammingExerciseResetDialogComponent, { size: 'lg', keyboard: true });
        expect(modalService.open).toHaveBeenCalledOnce();
    });
});
