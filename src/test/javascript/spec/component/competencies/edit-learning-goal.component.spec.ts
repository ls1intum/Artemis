import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockPipe, MockProvider } from 'ng-mocks';
import { AlertService } from 'app/core/util/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Lecture } from 'app/entities/lecture.model';
import { EditCompetencyComponent } from 'app/course/competencies/edit-competency/edit-competency.component';
import { CompetencyService } from 'app/course/competencies/competency.service';
import { LectureService } from 'app/lecture/lecture.service';
import { Competency, CourseCompetencyProgress } from 'app/entities/competency.model';
import { TextUnit } from 'app/entities/lecture-unit/textUnit.model';
import { MockRouter } from '../../helpers/mocks/mock-router';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { CompetencyFormStubComponent } from './competency-form-stub.component';
import { ArtemisTestModule } from '../../test.module';

describe('EditLearningGoalComponent', () => {
    let editLearningGoalComponentFixture: ComponentFixture<EditCompetencyComponent>;
    let editLearningGoalComponent: EditCompetencyComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [CompetencyFormStubComponent, EditCompetencyComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [
                MockProvider(LectureService),
                MockProvider(CompetencyService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                {
                    provide: ActivatedRoute,
                    useValue: {
                        paramMap: of({
                            get: (key: string) => {
                                switch (key) {
                                    case 'learningGoalId':
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
            .compileComponents()
            .then(() => {
                editLearningGoalComponentFixture = TestBed.createComponent(EditCompetencyComponent);
                editLearningGoalComponent = editLearningGoalComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editLearningGoalComponentFixture.detectChanges();
        expect(editLearningGoalComponent).toBeDefined();
    });

    it('should set form data correctly', () => {
        // mocking competency service
        const learningGoalService = TestBed.inject(CompetencyService);
        const lectureUnit = new TextUnit();
        lectureUnit.id = 1;

        const learningGoalOfResponse = new Competency();
        learningGoalOfResponse.id = 1;
        learningGoalOfResponse.title = 'test';
        learningGoalOfResponse.description = 'lorem ipsum';
        learningGoalOfResponse.lectureUnits = [lectureUnit];

        const learningGoalResponse: HttpResponse<Competency> = new HttpResponse({
            body: learningGoalOfResponse,
            status: 200,
        });
        const learningGoalCourseProgressResponse: HttpResponse<CourseCompetencyProgress> = new HttpResponse({
            body: { competencyId: 1, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress,
            status: 200,
        });

        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(learningGoalResponse));
        const getCourseProgressSpy = jest.spyOn(learningGoalService, 'getCourseProgress').mockReturnValue(of(learningGoalCourseProgressResponse));

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

        editLearningGoalComponentFixture.detectChanges();
        const learningGoalFormStubComponent: CompetencyFormStubComponent = editLearningGoalComponentFixture.debugElement.query(
            By.directive(CompetencyFormStubComponent),
        ).componentInstance;
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(getCourseProgressSpy).toHaveBeenCalledOnce();
        expect(findAllByCourseSpy).toHaveBeenCalledOnce();

        expect(editLearningGoalComponent.formData.title).toEqual(learningGoalOfResponse.title);
        expect(editLearningGoalComponent.formData.description).toEqual(learningGoalOfResponse.description);
        expect(editLearningGoalComponent.formData.connectedLectureUnits).toEqual(learningGoalOfResponse.lectureUnits);
        expect(editLearningGoalComponent.lecturesWithLectureUnits).toEqual([lectureOfResponse]);
        expect(learningGoalFormStubComponent.formData).toEqual(editLearningGoalComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const learningGoalService = TestBed.inject(CompetencyService);
        const lectureService = TestBed.inject(LectureService);

        const textUnit = new TextUnit();
        textUnit.id = 1;
        const learningGoalDatabase: Competency = new Competency();
        learningGoalDatabase.id = 1;
        learningGoalDatabase.title = 'test';
        learningGoalDatabase.description = 'lorem ipsum';
        learningGoalDatabase.lectureUnits = [textUnit];

        const findByIdResponse: HttpResponse<Competency> = new HttpResponse({
            body: learningGoalDatabase,
            status: 200,
        });
        const findByIdSpy = jest.spyOn(learningGoalService, 'findById').mockReturnValue(of(findByIdResponse));
        jest.spyOn(learningGoalService, 'getCourseProgress').mockReturnValue(
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
        editLearningGoalComponentFixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(editLearningGoalComponent.competency).toEqual(learningGoalDatabase);

        const changedUnit: Competency = {
            ...learningGoalDatabase,
            title: 'Changed',
        };

        const updateResponse: HttpResponse<Competency> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedSpy = jest.spyOn(learningGoalService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const learningGoalForm: CompetencyFormStubComponent = editLearningGoalComponentFixture.debugElement.query(By.directive(CompetencyFormStubComponent)).componentInstance;
        learningGoalForm.formSubmitted.emit({
            title: changedUnit.title,
            description: changedUnit.description,
            connectedLectureUnits: changedUnit.lectureUnits,
        });

        expect(updatedSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
