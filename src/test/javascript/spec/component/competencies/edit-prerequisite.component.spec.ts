import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockComponent, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Lecture } from 'app/entities/lecture.model';
import { LectureService } from 'app/lecture/lecture.service';
import { CompetencyLectureUnitLink, CourseCompetencyProgress } from 'app/entities/competency.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ArtemisTestModule } from '../../test.module';
import { Prerequisite } from 'app/entities/prerequisite.model';
import { EditPrerequisiteComponent } from 'app/course/competencies/edit/edit-prerequisite.component';
import { PrerequisiteService } from 'app/course/competencies/prerequisite.service';
import { PrerequisiteFormComponent } from 'app/course/competencies/forms/prerequisite/prerequisite-form.component';
import { PrerequisiteFormStubComponent } from './prerequisite-form-stub.component';
import { MarkdownEditorMonacoComponent } from 'app/shared/markdown-editor/monaco/markdown-editor-monaco.component';
import { ArtemisMarkdownEditorModule } from 'app/shared/markdown-editor/markdown-editor.module';

describe('EditPrerequisiteComponent', () => {
    let editPrerequisiteComponentFixture: ComponentFixture<EditPrerequisiteComponent>;
    let editPrerequisiteComponent: EditPrerequisiteComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule, EditPrerequisiteComponent, PrerequisiteFormStubComponent],
            declarations: [],
            providers: [
                MockProvider(LectureService),
                MockProvider(PrerequisiteService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'competencyId':
                                        return 1;
                                }
                            },
                        }),
                        parent: {
                            parent: {
                                paramMap: of({
                                    get: (key: string) => {
                                        switch (key) {
                                            case 'courseId':
                                                return 1;
                                        }
                                    },
                                }),
                            },
                        },
                    },
                },
            ],
            schemas: [],
        })
            .overrideModule(ArtemisMarkdownEditorModule, {
                remove: { exports: [MarkdownEditorMonacoComponent] },
                add: { exports: [MockComponent(MarkdownEditorMonacoComponent)], declarations: [MockComponent(MarkdownEditorMonacoComponent)] },
            })
            .compileComponents()
            .then(() => {
                editPrerequisiteComponentFixture = TestBed.createComponent(EditPrerequisiteComponent);
                editPrerequisiteComponent = editPrerequisiteComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editPrerequisiteComponentFixture.detectChanges();
        expect(editPrerequisiteComponent).toBeDefined();
    });

    it('should set form data correctly', () => {
        // mocking competency service
        const prerequisiteService = TestBed.inject(PrerequisiteService);
        const lectureUnit = new TextUnit();
        lectureUnit.id = 1;

        const competencyOfResponse: Prerequisite = {};
        competencyOfResponse.id = 1;
        competencyOfResponse.title = 'test';
        competencyOfResponse.description = 'lorem ipsum';
        competencyOfResponse.optional = true;
        competencyOfResponse.lectureUnitLinks = [new CompetencyLectureUnitLink(competencyOfResponse, lectureUnit, 1)];

        const competencyResponse: HttpResponse<Prerequisite> = new HttpResponse({
            body: competencyOfResponse,
            status: 200,
        });
        const competencyCourseProgressResponse: HttpResponse<CourseCompetencyProgress> = new HttpResponse({
            body: { competencyId: 1, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress,
            status: 200,
        });

        const findByIdSpy = jest.spyOn(prerequisiteService, 'findById').mockReturnValue(of(competencyResponse));
        const getCourseProgressSpy = jest.spyOn(prerequisiteService, 'getCourseProgress').mockReturnValue(of(competencyCourseProgressResponse));

        // mocking lecture service
        const lectureService = TestBed.inject(LectureService);
        const lectureOfResponse = new Lecture();
        lectureOfResponse.id = 1;
        lectureOfResponse.lectureUnits = [lectureUnit];

        const lecturesResponse: HttpResponse<Lecture[]> = new HttpResponse<Lecture[]>({
            body: [lectureOfResponse],
            status: 200,
        });

        const findAllByCourseSpy = jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(of(lecturesResponse));

        editPrerequisiteComponentFixture.detectChanges();
        const competencyFormComponent = editPrerequisiteComponentFixture.debugElement.query(By.directive(PrerequisiteFormComponent)).componentInstance;
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(getCourseProgressSpy).toHaveBeenCalledOnce();
        expect(findAllByCourseSpy).toHaveBeenCalledOnce();

        expect(editPrerequisiteComponent.formData.title).toEqual(competencyOfResponse.title);
        expect(editPrerequisiteComponent.formData.description).toEqual(competencyOfResponse.description);
        expect(editPrerequisiteComponent.formData.optional).toEqual(competencyOfResponse.optional);
        expect(editPrerequisiteComponent.formData.lectureUnitLinks).toEqual(competencyOfResponse.lectureUnitLinks);
        expect(editPrerequisiteComponent.lecturesWithLectureUnits).toEqual([lectureOfResponse]);
        expect(competencyFormComponent.formData).toEqual(editPrerequisiteComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const prerequisiteService = TestBed.inject(PrerequisiteService);
        const lectureService = TestBed.inject(LectureService);

        const textUnit = new TextUnit();
        textUnit.id = 1;

        const competencyDatabase: Prerequisite = {};
        competencyDatabase.id = 1;
        competencyDatabase.title = 'test';
        competencyDatabase.description = 'lorem ipsum';
        competencyDatabase.optional = true;
        competencyDatabase.lectureUnitLinks = [new CompetencyLectureUnitLink(competencyDatabase, textUnit, 1)];

        const findByIdResponse: HttpResponse<Prerequisite> = new HttpResponse({
            body: competencyDatabase,
            status: 200,
        });
        const findByIdSpy = jest.spyOn(prerequisiteService, 'findById').mockReturnValue(of(findByIdResponse));
        jest.spyOn(prerequisiteService, 'getCourseProgress').mockReturnValue(
            of(
                new HttpResponse({
                    body: {},
                    status: 200,
                }),
            ),
        );
        jest.spyOn(lectureService, 'findAllByCourseId').mockReturnValue(
            of(
                new HttpResponse({
                    body: [new Lecture()],
                    status: 200,
                }),
            ),
        );
        editPrerequisiteComponentFixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(editPrerequisiteComponent.prerequisite).toEqual(competencyDatabase);

        const changedUnit: Prerequisite = {
            ...competencyDatabase,
            title: 'Changed',
            optional: false,
        };

        const updateResponse: HttpResponse<Prerequisite> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedSpy = jest.spyOn(prerequisiteService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const competencyForm = editPrerequisiteComponentFixture.debugElement.query(By.directive(PrerequisiteFormComponent)).componentInstance;
        competencyForm.formSubmitted.emit({
            title: changedUnit.title,
            description: changedUnit.description,
            optional: changedUnit.optional,
            lectureUnitLinks: changedUnit.lectureUnitLinks,
        });

        expect(updatedSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
