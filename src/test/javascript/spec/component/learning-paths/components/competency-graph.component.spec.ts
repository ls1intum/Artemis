import { CompetencyGraphComponent } from 'app/course/learning-paths/components/competency-graph/competency-graph.component';
import { MockTranslateService } from '../../../helpers/mocks/service/mock-translate.service';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NoopAnimationsModule, provideAnimations } from '@angular/platform-browser/animations';
import { TranslateService } from '@ngx-translate/core';
import { CompetencyGraphDTO, CompetencyGraphEdgeDTO, CompetencyGraphNodeDTO } from 'app/entities/competency/learning-path.model';
import { By } from '@angular/platform-browser';
import { AnimationDriver } from '@angular/animations/browser';

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
                provideAnimations(),
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

    it('should show nodes and edges', async () => {
        fixture.detectChanges();

        const nodes = fixture.debugElement.queryAll(By.css('.node'));
        const edges = fixture.debugElement.queryAll(By.css('.edge'));
        expect(nodes.length).toHaveLength(2);
        expect(edges.length).toHaveLength(1);
    });
});
