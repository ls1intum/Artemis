import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { LearningPathApiService } from 'app/course/learning-paths/services/learning-path-api.service';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideAnimations } from '@angular/platform-browser/animations';
import { provideHttpClient } from '@angular/common/http';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyGraphEdgeDto, CompetencyGraphNodeDto } from 'app/entities/competency/learning-path.model';
import { CompetencyNodeComponent } from 'app/course/learning-paths/components/competency-node/competency-node.component';
import { AlertService } from 'app/core/util/alert.service';
import { MockAlertService } from '../../../helpers/mocks/service/mock-alert.service';

describe('CompetencyGraphComponent', () => {
    let component: CompetencyGraphComponent;
    let fixture: ComponentFixture<CompetencyGraphComponent>;
    let learningPathApiService: LearningPathApiService;
    let alertService: AlertService;
    let getLearningPathCompetencyGraphSpy: jest.SpyInstance;

    const learningPathId = 1;
    const competencyGraph = {
        nodes: [
            {
                id: '1',
                label: 'Node 1',
            } as CompetencyGraphNodeDto,
            {
                id: '2',
                label: 'Node 2',
            } as CompetencyGraphNodeDto,
        ],
        edges: [
            {
                source: '1',
                target: '2',
            } as CompetencyGraphEdgeDto,
        ],
    };

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [CompetencyGraphComponent],
            providers: [
                provideAnimations(),
                provideHttpClient(),
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
                {
                    provide: AlertService,
                    useClass: MockAlertService,
                },
            ],
        })
            .overrideComponent(CompetencyGraphComponent, {
                remove: {
                    imports: [CompetencyNodeComponent],
                },
            })
            .compileComponents();

        learningPathApiService = TestBed.inject(LearningPathApiService);
        alertService = TestBed.inject(AlertService);
        getLearningPathCompetencyGraphSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencyGraph').mockResolvedValue(competencyGraph);

        fixture = TestBed.createComponent(CompetencyGraphComponent);
        component = fixture.componentInstance;
        fixture.componentRef.setInput('learningPathId', learningPathId);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', async () => {
        const loadCompetencyGraphSpy = jest.spyOn(component, 'loadCompetencyGraph');
        fixture.detectChanges();
        expect(component).toBeTruthy();
        expect(loadCompetencyGraphSpy).toHaveBeenCalledWith(learningPathId);
    });

    it('should call loadCompetencyGraph', async () => {
        await component.loadCompetencyGraph(learningPathId);
        expect(getLearningPathCompetencyGraphSpy).toHaveBeenCalledWith(learningPathId);
    });

    it('should center graph', () => {
        const centerSpy = jest.spyOn(component.zoomToFit$, 'next');
        fixture.detectChanges();
        expect(centerSpy).toHaveBeenCalled();
    });

    it('should show alert on error', async () => {
        const getLearningPathCompetencyGraphErrorSpy = jest.spyOn(learningPathApiService, 'getLearningPathCompetencyGraph').mockRejectedValue(new Error('Error'));
        const alertServiceErrorSpy = jest.spyOn(alertService, 'error');

        await component.loadCompetencyGraph(learningPathId);
        expect(getLearningPathCompetencyGraphErrorSpy).toHaveBeenCalled();
        expect(alertServiceErrorSpy).toHaveBeenCalled();
    });
});
