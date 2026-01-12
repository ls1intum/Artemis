import { HttpErrorResponse, HttpResponse } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { DynamicDialogRef } from 'primeng/dynamicdialog';
import { AlertService } from 'app/shared/service/alert.service';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { ExamManagementService } from 'app/exam/manage/services/exam-management.service';
import { ExamExerciseImportComponent } from 'app/exam/manage/exams/exam-exercise-import/exam-exercise-import.component';
import { ExamImportPagingService } from 'app/exam/manage/exams/exam-import/exam-import-paging.service';
import { ExamImportComponent } from 'app/exam/manage/exams/exam-import/exam-import.component';
import { DifficultyBadgeComponent } from 'app/exercise/exercise-headers/difficulty-badge/difficulty-badge.component';
import { ButtonComponent } from 'app/shared/components/buttons/button/button.component';
import { HelpIconComponent } from 'app/shared/components/help-icon/help-icon.component';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { SortService } from 'app/shared/service/sort.service';
import { SortByDirective } from 'app/shared/sort/directive/sort-by.directive';
import { SortDirective } from 'app/shared/sort/directive/sort.directive';
import { MockComponent, MockDirective, MockPipe, MockProvider } from 'ng-mocks';
import { Subject, of, throwError } from 'rxjs';
import { UMLDiagramType } from '@ls1intum/apollon';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';
import { MODULE_FEATURE_TEXT } from 'app/app.constants';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('Exam Import Component', () => {
    let component: ExamImportComponent;
    let fixture: ComponentFixture<ExamImportComponent>;
    let dialogRef: DynamicDialogRef;
    let dialogRefCloseSpy: jest.SpyInstance;
    let examManagementService: ExamManagementService;
    let alertService: AlertService;
    let profileService: ProfileService;
    let getProfileInfoStub: jest.SpyInstance;

    const exam1 = { id: 1 } as Exam;

    // Initializing one Exercise Group
    const exerciseGroup1 = { title: 'exerciseGroup1' } as ExerciseGroup;
    const modelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup1);
    modelingExercise.id = 1;
    modelingExercise.title = 'ModelingExercise';
    exerciseGroup1.exercises = [modelingExercise];
    const exam1WithExercises = { id: 1, exerciseGroups: [exerciseGroup1] } as Exam;

    beforeEach(() => {
        dialogRefCloseSpy = jest.fn();
        dialogRef = {
            close: dialogRefCloseSpy,
            onClose: new Subject<any>(),
        } as unknown as DynamicDialogRef;

        TestBed.configureTestingModule({
            imports: [FormsModule, FaIconComponent],
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
                { provide: DynamicDialogRef, useValue: dialogRef },
                MockProvider(ExamManagementService),
                MockProvider(AlertService),
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: ProfileService, useClass: MockProfileService },
            ],
        })
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ExamImportComponent);
                component = fixture.componentInstance;
                examManagementService = TestBed.inject(ExamManagementService);
                alertService = TestBed.inject(AlertService);

                profileService = TestBed.inject(ProfileService);

                getProfileInfoStub = jest.spyOn(profileService, 'getProfileInfo');
                getProfileInfoStub.mockReturnValue(of({ activeModuleFeatures: [MODULE_FEATURE_TEXT] }));
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
        component.subsequentExerciseGroupSelection.set(false);
        component.performImportOfExerciseGroups();

        component.subsequentExerciseGroupSelection.set(true);
        component.exam = undefined;
        component.performImportOfExerciseGroups();

        component.exam = exam1WithExercises;
        component.targetExamId.set(undefined);
        component.performImportOfExerciseGroups();

        component.targetExamId.set(1);
        component.targetCourseId.set(undefined);
        component.performImportOfExerciseGroups();

        expect(importSpy).not.toHaveBeenCalled();
        expect(alertSpy).not.toHaveBeenCalled();
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
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

        performImport(importSpy);
        expect(alertSpy).not.toHaveBeenCalled();
        expect(dialogRefCloseSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).toHaveBeenCalledWith([exerciseGroup1]);
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

        component.subsequentExerciseGroupSelection.set(true);
        const exerciseGroup2 = { title: 'exerciseGroup2' } as ExerciseGroup;
        const modelingExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, undefined, exerciseGroup2);
        modelingExercise2.id = 2;
        exerciseGroup2.exercises = [modelingExercise2];
        component.exam = { id: 1, exerciseGroups: [exerciseGroup2] } as Exam;
        component.targetCourseId.set(1);
        component.targetExamId.set(3);
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).not.toHaveBeenCalled();
        expect(alertSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
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

            performImport(importSpy);
            if (errorKey == 'invalidKey') {
                expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.invalidKey', { number: 0 });
            } else {
                expect(alertSpy).toHaveBeenCalledWith('artemisApp.examManagement.exerciseGroup.importModal.' + errorKey);
            }
            expect(dialogRefCloseSpy).not.toHaveBeenCalled();
        },
    );

    it('should perform import of exercise groups AND correctly process arbitrary exception from server', () => {
        const error = new HttpErrorResponse({
            status: 400,
        });
        const importSpy = jest.spyOn(examManagementService, 'importExerciseGroup').mockReturnValue(throwError(() => error));
        const alertSpy = jest.spyOn(alertService, 'error');
        performImport(importSpy);

        expect(alertSpy).toHaveBeenCalledOnce();
        expect(dialogRefCloseSpy).not.toHaveBeenCalled();
    });

    function performImport(importSpy: jest.SpyInstance): void {
        component.exam = exam1WithExercises;
        component.subsequentExerciseGroupSelection.set(true);
        component.targetCourseId.set(1);
        component.targetExamId.set(2);
        fixture.detectChanges();
        component.performImportOfExerciseGroups();
        expect(importSpy).toHaveBeenCalledOnce();
        expect(importSpy).toHaveBeenCalledWith(1, 2, [exerciseGroup1]);
    }
});
