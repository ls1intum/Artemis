import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ArtemisTestModule } from '../test.module';
import { MockFileUploadExerciseService } from '../helpers/mocks/service/mock-file-upload-exercise.service';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { MockComponent, MockProvider } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MockDirective } from 'ng-mocks';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';
import { ExerciseType } from 'app/entities/exercise.model';
import { MockRouter } from '../helpers/mocks/mock-router';
import { Router } from '@angular/router';
import { MockRouterLinkDirective } from '../helpers/mocks/directive/mock-router-link.directive';

describe('Exercise detail common actions Component', () => {
    let comp: NonProgrammingExerciseDetailCommonActionsComponent;
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;

    const course: Course = { id: 123 };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                NonProgrammingExerciseDetailCommonActionsComponent,
                MockComponent(SubmissionExportButtonComponent),
                MockDirective(DeleteButtonDirective),
                MockComponent(ExternalSubmissionButtonComponent),
                MockRouterLinkDirective,
            ],
            providers: [
                MockProvider(TextExerciseService),
                { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService },
                MockProvider(ModelingExerciseService),
                { provide: Router, useClass: MockRouter },
            ],
        }).compileComponents();
        fixture = TestBed.createComponent(NonProgrammingExerciseDetailCommonActionsComponent);
        comp = fixture.componentInstance;
    });

    it('should be ok', () => {
        comp.exercise = { type: ExerciseType.TEXT, id: 1 } as TextExercise;
        comp.course = course;
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should get the correct edit routes for non exam exercise', () => {
        const textExercise: TextExercise = new TextExercise(course, undefined);
        textExercise.id = 123;

        comp.exercise = textExercise;
        comp.course = course;
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/text-exercises/123/');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.id = 123;

        comp.exercise = fileUploadExercise;
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/file-upload-exercises/123/');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        modelingExercise.id = 123;

        comp.exercise = modelingExercise;
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/modeling-exercises/123/');
    });

    it('should get the correct edit routes for exam exercise', () => {
        const exam: Exam = { id: 2, course };
        const exerciseGroup: ExerciseGroup = { id: 3, exam };
        const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
        textExercise.id = 4;

        comp.isExamExercise = true;
        comp.exercise = textExercise;
        comp.course = course;
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/exams/2/exercise-groups/3/text-exercises/4/');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, exerciseGroup);
        fileUploadExercise.id = 5;

        comp.exercise = fileUploadExercise;
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/exams/2/exercise-groups/3/file-upload-exercises/5/');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, exerciseGroup);
        modelingExercise.id = 6;

        comp.exercise = modelingExercise;
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/exams/2/exercise-groups/3/modeling-exercises/6/');
    });

    it('should call event manager on delete exercises', () => {
        const textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
        const fileUploadExerciseService = fixture.debugElement.injector.get(FileUploadExerciseService);
        const modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);

        const deleteTextExerciseService = jest.spyOn(textExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<{}>));
        const deleteFileUploadExerciseStub = jest.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<{}>));
        const deleteModelingExerciseService = jest.spyOn(modelingExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<{}>));

        comp.course = course;

        comp.exercise = new TextExercise({ id: 123 }, undefined);
        comp.deleteExercise();
        expect(deleteTextExerciseService).toHaveBeenCalledOnce();

        comp.exercise = new FileUploadExercise({ id: 123 }, undefined);
        comp.deleteExercise();
        expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();

        comp.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, { id: 123 }, undefined);
        comp.deleteExercise();
        expect(deleteModelingExerciseService).toHaveBeenCalledOnce();
    });
});
