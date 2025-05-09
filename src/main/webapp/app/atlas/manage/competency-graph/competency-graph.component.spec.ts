import { CompetencyGraphComponent } from 'app/atlas/manage/competency-graph/competency-graph.component';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule } from '@angular/platform-browser/animations';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO } from 'app/atlas/shared/entities/learning-path.model';
import { SizeUpdate } from 'app/atlas/manage/competency-node/competency-node.component';

import { Component } from '@angular/core';
import { getComponentInstanceFromFixture } from 'test/helpers/utils/general-test.utils';

@Component({
    template: '<jhi-competency-graph [competencyGraph]="competencyGraph"/>',
    imports: [CompetencyGraphComponent],
})
class WrapperComponent {
    competencyGraph: CompetencyGraphDTO;
}

describe('CompetencyGraphComponent', () => {
    let component: WrapperComponent;
    let fixture: ComponentFixture<WrapperComponent>;
    let graphComponent: CompetencyGraphComponent;

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
            imports: [WrapperComponent, NoopAnimationsModule],
            providers: [
                {
                    provide: TranslateService,
                    useClass: MockTranslateService,
                },
            ],
        }).compileComponents();

        fixture = TestBed.createComponent(WrapperComponent);
        component = fixture.componentInstance;
        graphComponent = getComponentInstanceFromFixture(fixture, 'jhi-competency-graph') as CompetencyGraphComponent;

        component.competencyGraph = competencyGraph;

        fixture.detectChanges();
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', async () => {
        expect(component).toBeTruthy();
        expect(graphComponent.competencyGraph()).toEqual(competencyGraph);
    });

    it('should have nodes and edges', async () => {
        const edgesWithUniqueIds = competencyGraph.edges!.map((edge) => ({ ...edge, id: `edge-${edge.id}` }));

        fixture.detectChanges();

        expect(graphComponent.nodes()).toEqual(competencyGraph.nodes);
        expect(graphComponent.edges()).toEqual(edgesWithUniqueIds);
    });

    it('should handle empty nodes array gracefully', () => {
        graphComponent['internalCompetencyGraph'].set({ nodes: [], edges: [] });
        const sizeUpdate: SizeUpdate = { id: '1', dimension: { width: 100, height: 100 } };
        graphComponent.setNodeDimension(sizeUpdate);

        expect(graphComponent.nodes()).toHaveLength(0);
    });

    it('should not update dimension if node id does not exist', () => {
        const sizeUpdate: SizeUpdate = { id: '3', dimension: { width: 100, height: 100 } };
        const originalNodes = [...graphComponent.nodes()];
        graphComponent.setNodeDimension(sizeUpdate);

        expect(graphComponent.nodes()).toEqual(originalNodes);
    });
});
