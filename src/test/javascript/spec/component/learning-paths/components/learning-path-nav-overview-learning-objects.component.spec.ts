import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/course/learning-paths/components/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';
import { LearningObjectType, LearningPathNavigationObjectDTO } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';

describe('LearningPathNavOverviewLearningObjectsComponent', () => {
    let component: LearningPathNavOverviewLearningObjectsComponent;
    let fixture: ComponentFixture<LearningPathNavOverviewLearningObjectsComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

    const learningPathId = 1;
    const competencyId = 2;

    const learningObjects = [{ id: 1, name: 'Exercise 1', type: LearningObjectType.EXERCISE, completed: true }] as LearningPathNavigationObjectDTO[];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathNavOverviewLearningObjectsComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        fixture = TestBed.createComponent(LearningPathNavOverviewLearningObjectsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
        fixture.componentRef.setInput('competencyId', competencyId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeDefined();
        expect(component.learningPathId()).toBe(learningPathId);
        expect(component.competencyId()).toBe(competencyId);
    });

    it('should load learning objects', async () => {
        const getLearningPathCompetencyLearningObjectsSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockResolvedValue(learningObjects);

        await component.loadLearningObjects();

        expect(getLearningPathCompetencyLearningObjectsSpy).toHaveBeenCalledWith(learningPathId, competencyId);
        expect(component.learningObjects()).toEqual(learningObjects);
    });

    it('should show error message when loading learning objects fails', async () => {
        const error = 'Failed to load learning objects';
        jest.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        await component.loadLearningObjects();

        expect(alertServiceErrorSpy).toHaveBeenCalledWith(error);
    });

    it('should set isLoading correctly', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockResolvedValue(learningObjects);
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        await component.loadLearningObjects();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });
});
