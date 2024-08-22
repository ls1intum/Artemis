import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO } from 'app/entities/competency/learning-path.model';

describe('CompetencyGraphComponent', () => {
    let component: CompetencyGraphComponent;
    let fixture: ComponentFixture<CompetencyGraphComponent>;

    const competencyGraph = <CompetencyGraphDTO>{
        nodes: [
            {
                id: '1',
                label: 'Node 1',
            } as CompetencyGraphNodeDTO,
            {
                id: '2',
                label: 'Node 2',
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
            imports: [CompetencyGraphComponent, NoopAnimationsModule],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(CompetencyGraphComponent);
        component = fixture.componentInstance;

        fixture.componentRef.setInput('competencyGraph', competencyGraph);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', async () => {
        expect(component).toBeTruthy();
        expect(component.competencyGraph()).toEqual(competencyGraph);
    });

    it('should have nodes and edges', async () => {
        const edgesWithUniqueIds = competencyGraph.edges!.map((edge) => ({ ...edge, id: `edge-${edge.id}` }));

        fixture.detectChanges();

        expect(component.nodes()).toEqual(competencyGraph.nodes);
        expect(component.edges()).toEqual(edgesWithUniqueIds);
    });
});
