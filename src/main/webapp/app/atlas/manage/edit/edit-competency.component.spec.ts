import { ComponentFixture, TestBed } from '@angular/core/testing';
import { AlertService } from 'app/shared/service/alert.service';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateDirective } from 'app/shared/language/translate.directive';
import { of } from 'rxjs';
import { HttpResponse } from '@angular/common/http';
import { By } from '@angular/platform-browser';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { EditCompetencyComponent } from 'app/atlas/manage/edit/edit-competency.component';
import { CompetencyService } from 'app/atlas/manage/services/competency.service';
import { LectureService } from 'app/lecture/manage/services/lecture.service';
import { Competency, CourseCompetencyProgress } from 'app/atlas/shared/entities/competency.model';
import { TextUnit } from 'app/lecture/shared/entities/lecture-unit/textUnit.model';
import { MockRouter } from 'test/helpers/mocks/mock-router';
import { CompetencyFormComponent } from 'app/atlas/manage/forms/competency/competency-form.component';
import { OwlNativeDateTimeModule } from '@danielmoncada/angular-datetime-picker';
import { MockResizeObserver } from 'test/helpers/mocks/service/mock-resize-observer';
import { MockComponent, MockDirective, MockModule, MockProvider } from 'ng-mocks';
import { ProfileService } from '../../../core/layouts/profiles/shared/profile.service';
import { MockProfileService } from 'test/helpers/mocks/service/mock-profile.service';

describe('EditCompetencyComponent', () => {
    let editCompetencyComponentFixture: ComponentFixture<EditCompetencyComponent>;
    let editCompetencyComponent: EditCompetencyComponent;
    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [EditCompetencyComponent, MockModule(OwlNativeDateTimeModule), MockComponent(CompetencyFormComponent), MockDirective(TranslateDirective)],
            providers: [
                MockProvider(LectureService),
                MockProvider(CompetencyService),
                MockProvider(AlertService),
                { provide: Router, useClass: MockRouter },
                { provide: ProfileService, useClass: MockProfileService },
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
        }).compileComponents();

        global.ResizeObserver = jest.fn().mockImplementation((callback: ResizeObserverCallback) => {
            return new MockResizeObserver(callback);
        });

        editCompetencyComponentFixture = TestBed.createComponent(EditCompetencyComponent);
        editCompetencyComponent = editCompetencyComponentFixture.componentInstance;
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

        const competencyOfResponse: Competency = {};
        competencyOfResponse.id = 1;
        competencyOfResponse.title = 'test';
        competencyOfResponse.description = 'lorem ipsum';
        competencyOfResponse.optional = true;

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

        const findAllByCourseSpy = jest.spyOn(lectureService, 'findAllByCourseIdWithUnits').mockReturnValue(of(lecturesResponse));

        editCompetencyComponentFixture.detectChanges();
        const competencyFormComponent = editCompetencyComponentFixture.debugElement.query(By.directive(CompetencyFormComponent)).componentInstance;
        expect(findByIdSpy).toHaveBeenCalledOnce();
        expect(getCourseProgressSpy).toHaveBeenCalledOnce();
        expect(findAllByCourseSpy).toHaveBeenCalledOnce();

        expect(editCompetencyComponent.formData.title).toEqual(competencyOfResponse.title);
        expect(editCompetencyComponent.formData.description).toEqual(competencyOfResponse.description);
        expect(editCompetencyComponent.formData.optional).toEqual(competencyOfResponse.optional);
        expect(editCompetencyComponent.lecturesWithLectureUnits).toEqual([lectureOfResponse]);
        expect(competencyFormComponent.formData).toEqual(editCompetencyComponent.formData);
    });

    it('should send PUT request upon form submission and navigate', () => {
        const router: Router = TestBed.inject(Router);
        const competencyService = TestBed.inject(CompetencyService);
        const lectureService = TestBed.inject(LectureService);

        const textUnit = new TextUnit();
        textUnit.id = 1;

        const competencyDatabase: Competency = {};
        competencyDatabase.id = 1;
        competencyDatabase.title = 'test';
        competencyDatabase.description = 'lorem ipsum';
        competencyDatabase.optional = true;

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

        const competencyForm = editCompetencyComponentFixture.debugElement.query(By.directive(CompetencyFormComponent)).componentInstance;
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
