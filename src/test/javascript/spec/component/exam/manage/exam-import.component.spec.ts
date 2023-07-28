import { ComponentFixture, TestBed, fakeAsync, tick } from '@angular/core/testing';
import { Subject, of, throwError } from 'rxjs';
import { ArtemisTestModule } from '../../../test.module';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { FormsModule } from '@angular/forms';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { SearchResult } from 'app/shared/table/pageable-table';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { Exam } from 'app/entities/exam.model';
import { AlertService } from 'app/core/util/alert.service';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { NgbPaginationMocksModule } from '../../../helpers/mocks/directive/ngbPaginationMocks.module';

describe('Exam Import Component', () => {
    let component: ExamImportComponent;
    let fixture: ComponentFixture<ExamImportComponent>;
    let sortService: SortService;
    let pagingService: ExamImportPagingService;
    let activeModal: NgbActiveModal;
    let examManagementService: ExamManagementService;
    let alertService: AlertService;

    const exam1 = { id: 1 } as Exam;

    // Initializing one Exercise Group
    const exerciseGroup1 = { title: 'exerciseGroup1' } as ExerciseGroup;
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup1);
    modelingExercise.id = 1;
    modelingExercise.title = 'ModelingExercise';
    exerciseGroup1.exercises = [modelingExercise];
    const exam1WithExercises = { id: 1, exerciseGroups: [exerciseGroup1] } as Exam;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, FormsModule, NgbPaginationMocksModule],
            declarations: [
                ExamImportComponent,
                ExamExerciseImportComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(SortByDirective),
                MockDirective(SortDirective),
                MockComponent(ButtonComponent),
                MockComponent(HelpIconComponent),
                MockComponent(DifficultyBadgeComponent),
            ],
            providers: [
                MockProvider(SortService),
                MockProvider(ExamImportPagingService),
                MockProvider(NgbActiveModal),
                MockProvider(ExamManagementService),
                MockProvider(AlertService),
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamImportComponent);
                component = fixture.componentInstance;
                sortService = fixture.debugElement.injector.get(SortService);
                pagingService = fixture.debugElement.injector.get(ExamImportPagingService);
                activeModal = TestBed.inject(NgbActiveModal);
                examManagementService = fixture.debugElement.injector.get(ExamManagementService);
                alertService = fixture.debugElement.injector.get(AlertService);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should correctly open the exercise selection', () => {
        jest.spyOn(examManagementService, 'findWithExercisesAndWithoutCourseId').mockReturnValue(of(new HttpResponse({ body: exam1WithExercises })));
        component.openExerciseSelection(exam1);
        expect(component.exam).toEqual(exam1WithExercises);
    });

    it('should correctly show an error for the exercise selection, if the server throws an error', () => {
        const error = new HttpErrorResponse({
            status: 400,
        });
        jest.spyOn(examManagementService, 'findWithExercisesAndWithoutCourseId').mockReturnValue(throwError(() => error));
        const alertSpy = jest.spyOn(alertService, 'error');
        component.openExerciseSelection(exam1);
        expect(component.exam).toBeUndefined();
        expect(alertSpy).toHaveBeenCalledOnce();
    });

    it('should only perform input of exercise groups if prerequisites are met', () => {
        const importSpy = jest.spyOn(examManagementService, 'importExerciseGroup');
        const alertSpy = jest.spyOn(alertService, 'error');
        const modalSpy = jest.spyOn(activeModal, 'close');

        component.subsequentExerciseGroupSelection = false;
        component.performImportOfExerciseGroups();

        component.subsequentExerciseGroupSelection = true;
        component.exam = undefined;
        component.performImportOfExerciseGroups();

        component.exam = exam1WithExercises;
        component.targetExamId = undefined;
        component.performImportOfExerciseGroups();

        component.targetExamId = 1;
        component.targetCourseId = undefined;
        component.performImportOfExerciseGroups();

        expect(importSpy).not.toHaveBeenCalled();
        expect(alertSpy).not.toHaveBeenCalled();
        expect(modalSpy).not.toHaveBeenCalled();
    });

    it('should perform import of exercise groups successfully', () => {
        const importSpy = jest.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(
            of(
                new HttpResponse({
                    status: 200,
                    body: [exerciseGroup1],
                }),
            ),
        );
        const alertSpy = jest.spyOn(alertService, 'error');
        const modalSpy = jest.spyOn(activeModal, 'close');

        component.subsequentExerciseGroupSelection = true;
        component.exam = exam1WithExercises;
        component.targetCourseId = 1;
        component.targetExamId = 2;
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1]);
        expect(alertSpy).not.toHaveBeenCalled();
        expect(modalSpy).toHaveBeenCalledOnce();
        expect(modalSpy).toHaveBeenCalledWith([exerciseGroup1]);
    });

    it('should trigger an alarm for a wrong user input', () => {
        const importSpy = jest.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(
            of(
                new HttpResponse({
                    status: 200,
                    body: [exerciseGroup1],
                }),
            ),
        );
        const alertSpy = jest.spyOn(alertService, 'error');
        const modalSpy = jest.spyOn(activeModal, 'close');

        component.subsequentExerciseGroupSelection = true;
        const exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
        const modelingExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup2);
        modelingExercise2.id = 2;
        exerciseGroup2.exercises = [modelingExercise2];
        component.exam = { id: 1, exerciseGroups: [exerciseGroup2] } as Exam;
        component.targetCourseId = 1;
        component.targetExamId = 3;
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).not.toHaveBeenCalled();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(modalSpy).not.toHaveBeenCalled();
    });

    it.each(['duplicatedProgrammingExerciseShortName', 'duplicatedProgrammingExerciseTitle', 'invalidKey'])(
        'should perform import of exercise groups AND correctly process conflict from server',
        (errorKey) => {
            const preCheckError = new HttpErrorResponse({
                error: { errorKey: errorKey, numberOfInvalidProgrammingExercises: 0, params: { exerciseGroups: [exerciseGroup1] } },
                status: 400,
            });
            const importSpy = jest.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(throwError(() => preCheckError));
            const alertSpy = jest.spyOn(alertService, 'error');
            const modalSpy = jest.spyOn(activeModal, 'close');

            component.subsequentExerciseGroupSelection = true;
            component.exam = exam1WithExercises;
            component.targetCourseId = 1;
            component.targetExamId = 2;
            fixture.detectChanges();
            component.performImportOfExerciseGroups();

            expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1]);
            if (errorKey == 'invalidKey') {
                expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: 0 });
            } else {
                expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
            }
            expect(modalSpy).not.toHaveBeenCalled();
        },
    );

    it('should perform import of exercise groups AND correctly process arbitrary exception from server', () => {
        const error = new HttpErrorResponse({
            status: 400,
        });
        const importSpy = jest.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(throwError(() => error));
        const alertSpy = jest.spyOn(alertService, 'error');
        const modalSpy = jest.spyOn(activeModal, 'close');

        component.subsequentExerciseGroupSelection = true;
        component.exam = exam1WithExercises;
        component.targetCourseId = 1;
        component.targetExamId = 2;
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1]);
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(modalSpy).not.toHaveBeenCalled();
    });

    it('should initialize the subjects', () => {
        // GIVEN
        const searchSpy = jest.spyOn(component, 'performSearch' as any);

        // WHEN
        fixture.detectChanges();

        // THEN
        expect(searchSpy).toHaveBeenCalledTimes(2);
        expect(searchSpy).toHaveBeenCalledWith(expect.any(Subject), 0);
        expect(searchSpy).toHaveBeenCalledWith(expect.any(Subject), 300);
    });

    it('should initialize the content', () => {
        // WHEN
        fixture.detectChanges();

        // THEN
        expect(component.content).toEqual({ resultsOnPage: [], numberOfPages: 0 });
    });

    it('should close the active modal', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'dismiss');

        // WHEN
        component.clear();

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith('cancel');
    });

    it('should close the active modal with result', () => {
        // GIVEN
        const activeModalSpy = jest.spyOn(activeModal, 'close');
        const exam = { id: 1 } as Exam;
        // WHEN
        component.forwardSelectedExam(exam);

        // THEN
        expect(activeModalSpy).toHaveBeenCalledOnce();
        expect(activeModalSpy).toHaveBeenCalledWith(exam);
    });

    it('should change the page on active modal', fakeAsync(() => {
        const defaultPageSize = 10;
        const numberOfPages = 5;
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExams');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages } as SearchResult<Exam>));

        fixture.detectChanges();

        let expectedPageNumber = 1;
        component.onPageChange(expectedPageNumber);
        tick();
        expect(component.page).toBe(expectedPageNumber);
        expect(component.total).toBe(numberOfPages * defaultPageSize);

        expectedPageNumber = 2;
        component.onPageChange(expectedPageNumber);
        tick();
        expect(component.page).toBe(expectedPageNumber);
        expect(component.total).toBe(numberOfPages * defaultPageSize);

        // Page number should be changed unless it is falsy.
        component.onPageChange(0);
        tick();
        expect(component.page).toBe(expectedPageNumber);

        // Number of times onPageChange is called with a truthy value.
        expect(pagingServiceSpy).toHaveBeenCalledTimes(2);
    }));

    it('should sort rows with default values', () => {
        const sortServiceSpy = jest.spyOn(sortService, 'sortByProperty');

        fixture.detectChanges();
        component.sortRows();

        expect(sortServiceSpy).toHaveBeenCalledOnce();
        expect(sortServiceSpy).toHaveBeenCalledWith([], component.column.ID, false);
    });

    it('should set search term and search', fakeAsync(() => {
        const pagingServiceSpy = jest.spyOn(pagingService, 'searchForExams');
        pagingServiceSpy.mockReturnValue(of({ numberOfPages: 3 } as SearchResult<Exam>));

        fixture.detectChanges();

        const expectedSearchTerm = 'search term';
        component.searchTerm = expectedSearchTerm;
        tick();
        expect(component.searchTerm).toBe(expectedSearchTerm);

        // It should wait first before executing search.
        expect(pagingServiceSpy).not.toHaveBeenCalled();

        tick(300);

        expect(pagingServiceSpy).toHaveBeenCalledOnce();
    }));

    it('should track the id correctly', () => {
        const exam = { id: 1 } as Exam;
        expect(component.trackId(5, exam)).toBe(exam.id);
    });
});
