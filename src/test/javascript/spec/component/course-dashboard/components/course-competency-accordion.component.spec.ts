import { CourseCompetencyAccordionComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/course-competency-accordion/course-competency-accordion.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { AlertService } from '../../../../../../main/webapp/app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';
import { LearningPathApiService } from '../../../../../../main/webapp/app/course/learning-paths/services/learning-path-api.service';
import { CompetencyTaxonomy } from '../../../../../../main/webapp/app/entities/competency.model';
import { LearningPathCompetencyDTO, LearningPathDTO } from '../../../../../../main/webapp/app/entities/competency/learning-path.model';
import { CourseCompetencyBodyComponent } from '../../../../../../main/webapp/app/overview/course-dashboard/components/course-competency-body/course-competency-body.component';
import { Component, input } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';

describe('CourseCompetencyAccordionComponent', () => {
    let component: CourseCompetencyAccordionComponent;
    let fixture: ComponentFixture<CourseCompetencyAccordionComponent>;

    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

    let getLearningPathSpy: jest.SpyInstance;
    let getCourseCompetenciesSpy: jest.SpyInstance;

    const courseId = 1;
    const learningPathId = 2;
    const learningPath: LearningPathDTO = {
        id: learningPathId,
        progress: 40,
        startedByStudent: true,
    };
    const courseCompetencies: LearningPathCompetencyDTO[] = [
        {
            id: 1,
            title: 'Competency 1',
            masteryProgress: 50,
            optional: false,
            numberOfLearningObjects: 4,
            numberOfCompletedLearningObjects: 2,
            taxonomy: CompetencyTaxonomy.APPLY,
        },
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

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CourseCompetencyAccordionComponent],
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
                        getLearningPathCompetencies: () => jest.fn(),
                        getLearningPathForCurrentUser: () => jest.fn(),
                        generateLearningPathForCurrentUser: () => jest.fn(),
                    },
                },
            ],
        })
            .overrideComponent(CourseCompetencyAccordionComponent, {
                remove: {
                    imports: [CourseCompetencyBodyComponent],
                },
                add: {
                    imports: [CourseCompetencyBodyStubComponent],
                },
            })
            .compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        getLearningPathSpy = jest.spyOn(learningPathApiService, 'getLearningPathForCurrentUser').mockResolvedValue(learningPath);
        getCourseCompetenciesSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockResolvedValue(courseCompetencies);

        fixture = TestBed.createComponent(CourseCompetencyAccordionComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
    });

    afterEach(() => {
        jest.clearAllMocks();
    });

    it('should load course competencies and learning path id', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        // This is a workaround as fixture.whenStable() does not wait for the async effect to finish
        await new Promise((r) => setTimeout(r, 1));

        expect(getLearningPathSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.learningPathId()).toEqual(learningPathId);
        expect(getCourseCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(learningPathId);
        expect(component.courseCompetencies()).toEqual(courseCompetencies);
        expect(component.learningPathId()).toEqual(learningPathId);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        await component['loadCourseCompetencies'](courseId);

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error message if loading course competencies fails', async () => {
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        getCourseCompetenciesSpy.mockRejectedValueOnce(new HttpErrorResponse({ status: 500 }));

        await component['loadCourseCompetencies'](courseId);

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });

    it('should show error message if loading learning path fails', async () => {
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');
        getLearningPathSpy.mockRejectedValueOnce(new HttpErrorResponse({ status: 500 }));

        await component['loadCourseCompetencies'](courseId);

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });
});

@Component({
    selector: 'jhi-course-competency-body',
    template: '',
    standalone: true,
})
class CourseCompetencyBodyStubComponent {
    readonly courseId = input.required<number>();
    readonly learningPathId = input.required<number>();
    readonly courseCompetencyId = input.required<number>();
    readonly courseCompetency = input.required<LearningPathCompetencyDTO>();
    readonly courseCompetencies = input.required<LearningPathCompetencyDTO[]>();
}
