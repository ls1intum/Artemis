import { vi } from 'vitest';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LearningPathsAnalyticsComponent } from 'app/atlas/manage/learning-paths-analytics/learning-paths-analytics.component';
import { LearningPathApiService } from 'app/atlas/shared/services/learning-path-api.service';
import { AlertService } from 'app/shared/service/alert.service';
import { MockAlertService } from 'test/helpers/mocks/service/mock-alert.service';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { TranslateService } from '@ngx-translate/core';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting } from '@angular/common/http/testing';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO, CompetencyGraphNodeValueType } from 'app/atlas/shared/entities/learning-path.model';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';

describe('LearningPathsAnalyticsComponent', () => {
    setupTestBed({ zoneless: true });
    let component: LearningPathsAnalyticsComponent;
    let fixture: ComponentFixture<LearningPathsAnalyticsComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getInstructorCompetencyGraphSpy: ReturnType<typeof vi.spyOn>;

    const courseId = 1;

    const competencyGraph = <CompetencyGraphDTO>{
        nodes: [
            {
                id: '1',
                label: 'Node 1',
                valueType: CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS,
                value: 12,
            } as CompetencyGraphNodeDTO,
            {
                id: '2',
                label: 'Node 2',
                valueType: CompetencyGraphNodeValueType.AVERAGE_MASTERY_PROGRESS,
                value: 0,
            } as CompetencyGraphNodeDTO,
        ],
        edges: [
            {
                source: '1',
                target: '2',
            } as CompetencyGraphEdgeDTO,
        ],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [LearningPathsAnalyticsComponent],
            providers: [
                provideHttpClient(),
                provideHttpClientTesting(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                { provide: AlertService, useClass: MockAlertService },
            ],
        }).compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);

        getInstructorCompetencyGraphSpy = vi.spyOn(learningPathApiService, 'getLearningPathInstructorCompetencyGraph').mockResolvedValue(competencyGraph);

        fixture = TestBed.createComponent(LearningPathsAnalyticsComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('courseId', courseId);
    });

    it('should load instructor competency graph', async () => {
        fixture.detectChanges();
        await fixture.whenStable();

        expect(getInstructorCompetencyGraphSpy).toHaveBeenCalledExactlyOnceWith(courseId);
        expect(component.instructorCompetencyGraph()).toEqual(competencyGraph);
    });

    it('should set isLoading correctly', async () => {
        const isLoadingSpy = vi.spyOn(component.isLoading, 'set');

        fixture.detectChanges();
        await fixture.whenStable();

        expect(isLoadingSpy).toHaveBeenNthCalledWith(1, true);
        expect(isLoadingSpy).toHaveBeenNthCalledWith(2, false);
    });

    it('should show error on load instructor competency graph', async () => {
        const alertServiceErrorSpy = vi.spyOn(alertService, 'addAlert');
        getInstructorCompetencyGraphSpy.mockRejectedValue(new Error('Error'));

        fixture.detectChanges();
        await fixture.whenStable();

        expect(alertServiceErrorSpy).toHaveBeenCalledOnce();
    });
});
