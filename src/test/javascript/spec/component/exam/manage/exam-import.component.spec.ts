import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { DifficultyBadgeComponent } from 'app/exercises/shared/exercise-headers/difficulty-badge.component';
import { ButtonComponent } from 'app/shared/components/button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/sort-by.directive';
import { SortDirective } from 'app/shared/sort/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { of, throwError } from 'rxjs';
import { NgbPaginationMocksModule } from '../../../helpers/mocks/directive/ngbPaginationMocks.module';
import { ArtemisTestModule } from '../../../test.module';
import { UMLDiagramType } from '@ls1intum/apollon';

describe('Exam Import Component', () => {
    let component: ExamImportComponent;
    let fixture: ComponentFixture<ExamImportComponent>;
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

        performImport(importSpy);
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

            performImport(importSpy);
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
        performImport(importSpy);

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(modalSpy).not.toHaveBeenCalled();
    });

    function performImport(importSpy: jest.SpyInstance): void {
        component.subsequentExerciseGroupSelection = true;
        component.exam = exam1WithExercises;
        component.targetCourseId = 1;
        component.targetExamId = 2;
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1]);
    }
});
