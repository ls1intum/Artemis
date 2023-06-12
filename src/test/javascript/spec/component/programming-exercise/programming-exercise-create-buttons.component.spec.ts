import { TestBed } from '@angular/core/testing';
import { ExerciseType } from 'app/entities/exercise.model';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseImportWrapperComponent } from 'app/exercises/shared/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { ProgrammingExerciseCreateButtonsComponent } from 'app/exercises/programming/manage/programming-exercise-create-buttons.component';

describe('ProgrammingExercise Create Buttons Component', () => {
    const course = { id: 123 } as Course;

    let comp: ProgrammingExerciseCreateButtonsComponent;
    let modalService: NgbModal;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [ProgrammingExerciseComponent],
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: NgbModal, useClass: MockNgbModalService },
            ],
        }).compileComponents();

        const fixture = TestBed.createComponent(ProgrammingExerciseCreateButtonsComponent);
        comp = fixture.componentInstance;
        modalService = fixture.debugElement.injector.get(NgbModal);

        comp.course = course;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([undefined, 456])('should open import modal', (id: number | undefined) => {
        const mockReturnValue = {
            result: Promise.resolve({ id } as ProgrammingExercise),
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ExerciseImportWrapperComponent, { size: 'lg', backdrop: 'static' });
        expect(modalService.open).toHaveBeenCalledOnce();
        expect(mockReturnValue.componentInstance.exerciseType).toEqual(ExerciseType.PROGRAMMING);
    });
});
