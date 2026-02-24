import { vi } from 'vitest';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/atlas/overview/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';
import { LearningObjectType, LearningPathNavigationObjectDTO } from 'app/atlas/shared/entities/learning-path.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathNavOverviewLearningObjectsComponent', () => {
    setupTestBed({ zoneless: true });
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
                { provide: AlertService, useClass: MockAlertService },
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
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeDefined();
        expect(component.learningPathId()).toBe(learningPathId);
        expect(component.competencyId()).toBe(competencyId);
    });

    it('should load learning objects', async () => {
        const getLearningPathCompetencyLearningObjectsSpy = vi.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockResolvedValue(learningObjects);

        fixture.detectChanges();
        await fixture.whenStable();
        fixture.detectChanges();

        expect(getLearningPathCompetencyLearningObjectsSpy).toHaveBeenCalledWith(learningPathId, competencyId);
        expect(component.learningObjects()).toEqual(learningObjects);
    });

    it('should show error message when loading learning objects fails', async () => {
        const error = 'Failed to load learning objects';
        vi.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockRejectedValue(error);
        const alertServiceErrorSpy = vi.spyOn(alertService, 'error');

        await component.loadLearningObjects();

        expect(alertServiceErrorSpy).toHaveBeenCalledWith(error);
    });

    it('should set isLoading correctly', async () => {
        vi.spyOn(learningPathApiService, 'getLearningPathCompetencyLearningObjects').mockResolvedValue(learningObjects);
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        await component.loadLearningObjects();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });
});
