import { vi } from 'vitest';
import { LearningPathNavOverviewComponent } from 'app/atlas/overview/learning-path-nav-overview/learning-path-nav-overview.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/atlas/overview/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';
import { CompetencyGraphComponent } from 'app/atlas/manage/competency-graph/competency-graph.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { LearningPathCompetencyDTO } from 'app/atlas/shared/entities/learning-path.model';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockProvider } from 'ng-mocks';
import { ScienceService } from 'app/shared/science/science.service';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathNavOverviewComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LearningPathNavOverviewComponent;
    let fixture: ComponentFixture<LearningPathNavOverviewComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;

    const learningPathId = 1;
    const competencies = [{ id: 1, title: 'Competency 1', masteryProgress: 10 }] as LearningPathCompetencyDTO[];

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathNavOverviewComponent],
            providers: [
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: AlertService, useClass: MockAlertService },
                MockProvider(ScienceService),
            ],
        })
            .overrideComponent(LearningPathNavOverviewComponent, {
                add: {
                    imports: [LearningPathNavOverviewLearningObjectsComponent, CompetencyGraphComponent],
                },
            })
            .compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        fixture = TestBed.createComponent(LearningPathNavOverviewComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        vi.clearAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeDefined();
        expect(component.learningPathId()).toBe(learningPathId);
    });

    it('should load competencies', async () => {
        const loadLearningPathCompetenciesSpy = vi.spyOn(component, 'loadCompetencies');
        const getLearningPathCompetenciesSpy = vi.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockResolvedValue(competencies);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(loadLearningPathCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(learningPathId);
        expect(getLearningPathCompetenciesSpy).toHaveBeenCalledExactlyOnceWith(learningPathId);

        fixture.detectChanges();
        await fixture.whenStable();

        expect(component.competencies()).toEqual(competencies);
    });

    it('should correctly set isLoading to true and false', async () => {
        vi.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockResolvedValue(competencies);
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        await component.loadCompetencies(learningPathId);

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when loading competencies fails', async () => {
        const error = 'Error loading competencies';
        const getLearningPathCompetenciesSpy = vi.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockRejectedValue(error);
        const alertServiceErrorSpy = vi.spyOn(alertService, 'error');

        await component.loadCompetencies(learningPathId);

        expect(getLearningPathCompetenciesSpy).toHaveBeenCalledWith(learningPathId);
        expect(alertServiceErrorSpy).toHaveBeenCalledWith(error);
    });

    it('should open competency graph modal', async () => {
        fixture.detectChanges();

        const openCompetencyGraphSpy = vi.spyOn(component, 'openCompetencyGraph');
        const openCompetencyGraphButton = fixture.debugElement.query(By.css('#open-competency-graph-button'));
        openCompetencyGraphButton.nativeElement.click();

        fixture.detectChanges();

        expect(openCompetencyGraphSpy).toHaveBeenCalled();
    });
});
