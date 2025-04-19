import { TestBed } from '@angular/core/testing';
import { Exercise, ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { Course } from 'app/core/course/shared/entities/course.model';
import { NgbModal, NgbModalRef } from '@ng-bootstrap/ng-bootstrap';
import { MockNgbModalService } from 'test/helpers/mocks/service/mock-ngb-modal.service';
import { ExerciseImportWrapperComponent } from 'app/exercise/import/exercise-import-wrapper/exercise-import-wrapper.component';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { ImportProgrammingButtonComponent } from 'app/programming/manage/create-buttons/import-button/import-programming-button.component';

describe('ProgrammingExercise Import Button Component', () => {
    const course = { id: 123 } as Course;

    let comp: ImportProgrammingButtonComponent;
    let modalService: NgbModal;
    const route = { snapshot: { paramMap: convertToParamMap({ courseId: course.id }) } } as any as ActivatedRoute;
    let router: Router;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ActivatedRoute, useValue: route },
                { provide: NgbModal, useClass: MockNgbModalService },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        }).compileComponents();

        const fixture = TestBed.createComponent(ImportProgrammingButtonComponent);
        comp = fixture.componentInstance;
        modalService = TestBed.inject(NgbModal);
        router = TestBed.inject(Router);
        fixture.componentRef.setInput('course', course);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it.each([undefined, 456])('should open import modal', async (id: number | undefined) => {
        const promise = new Promise((resolve) => {
            resolve({ id, maxPoints: 1 } as Exercise);
        });
        const mockReturnValue = {
            result: promise,
            componentInstance: {},
        } as NgbModalRef;
        jest.spyOn(modalService, 'open').mockReturnValue(mockReturnValue);
        const modalSpy = jest.spyOn(modalService, 'dismissAll');
        const routerSpy = jest.spyOn(router, 'navigate');

        comp.openImportModal();
        expect(modalService.open).toHaveBeenCalledWith(ExerciseImportWrapperComponent, {
            size: 'lg',
            backdrop: 'static',
        });
        expect(modalService.open).toHaveBeenCalledOnce();
        expect(mockReturnValue.componentInstance.exerciseType).toEqual(ExerciseType.PROGRAMMING);

        if (id) {
            await expect(promise)
                .toResolve()
                .then(() => {
                    expect(modalSpy).toHaveBeenCalledOnce();
                    expect(routerSpy).toHaveBeenCalledExactlyOnceWith(['/course-management', 123, `programming-exercises`, 'import', 456]);
                });
        } else {
            await expect(promise)
                .toResolve()
                .then(() => {
                    expect(modalSpy).toHaveBeenCalledOnce();
                    expect(routerSpy).toHaveBeenCalledWith(['/course-management', 123, 'programming-exercises', 'import-from-file'], {
                        state: { programmingExerciseForImportFromFile: { id: undefined, maxPoints: 1 } },
                    });
                });
        }
    });
});
