import { LearningPathNavOverviewComponent } from 'app/course/learning-paths/components/learning-path-nav-overview/learning-path-nav-overview.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { LearningPathNavOverviewLearningObjectsComponent } from 'app/course/learning-paths/components/learning-path-nav-overview-learning-objects/learning-path-nav-overview-learning-objects.component';
import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { By } from '@angular/platform-browser';
import { LearningPathCompetencyDTO } from 'app/entities/competency/learning-path.model';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';

describe('LearningPathNavOverviewComponent', () => {
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
        jest.clearAllMocks();
    });

    it('should initialize', () => {
        fixture.detectChanges();

        expect(component).toBeDefined();
        expect(component.learningPathId()).toBe(learningPathId);
    });

    it('should load competencies', async () => {
        const getLearningPathCompetenciesSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockResolvedValue(competencies);
        await component.loadCompetencies(learningPathId);

        expect(getLearningPathCompetenciesSpy).toHaveBeenCalledWith(learningPathId);
        expect(component.competencies()).toEqual(competencies);
    });

    it('should correctly set isLoading to true and false', async () => {
        jest.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockResolvedValue(competencies);
        const isLoadingSpy = jest.spyOn(component.isLoading, 'set');

        await component.loadCompetencies(learningPathId);

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error when loading competencies fails', async () => {
        const error = 'Error loading competencies';
        const getLearningPathCompetenciesSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencies').mockRejectedValue(error);
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        await component.loadCompetencies(learningPathId);

        expect(getLearningPathCompetenciesSpy).toHaveBeenCalledWith(learningPathId);
        expect(alertServiceErrorSpy).toHaveBeenCalledWith(error);
    });

    it('should open competency graph modal', async () => {
        fixture.detectChanges();

        const openCompetencyGraphSpy = jest.spyOn(component, 'openCompetencyGraph');
        const openCompetencyGraphButton = fixture.debugElement.query(By.css('#open-competency-graph-button'));
        openCompetencyGraphButton.nativeElement.click();

        fixture.detectChanges();

        expect(openCompetencyGraphSpy).toHaveBeenCalled();
    });
});
