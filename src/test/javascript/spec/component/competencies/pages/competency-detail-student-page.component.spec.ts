import { CourseCompetencyDetailStudentPageComponent } from 'app/course/competencies/pages/course-competency-detail-student-page/course-competency-detail-student-page.component';
import { AlertService } from 'app/core/util/alert.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { ActivatedRoute } from '@angular/router';
import { of } from 'rxjs';
import { TranslateService } from '@ngx-translate/core';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { CompetencyJoLResponse, CompetencyJol, CompetencyProgress, CourseCompetency, CourseCompetencyType } from 'app/entities/competency.model';
import { LectureUnitType } from 'app/entities/lecture-unit/lectureUnit.model';
import { CourseStorageService } from 'app/course/manage/course-storage.service';
import { Course } from 'app/entities/course.model';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { ScienceService } from 'app/shared/science/science.service';
import { MockScienceService } from '../../../helpers/mocks/service/mock-science-service';
import { CourseCompetencyApiService } from 'app/course/competencies/services/course-competency-api.service';

describe('CompetencyDetailStudentPageComponent', () => {
    let component: CourseCompetencyDetailStudentPageComponent;
    let fixture: ComponentFixture<CourseCompetencyDetailStudentPageComponent>;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;
    let courseStorageService: CourseStorageService;
    let featureToggleService: FeatureToggleService;

    let getCourseCompetencySpy: jest.SpyInstance;
    let getCourseCompetenciesSpy: jest.SpyInstance;
    let getJoLSpy: jest.SpyInstance;

    const courseId = 1;
    const competencyId = 2;

    const courseCompetencies = <CourseCompetency[]>[
        {
            type: CourseCompetencyType.COMPETENCY,
            id: 2,
            title: 'Competency 2',
            description: '## Descriptiuon\nHier steht viel über eine Kompetenz das kann auch viel Text sein',
            taxonomy: 'UNDERSTAND',
            masteryThreshold: 100,
            optional: false,
            lectureUnits: [
                {
                    id: 7,
                    name: 'Text Exercise 1',
                    lecture: {
                        id: 2,
                        title: 'Lecture 1',
                        visibleToStudents: true,
                    },
                    content: 'Text Exercise',
                    type: LectureUnitType.TEXT,
                    completed: true,
                    visibleToStudents: true,
                },
                {
                    id: 9,
                    name: 'Text Lecture',
                    type: LectureUnitType.TEXT,
                    completed: false,
                    visibleToStudents: true,
                },
            ],
            userProgress: [
                {
                    progress: 50.0,
                    confidence: 1.0,
                    confidenceReason: 'NO_REASON',
                },
            ],
        },
        {
            type: CourseCompetencyType.PREREQUISITE,
            id: 3,
            title: 'Prerequisite 1',
            description: '## Descriptiuon\nHier steht viel über eine Kompetenz das kann auch viel Text sein',
            taxonomy: 'UNDERSTAND',
            masteryThreshold: 100,
            optional: false,
            lectureUnits: [
                {
                    id: 7,
                    name: 'Text Exercise 1',
                    lecture: {
                        id: 2,
                        title: 'Lecture 1',
                        visibleToStudents: true,
                    },
                    content: 'Text Exercise',
                    type: LectureUnitType.TEXT,
                    completed: true,
                    visibleToStudents: true,
                },
                {
                    id: 9,
                    name: 'Text Lecture',
                    type: LectureUnitType.TEXT,
                    completed: false,
                    visibleToStudents: true,
                },
            ],
            userProgress: [
                {
                    progress: 50.0,
                    confidence: 1.0,
                    confidenceReason: 'NO_REASON',
                },
            ],
        },
    ];
    const courseCompetency = courseCompetencies[0];

    const course = <Course>{
        id: courseId,
        title: 'Course 1',
        studentCourseAnalyticsDashboardEnabled: true,
    };

    const competencyJoLResponse = <CompetencyJoLResponse>{
        current: {
            competencyId: 1,
            jolValue: 2,
            judgementTime: '2021-08-01T00:00:00Z',
            competencyProgress: 50.0,
            competencyConfidence: 1.0,
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyDetailStudentPageComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: ActivatedRoute,
                    useValue: {
                        parent: {
                            parent: {
                                params: of({
                                    courseId: courseId,
                                }),
                            },
                        },
                        params: of({
                            competencyId: competencyId,
                        }),
                    },
                },
                { provide: AlertService, useClass: MockAlertService },
                { provide: TranslateService, useClass: MockTranslateService },
                {
                    provide: CourseStorageService,
                    useValue: {
                        getCourse: jest.fn(),
                    },
                },
                {
                    provide: FeatureToggleService,
                    useValue: {
                        getFeatureToggleActive: jest.fn(),
                    },
                },
                { provide: ScienceService, useClass: MockScienceService },
            ],
        }).compileComponents();

        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);
        courseStorageService = TestBed.inject(CourseStorageService);
        featureToggleService = TestBed.inject(FeatureToggleService);

        getCourseCompetencySpy = jest.spyOn(courseCompetencyApiService, 'getCourseCompetencyById').mockResolvedValue(courseCompetency);
        getCourseCompetenciesSpy = jest.spyOn(courseCompetencyApiService, 'getCourseCompetenciesByCourseId').mockResolvedValue(courseCompetencies);
        jest.spyOn(courseStorageService, 'getCourse').mockReturnValueOnce(course);
        jest.spyOn(featureToggleService, 'getFeatureToggleActive').mockReturnValue(of(true));
        getJoLSpy = jest.spyOn(courseCompetencyApiService, 'getJoL').mockResolvedValue(competencyJoLResponse);

        fixture = TestBed.createComponent(CourseCompetencyDetailStudentPageComponent);
        component = fixture.componentInstance;
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        expect(component).toBeTruthy();
        expect(component.courseId()).toBe(courseId);
        expect(component.course()).toBe(course);
    });

    it('should load course competency', async () => {
        await component.loadData(courseId, competencyId);
        fixture.detectChanges();

        expect(getCourseCompetencySpy).toHaveBeenCalledWith(courseId, competencyId);
        expect(component.courseCompetency()).toBe(courseCompetency);
    });

    it('should set computed values correctly', async () => {
        await component.loadData(courseId, competencyId);
        fixture.detectChanges();

        expect(component.userProgress()).toEqual(courseCompetency.userProgress?.first());
        expect(component.progress()).toBe(50);
        expect(component.mastery()).toBe(50);
        expect(component.isMastered()).toBeFalse();
    });

    it('should show error on failed load', async () => {
        getCourseCompetencySpy.mockRejectedValue(new Error());
        const errorSpy = jest.spyOn(alertService, 'addAlert');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(errorSpy).toHaveBeenCalledOnce();
    });

    it('should set judgementOfLearningEnabled correctly', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.judgementOfLearningEnabled()).toBeTrue();
    });

    it('should load prompt for judgement of learning rating', async () => {
        await component.loadData(courseId, competencyId);

        expect(getCourseCompetenciesSpy).toHaveBeenCalledWith(courseId);
        expect(getJoLSpy).toHaveBeenCalledWith(courseId, competencyId);
        expect(component.judgementOfLearning()).toEqual(competencyJoLResponse.current);
        expect(component.promptForJolRating()).toEqual(CompetencyJol.shouldPromptForJol(courseCompetency, courseCompetency.userProgress!.first(), courseCompetencies));
    });

    it('should set isLoading correctly', async () => {
        const loadingSpy = jest.spyOn(component.isLoading, 'set');

        await component.loadData(courseId, competencyId);
        fixture.detectChanges();

        expect(loadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(loadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should set lecture unit completion', async () => {
        const getCourseCompetencyProgressSpy = jest
            .spyOn(courseCompetencyApiService, 'getCourseCompetencyProgressById')
            .mockResolvedValue(<CompetencyProgress>{ progress: 50, confidence: 100 });
        const updateSpy = jest.spyOn(component.courseCompetency, 'update');

        await component.onLectureUnitCompletion();

        expect(getCourseCompetencyProgressSpy).toHaveBeenCalledWith(courseId, competencyId, true);
        expect(updateSpy).toHaveBeenCalledOnce();
    });
});
