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

describe('EditCompetencyComponent', () => {
    let editCompetencyComponentFixture: ComponentFixture<EditCompetencyComponent>;
    let editCompetencyComponent: EditCompetencyComponent;
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
            .compileComponents()
            .then(() => {
                editCompetencyComponentFixture = TestBed.createComponent(EditCompetencyComponent);
                editCompetencyComponent = editCompetencyComponentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        editCompetencyComponentFixture.detectChanges();
        expect(editCompetencyComponent).toBeDefined();
    });

    it('should set form data correctly', () => {
        // mocking competency service
        const competencyService = TestBed.inject(CompetencyService);
        const lectureUnit = new TextUnit();
        lectureUnit.id = 1;

        const competencyOfResponse = new Competency();
        competencyOfResponse.id = 1;
        competencyOfResponse.title = 'test';
        competencyOfResponse.description = 'lorem ipsum';
        competencyOfResponse.optional = true;
        competencyOfResponse.lectureUnits = [lectureUnit];

        const competencyResponse: HttpResponse<Competency> = new HttpResponse({
            body: competencyOfResponse,
            status: 200,
        });
        const competencyCourseProgressResponse: HttpResponse<CourseCompetencyProgress> = new HttpResponse({
            body: { competencyId: 1, numberOfStudents: 8, numberOfMasteredStudents: 5, averageStudentScore: 90 } as CourseCompetencyProgress,
            status: 200,
        });

        const findByIdSpy = jest.spyOn(competencyService, 'findById').mockReturnValue(of(competencyResponse));
        const getCourseProgressSpy = jest.spyOn(competencyService, 'getCourseProgress').mockReturnValue(of(competencyCourseProgressResponse));

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

        editCompetencyComponentFixture.detectChanges();
        const competencyFormStubComponent: CompetencyFormStubComponent = editCompetencyComponentFixture.debugElement.query(
            By.directive(CompetencyFormStubComponent),
        ).componentInstance;
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(getCourseProgressSpy).toHaveBeenCalledOnce();
        expect(findAllByCourseSpy).toHaveBeenCalledOnce();

        expect(editCompetencyComponent.formData.title).toEqual(competencyOfResponse.title);
        expect(editCompetencyComponent.formData.description).toEqual(competencyOfResponse.description);
        expect(editCompetencyComponent.formData.optional).toEqual(competencyOfResponse.optional);
        expect(editCompetencyComponent.formData.connectedLectureUnits).toEqual(competencyOfResponse.lectureUnits);
        expect(editCompetencyComponent.lecturesWithLectureUnits).toEqual([lectureOfResponse]);
        expect(competencyFormStubComponent.formData).toEqual(editCompetencyComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const competencyService = TestBed.inject(CompetencyService);
        const lectureService = TestBed.inject(LectureService);

        const textUnit = new TextUnit();
        textUnit.id = 1;

        const competencyDatabase: Competency = new Competency();
        competencyDatabase.id = 1;
        competencyDatabase.title = 'test';
        competencyDatabase.description = 'lorem ipsum';
        competencyDatabase.optional = true;
        competencyDatabase.lectureUnits = [textUnit];

        const findByIdResponse: HttpResponse<Competency> = new HttpResponse({
            body: competencyDatabase,
            status: 200,
        });
        const findByIdSpy = jest.spyOn(competencyService, 'findById').mockReturnValue(of(findByIdResponse));
        jest.spyOn(competencyService, 'getCourseProgress').mockReturnValue(
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
        editCompetencyComponentFixture.detectChanges();
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(editCompetencyComponent.competency).toEqual(competencyDatabase);

        const changedUnit: Competency = {
            ...competencyDatabase,
            title: 'Changed',
            optional: false,
        };

        const updateResponse: HttpResponse<Competency> = new HttpResponse({
            body: changedUnit,
            status: 200,
        });
        const updatedSpy = jest.spyOn(competencyService, 'update').mockReturnValue(of(updateResponse));
        const navigateSpy = jest.spyOn(router, 'navigate');

        const competencyForm: CompetencyFormStubComponent = editCompetencyComponentFixture.debugElement.query(By.directive(CompetencyFormStubComponent)).componentInstance;
        competencyForm.formSubmitted.emit({
            title: changedUnit.title,
            description: changedUnit.description,
            optional: changedUnit.optional,
            connectedLectureUnits: changedUnit.lectureUnits,
        });

        expect(updatedSpy).toHaveBeenCalledOnce();
        expect(navigateSpy).toHaveBeenCalledOnce();
    });
});
