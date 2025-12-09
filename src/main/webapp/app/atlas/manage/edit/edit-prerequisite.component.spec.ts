import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router, convertToParamMap } from '@angular/router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { CompetencyLectureUnitLink, CourseCompetencyProgress } from 'app/atlas/shared/entities/competency.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { Prerequisite } from 'app/atlas/shared/entities/prerequisite.model';
import { EditPrerequisiteComponent } from 'app/atlas/manage/edit/edit-prerequisite.component';
import { PrerequisiteService } from 'app/atlas/manage/services/prerequisite.service';
import { PrerequisiteFormComponent } from 'app/atlas/manage/forms/prerequisite/prerequisite-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';

describe('EditPrerequisiteComponent', () => {
    let editPrerequisiteComponentFixture: ComponentFixture<EditPrerequisiteComponent>;
    let editPrerequisiteComponent: EditPrerequisiteComponent;
    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EditPrerequisiteComponent, MockModule(OwlNativeDateTimeModule), MockComponent(PrerequisiteFormComponent), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(LectureService),
                MockProvider(PrerequisiteService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of(convertToParamMap({ prerequisiteId: 1 })),
                        snapshot: { paramMap: convertToParamMap({ prerequisiteId: 1 }) },
                        parent: {
                            parent: {
                                paramMap: of(convertToParamMap({ courseId: 1 })),
                                snapshot: { paramMap: convertToParamMap({ courseId: 1 }) },
                            },
                        },
                    },
                },
            ],
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        editPrerequisiteComponentFixture = TestBed.createComponent(EditPrerequisiteComponent);
        editPrerequisiteComponent = editPrerequisiteComponentFixture.componentInstance;
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

        editPrerequisiteComponentFixture.detectChanges();
        const competencyFormComponent = editPrerequisiteComponentFixture.debugElement.query(By.directive(PrerequisiteFormComponent)).componentInstance;
        expect(findByIdSpy).toHaveBeenCalledWith(1, 1);
        expect(getCourseProgressSpy).toHaveBeenCalledWith(1, 1);

        expect(editPrerequisiteComponent.prerequisite.courseProgress).toEqual(competencyCourseProgressResponse.body);
        expect(editPrerequisiteComponent.formData).toMatchObject({
            id: competencyOfResponse.id,
            title: competencyOfResponse.title,
            description: competencyOfResponse.description,
            optional: competencyOfResponse.optional,
        });
        expect(competencyFormComponent.formData).toEqual(editPrerequisiteComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const prerequisiteService = TestBed.inject(PrerequisiteService);

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
        editPrerequisiteComponentFixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(editPrerequisiteComponent.prerequisite).toEqual(competencyDatabase);

        const changedUnit: Prerequisite = Object.assign({}, competencyDatabase, { title: 'Changed', optional: false });

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

        expect(updatedSpy).toHaveBeenCalledWith(expect.objectContaining(changedUnit), 1);
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
