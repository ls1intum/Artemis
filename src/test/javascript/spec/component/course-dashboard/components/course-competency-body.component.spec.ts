import { CourseCompetencyBodyComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/course-competency-body/course-competency-body.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathApiService } from '../../../../../../main/webapp/app/course/learning-paths/services/learning-path-api.service';
import { AlertService } from '../../../../../../main/webapp/app/core/util/alert.service';
import { CourseCompetencyApiService } from '../../../../../../main/webapp/app/course/competencies/services/course-competency-api.service';
import { LearningObjectType, LearningPathCompetencyDTO, LearningPathNavigationObjectDTO } from '../../../../../../main/webapp/app/entities/competency/learning-path.model';
import { CompetencyJolResponseType, CompetencyTaxonomy } from '../../../../../../main/webapp/app/entities/competency.model';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { HttpErrorResponse } from '@angular/common/http';

describe('CourseCompetencyBodyComponent', () => {
    let component: CourseCompetencyBodyComponent;
    let fixture: ComponentFixture<CourseCompetencyBodyComponent>;

    let learningPathApiService: LearningPathApiService;
    let courseCompetencyApiService: CourseCompetencyApiService;
    let alertService: AlertService;

    let getLearningObjectsSpy: jest.SpyInstance;
    let getJoLSpy: jest.SpyInstance;

    const courseId = 1;
    const learningPathId = 1;
    const courseCompetencyId = 1;
    const courseCompetency: LearningPathCompetencyDTO = {
        id: 1,
        title: 'Competency 1',
        masteryProgress: 50,
        optional: false,
        numberOfLearningObjects: 4,
        numberOfCompletedLearningObjects: 2,
        taxonomy: CompetencyTaxonomy.APPLY,
        userProgress: { progress: 1, confidence: 1 },
    };
    const courseCompetencies: LearningPathCompetencyDTO[] = [
        courseCompetency,
        {
            id: 2,
            title: 'Competency 2',
            masteryProgress: 0,
            optional: false,
            numberOfLearningObjects: 4,
            numberOfCompletedLearningObjects: 0,
            taxonomy: CompetencyTaxonomy.APPLY,
        },
    ];

    const learningObjects: LearningPathNavigationObjectDTO[] = [
        {
            id: 1,
            name: 'Learning Object 1',
            type: LearningObjectType.EXERCISE,
            completed: true,
            competencyId: 1,
            unreleased: false,
        },
        {
            id: 2,
            name: 'Learning Object 2',
            type: LearningObjectType.LECTURE,
            competencyId: 1,
            completed: false,
            unreleased: false,
        },
    ];

    const jol: CompetencyJolResponseType = {
        current: {
            competencyId: 1,
            jolValue: 1,
            competencyConfidence: 1,
            competencyProgress: 1,
            judgementTime: '2021-01-01',
        },
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyBodyComponent],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
                {
                    provide: LearningPathApiService,
                    useValue: {
                        getLearningPathCompetencyLearningObjects: () => jest.fn(),
                    },
                },
                {
                    provide: CourseCompetencyApiService,
                    useValue: {
                        getJoL: () => jest.fn(),
                    },
                },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        courseCompetencyApiService = TestBed.inject(CourseCompetencyApiService);
        alertService = TestBed.inject(AlertService);

        getLearningObjectsSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockResolvedValue(learningObjects);
        getJoLSpy = jest.spyOn(courseCompetencyApiService, 'getJoL').mockResolvedValue(jol);

        fixture = TestBed.createComponent(CourseCompetencyBodyComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('courseId', courseId);
        fixture.componentRef.setInput('courseCompetencyId', courseCompetencyId);
        fixture.componentRef.setInput('courseCompetency', courseCompetency);
        fixture.componentRef.setInput('courseCompetencies', courseCompetencies);
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should load learning objects', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getLearningObjectsSpy).toHaveBeenCalledExactlyOnceWith(learningPathId, courseCompetencyId);
        expect(component.learningObjects()).toEqual(learningObjects);
    });

    it('should load judgement of learning', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getJoLSpy).toHaveBeenCalledExactlyOnceWith(courseId, courseCompetencyId);
        expect(component.jolRating()).toEqual(jol);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        // This is a workaround as fixture.whenStable() does not wait for the async effect to finish
        await new Promise((r) => setTimeout(r, 1));

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when loading learning objects fails', async () => {
        const error = new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' });
        getLearningObjectsSpy.mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        await component['loadData'](courseId, learningPathId, courseCompetencyId);

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error when loading judgement of learning fails', async () => {
        const error = new HttpErrorResponse({ status: 500, statusText: 'Internal Server Error' });
        getJoLSpy.mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        await component['loadData'](courseId, learningPathId, courseCompetencyId);

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });
});
