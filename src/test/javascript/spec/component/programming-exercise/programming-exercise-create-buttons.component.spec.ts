import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse, HttpHeaders, HttpResponse } from '@angular/common/http';
import { ExerciseType } from 'app/entities/exercise.model';
import { of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../test.module';
import { ProgrammingExerciseComponent } from 'app/exercises/programming/manage/programming-exercise.component';
import { ProgrammingExercise } from 'app/entities/programming-exercise.model';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, convertToParamMap } from '@angular/router';
import { Course } from 'app/entities/course.model';
import { MockCourseExerciseService } from '../../helpers/mocks/service/mock-course-exercise.service';
import { ExerciseFilter } from 'app/entities/exercise-filter.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { ProgrammingExerciseService } from 'app/exercises/programming/manage/services/programming-exercise.service';
import { MockNgbModalService } from '../../helpers/mocks/service/mock-ngb-modal.service';
import { ProgrammingExerciseEditSelectedComponent } from 'app/exercises/programming/manage/programming-exercise-edit-selected.component';
import { CourseExerciseService } from 'app/exercises/shared/course-exercises/course-exercise.service';
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
