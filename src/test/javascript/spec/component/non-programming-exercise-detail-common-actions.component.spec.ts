import { ComponentFixture, TestBed } from '@angular/core/testing';
import * as sinonChai from 'sinon-chai';
import * as chai from 'chai';
import { JhiLanguageHelper } from 'app/core/language/language.helper';
import { JhiAlertService } from 'ng-jhipster';
import { FileUploadExerciseService } from 'app/exercises/file-upload/manage/file-upload-exercise.service';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercises/shared/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { ArtemisTestModule } from '../test.module';
import { MockFileUploadExerciseService } from '../helpers/mocks/service/mock-file-upload-exercise.service';
import { SubmissionExportButtonComponent } from 'app/exercises/shared/submission-export/submission-export-button.component';
import { MockComponent } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/delete-button.directive';
import { MockDirective } from 'ng-mocks/dist/lib/mock-directive/mock-directive';
import { TranslateModule } from '@ngx-translate/core';
import { ProgrammingExerciseDetailComponent } from 'app/exercises/programming/manage/programming-exercise-detail.component';
import { RouterTestingModule } from '@angular/router/testing';
import { TextExercise } from 'app/entities/text-exercise.model';
import { Course } from 'app/entities/course.model';
import { FileUploadExercise } from 'app/entities/file-upload-exercise.model';
import { ModelingExercise, UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { Exam } from 'app/entities/exam.model';
import { ExerciseGroup } from 'app/entities/exercise-group.model';
import { TextExerciseService } from 'app/exercises/text/manage/text-exercise/text-exercise.service';
import { of } from 'rxjs/internal/observable/of';
import { HttpResponse } from '@angular/common/http';
import * as sinon from 'sinon';
import { ModelingExerciseService } from 'app/exercises/modeling/manage/modeling-exercise.service';
import { ExternalSubmissionButtonComponent } from 'app/exercises/shared/external-submission/external-submission-button.component';

chai.use(sinonChai);
const expect = chai.expect;

describe('Exercise detail common actions Component', () => {
    let comp: NonProgrammingExerciseDetailCommonActionsComponent;
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, RouterTestingModule, TranslateModule.forRoot()],
            declarations: [
                NonProgrammingExerciseDetailCommonActionsComponent,
                MockComponent(SubmissionExportButtonComponent),
                MockDirective(DeleteButtonDirective),
                MockComponent(ExternalSubmissionButtonComponent),
            ],
            providers: [JhiLanguageHelper, JhiAlertService, { provide: FileUploadExerciseService, useClass: MockFileUploadExerciseService }],
        })
            .overrideTemplate(ProgrammingExerciseDetailComponent, '')
            .compileComponents();
        fixture = TestBed.createComponent(NonProgrammingExerciseDetailCommonActionsComponent);
        comp = fixture.componentInstance;
    });

    it('Should be ok', () => {
        fixture.detectChanges();
        expect(comp).to.be.ok;
    });

    it('should get the correct edit routes for non exam exercise', () => {
        const course: Course = { id: 123 };
        const textExercise: TextExercise = new TextExercise(course, undefined);
        textExercise.id = 123;

        comp.exercise = textExercise;
        comp.courseId = course.id!;
        expect(comp.getEditRoute().join('/')).to.equal('/course-management/123/text-exercises/123/edit');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.id = 123;

        comp.exercise = fileUploadExercise;
        expect(comp.getEditRoute().join('/')).to.equal('/course-management/123/file-upload-exercises/123/edit');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        modelingExercise.id = 123;

        comp.exercise = modelingExercise;
        expect(comp.getEditRoute().join('/')).to.equal('/course-management/123/modeling-exercises/123/edit');
    });

    it('should get the correct edit routes for exam exercise', () => {
        const course: Course = { id: 1 };
        const exam: Exam = { id: 2, course };
        const exerciseGroup: ExerciseGroup = { id: 3, exam };
        const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
        textExercise.id = 4;

        comp.isExamExercise = true;
        comp.exercise = textExercise;
        comp.courseId = course.id!;
        expect(comp.getEditRoute().join('/')).to.equal('/course-management/1/exams/2/exercise-groups/3/text-exercises/4/edit');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, exerciseGroup);
        fileUploadExercise.id = 5;

        comp.exercise = fileUploadExercise;
        expect(comp.getEditRoute().join('/')).to.equal('/course-management/1/exams/2/exercise-groups/3/file-upload-exercises/5/edit');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, exerciseGroup);
        modelingExercise.id = 6;

        comp.exercise = modelingExercise;
        expect(comp.getEditRoute().join('/')).to.equal('/course-management/1/exams/2/exercise-groups/3/modeling-exercises/6/edit');
    });

    it('should call event manager on delete exercises', function () {
        const textExerciseService = fixture.debugElement.injector.get(TextExerciseService);
        const fileUploadExerciseService = fixture.debugElement.injector.get(FileUploadExerciseService);
        const modelingExerciseService = fixture.debugElement.injector.get(ModelingExerciseService);

        const deleteTextExerciseService = sinon.stub(textExerciseService, 'delete').returns(of({} as HttpResponse<{}>));
        const deleteFileUploadExerciseStub = sinon.stub(fileUploadExerciseService, 'delete').returns(of({} as HttpResponse<{}>));
        const deleteModelingExerciseService = sinon.stub(modelingExerciseService, 'delete').returns(of({} as HttpResponse<{}>));

        comp.courseId = 123;

        comp.exercise = new TextExercise({ id: 123 }, undefined);
        comp.deleteExercise();
        expect(deleteTextExerciseService).to.have.been.called;

        comp.exercise = new FileUploadExercise({ id: 123 }, undefined);
        comp.deleteExercise();
        expect(deleteFileUploadExerciseStub).to.have.been.called;

        comp.exercise = new ModelingExercise(UMLDiagramType.ClassDiagram, { id: 123 }, undefined);
        comp.deleteExercise();
        expect(deleteModelingExerciseService).to.have.been.called;
    });
});
