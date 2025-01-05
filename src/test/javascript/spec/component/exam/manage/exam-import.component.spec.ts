import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { AlertService } from 'app/core/util/alert.service';
import { Exam } from 'app/entities/exam/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { ExamManagementService } from 'app/exam/manage/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { MockPipe } from 'ng-mocks';
import { of, throwError } from 'rxjs';
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
            imports: [ArtemisTestModule],
            declarations: [ExamImportComponent, ExamExerciseImportComponent, MockPipe(ArtemisTranslatePipe)],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamImportComponent);
                component = fixture.componentInstance;
                activeModal = TestBed.inject(NgbActiveModal);
                examManagementService = TestBed.inject(ExamManagementService);
                alertService = TestBed.inject(AlertService);
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

        fixture.componentRef.setInput('subsequentExerciseGroupSelection', false);
        component.performImportOfExerciseGroups();

        fixture.componentRef.setInput('subsequentExerciseGroupSelection', true);
        component.exam = undefined;
        component.performImportOfExerciseGroups();

        component.exam = exam1WithExercises;
        fixture.componentRef.setInput('targetExamId', undefined);
        component.performImportOfExerciseGroups();

        fixture.componentRef.setInput('targetExamId', 1);
        fixture.componentRef.setInput('targetCourseId', undefined);
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

        fixture.componentRef.setInput('subsequentExerciseGroupSelection', true);
        const exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
        const modelingExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup2);
        modelingExercise2.id = 2;
        exerciseGroup2.exercises = [modelingExercise2];
        component.exam = { id: 1, exerciseGroups: [exerciseGroup2] } as Exam;
        fixture.componentRef.setInput('targetCourseId', 1);
        fixture.componentRef.setInput('targetExamId', 3);
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
        fixture.componentRef.setInput('subsequentExerciseGroupSelection', true);
        component.exam = exam1WithExercises;
        fixture.componentRef.setInput('targetCourseId', 1);
        fixture.componentRef.setInput('targetExamId', 2);
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1]);
    }
});
