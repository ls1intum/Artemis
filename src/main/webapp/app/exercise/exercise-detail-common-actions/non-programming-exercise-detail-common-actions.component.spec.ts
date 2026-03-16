import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ProfileInfo } from 'app/core/layouts/profiles/profile-info.model';
import { FileUploadExerciseService } from 'app/fileupload/manage/services/file-upload-exercise.service';
import { NonProgrammingExerciseDetailCommonActionsComponent } from 'app/exercise/exercise-detail-common-actions/non-programming-exercise-detail-common-actions.component';
import { MockFileUploadExerciseService } from 'test/helpers/mocks/service/mock-file-upload-exercise.service';
import { SubmissionExportButtonComponent } from 'app/exercise/submission-export/button/submission-export-button.component';
import { MockComponent, MockDirective, MockProvider } from 'ng-mocks';
import { DeleteButtonDirective } from 'app/shared/delete-dialog/directive/delete-button.directive';
import { TextExercise } from 'app/text/shared/entities/text-exercise.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { FileUploadExercise } from 'app/fileupload/shared/entities/file-upload-exercise.model';
import { ModelingExercise } from 'app/modeling/shared/entities/modeling-exercise.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { ExerciseGroup } from 'app/exam/shared/entities/exercise-group.model';
import { TextExerciseService } from 'app/text/manage/text-exercise/service/text-exercise.service';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { ModelingExerciseService } from 'app/modeling/manage/services/modeling-exercise.service';
import { ExternalSubmissionButtonComponent } from 'app/exercise/external-submission/external-submission-button.component';
import { ExerciseType } from 'app/exercise/shared/entities/exercise/exercise.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { ActivatedRoute, Router } from '@angular/router';
import { MockRouterLinkDirective } from 'test/helpers/mocks/directive/mock-router-link.directive';
import { UMLDiagramType } from '@ls1intum/apollon';
import { ProfileService } from 'app/core/layouts/profiles/shared/profile.service';
import { MockActivatedRoute } from 'test/helpers/mocks/activated-route/mock-activated-route';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { FaIconComponent } from '@fortawesome/angular-fontawesome';

describe('Exercise detail common actions Component', () => {
    let comp: NonProgrammingExerciseDetailCommonActionsComponent;
    let fixture: ComponentFixture<NonProgrammingExerciseDetailCommonActionsComponent>;

    const course: Course = { id: 123 };

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [FaIconComponent],
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
                { provide: ActivatedRoute, useValue: new MockActivatedRoute() },
                { provide: TranslateService, useClass: MockTranslateService },
                MockProvider(ProfileService),
            ],
        }).compileComponents();
        jest.spyOn(TestBed.inject(ProfileService), 'getProfileInfo').mockReturnValue({} as ProfileInfo);
        fixture = TestBed.createComponent(NonProgrammingExerciseDetailCommonActionsComponent);
        comp = fixture.componentInstance;
    });

    it('should be ok', () => {
        fixture.componentRef.setInput('exercise', { type: ExerciseType.TEXT, id: 1 } as TextExercise);
        fixture.componentRef.setInput('course', course);
        fixture.detectChanges();
        expect(comp).not.toBeNull();
    });

    it('should get the correct edit routes for non exam exercise', () => {
        const textExercise: TextExercise = new TextExercise(course, undefined);
        textExercise.id = 123;

        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('course', course);
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/text-exercises/123/');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, undefined);
        fileUploadExercise.id = 123;

        fixture.componentRef.setInput('exercise', fileUploadExercise);
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/file-upload-exercises/123/');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined);
        modelingExercise.id = 123;

        fixture.componentRef.setInput('exercise', modelingExercise);
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/modeling-exercises/123/');
    });

    it('should get the correct edit routes for exam exercise', () => {
        const exam: Exam = { id: 2, course };
        const exerciseGroup: ExerciseGroup = { id: 3, exam };
        const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
        textExercise.id = 4;

        fixture.componentRef.setInput('isExamExercise', true);
        fixture.componentRef.setInput('exercise', textExercise);
        fixture.componentRef.setInput('course', course);
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/exams/2/exercise-groups/3/text-exercises/4/');

        const fileUploadExercise: FileUploadExercise = new FileUploadExercise(course, exerciseGroup);
        fileUploadExercise.id = 5;

        fixture.componentRef.setInput('exercise', fileUploadExercise);
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/exams/2/exercise-groups/3/file-upload-exercises/5/');

        const modelingExercise: ModelingExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, exerciseGroup);
        modelingExercise.id = 6;

        fixture.componentRef.setInput('exercise', modelingExercise);
        comp.ngOnInit();
        expect(comp.baseResource).toBe('/course-management/123/exams/2/exercise-groups/3/modeling-exercises/6/');
    });

    describe('canAccessParticipationsAndScores', () => {
        it('should return true for course exercise when user is at least tutor', () => {
            const textExercise: TextExercise = new TextExercise(course, undefined);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = true;
            textExercise.isAtLeastInstructor = false;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', false);

            expect(comp.canAccessParticipationsAndScores()).toBeTrue();
        });

        it('should return false for course exercise when user is not at least tutor', () => {
            const textExercise: TextExercise = new TextExercise(course, undefined);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = false;
            textExercise.isAtLeastInstructor = false;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', false);

            expect(comp.canAccessParticipationsAndScores()).toBeFalse();
        });

        it('should return false for exam exercise when user is only tutor', () => {
            const exam: Exam = { id: 2, course };
            const exerciseGroup: ExerciseGroup = { id: 3, exam };
            const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = true;
            textExercise.isAtLeastInstructor = false;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', true);

            expect(comp.canAccessParticipationsAndScores()).toBeFalse();
        });

        it('should return true for exam exercise when user is at least instructor', () => {
            const exam: Exam = { id: 2, course };
            const exerciseGroup: ExerciseGroup = { id: 3, exam };
            const textExercise: TextExercise = new TextExercise(course, exerciseGroup);
            textExercise.id = 1;
            textExercise.isAtLeastTutor = true;
            textExercise.isAtLeastInstructor = true;

            fixture.componentRef.setInput('exercise', textExercise);
            fixture.componentRef.setInput('course', course);
            fixture.componentRef.setInput('isExamExercise', true);

            expect(comp.canAccessParticipationsAndScores()).toBeTrue();
        });
    });

    it('should call event manager on delete exercises', () => {
        const textExerciseService = TestBed.inject(TextExerciseService);
        const fileUploadExerciseService = TestBed.inject(FileUploadExerciseService);
        const modelingExerciseService = TestBed.inject(ModelingExerciseService);

        const deleteTextExerciseService = jest.spyOn(textExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));
        const deleteFileUploadExerciseStub = jest.spyOn(fileUploadExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));
        const deleteModelingExerciseService = jest.spyOn(modelingExerciseService, 'delete').mockReturnValue(of({} as HttpResponse<any>));

        fixture.componentRef.setInput('course', course);

        fixture.componentRef.setInput('exercise', new TextExercise({ id: 123 }, undefined));
        comp.deleteExercise();
        expect(deleteTextExerciseService).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('exercise', new FileUploadExercise({ id: 123 }, undefined));
        comp.deleteExercise();
        expect(deleteFileUploadExerciseStub).toHaveBeenCalledOnce();

        fixture.componentRef.setInput('exercise', new ModelingExercise(UMLDiagramType.ClassDiagram, { id: 123 }, undefined));
        comp.deleteExercise();
        expect(deleteModelingExerciseService).toHaveBeenCalledOnce();
    });
});
