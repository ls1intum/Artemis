import { Course } from 'app/entities/course.model';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { NgbActiveModal } from '@ng-bootstrap/ng-bootstrap';
import { of } from 'rxjs';
import { ApollonDiagram } from 'app/entities/apollon-diagram.model';
import { UMLDiagramType } from 'app/entities/modeling-exercise.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { HttpResponse } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import * as testClassDiagram from '../../../util/modeling/test-models/class-diagram.json';
import { Text } from '@ls1intum/apollon/lib/es5/utils/svg/text';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { MockRouter } from '../../../helpers/mocks/mock-router';
import { CourseManagementService } from 'app/course/manage/course-management.service';
import { ApollonQuizExerciseGenerationComponent } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/apollon-quiz-exercise-generation.component';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { ExerciseService } from 'app/exercises/shared/exercise/exercise.service';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { SubmissionService } from 'app/exercises/shared/submission/submission.service';
import { AccountService } from 'app/core/auth/account.service';
import { MockSyncStorage } from '../../../helpers/mocks/service/mock-sync-storage.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { SpyLocation } from '@angular/common/testing';
import { ApollonEditor, ApollonMode, Locale } from '@ls1intum/apollon';

// has to be overridden, because jsdom does not provide a getBBox() function for SVGTextElements
Text.size = () => {
    return { width: 0, height: 0 };
};

describe('ApollonQuizExerciseGeneration Component', () => {
    let courseManagementService: CourseManagementService;
    let ngbModal: NgbActiveModal;
    let fixture: ComponentFixture<ApollonQuizExerciseGenerationComponent>;

    const course: Course = { id: 123, title: 'TestCourse' } as Course;
    const diagram: ApollonDiagram = new ApollonDiagram(UMLDiagramType.ClassDiagram, course.id!);

    beforeEach(() => {
        diagram.id = 1;
        diagram.jsonRepresentation = JSON.stringify(testClassDiagram);

        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            declarations: [ApollonQuizExerciseGenerationComponent],
            providers: [
                CourseManagementService,
                FileUploaderService,
                QuizExerciseService,
                ExerciseService,
                ParticipationService,
                SubmissionService,
                AccountService,
                NgbActiveModal,
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: Location, useClass: SpyLocation },
                { provide: Router, useClass: MockRouter },
            ],
            schemas: [],
        })
            .overrideTemplate(ApollonQuizExerciseGenerationComponent, '')
            .compileComponents()
            .then(() => {
                fixture = TestBed.createComponent(ApollonQuizExerciseGenerationComponent);
                courseManagementService = fixture.debugElement.injector.get(CourseManagementService);
                ngbModal = fixture.debugElement.injector.get(NgbActiveModal);
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('ngOnInit', () => {
        jest.spyOn(fixture.componentInstance, 'getCourseId').mockReturnValue(course.id!);
        const response: HttpResponse<ApollonDiagram> = new HttpResponse({ body: course });
        jest.spyOn(courseManagementService, 'find').mockReturnValue(of(response));

        // test
        fixture.componentInstance.ngOnInit();
        expect(fixture.componentInstance.course).toEqual(course);
        expect(fixture.componentInstance.courseTitle).toEqual(course.title);
    });

    it('save', async () => {
        const div = document.createElement('div');
        fixture.componentInstance.apollonEditor = new ApollonEditor(div, {
            mode: ApollonMode.Exporting,
            model: undefined,
            type: UMLDiagramType.ClassDiagram,
            locale: Locale.de,
        });
        fixture.componentInstance.course = course;

        const quizExercise: QuizExercise = new QuizExercise(course, undefined);
        const module = require('app/exercises/quiz/manage/apollon-diagrams/exercise-generation/quiz-exercise-generator');
        jest.spyOn(module, 'generateDragAndDropQuizExercise').mockReturnValue(quizExercise);
        const ngbModalSpy = jest.spyOn(ngbModal, 'close');

        // test
        await fixture.componentInstance.save();
        expect(ngbModalSpy).toBeCalledTimes(1);
    });
});
