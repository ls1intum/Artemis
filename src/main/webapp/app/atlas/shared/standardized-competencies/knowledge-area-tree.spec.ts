import { vi } from 'vitest';
import { ArtemisTranslatePipe } from 'app/foundation/pipes/artemis-translate.pipe';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { MockPipe } from 'ng-mocks';
import { KnowledgeAreaTreeComponent, KnowledgeAreaTreeNode, convertToTreeNodes } from 'app/atlas/shared/standardized-competencies/knowledge-area-tree.component';
import { KnowledgeAreaDTO, KnowledgeAreaForTree, convertToKnowledgeAreaForTree } from 'app/atlas/shared/entities/standardized-competency.model';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';

describe('KnowledgeAreaTreeComponent', () => {
    let componentFixture: ComponentFixture<KnowledgeAreaTreeComponent>;
    let component: KnowledgeAreaTreeComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [KnowledgeAreaTreeComponent, MockPipe(ArtemisTranslatePipe)],
            declarations: [],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        componentFixture = TestBed.createComponent(KnowledgeAreaTreeComponent);
        component = componentFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });
});

/**
 * A host wrapper that projects the two named templates (`#knowledgeAreaTemplate` / `#competencyTemplate`) into the tree
 * component, mirroring how the management view uses it. PrimeNG `<p-tree>` resolves the inner per-type templates by the
 * node `type`, then the inner templates project these host templates via `ngTemplateOutlet`.
 */
@Component({
    selector: 'jhi-knowledge-area-tree-host',
    template: `
        <jhi-knowledge-area-tree [nodes]="nodes()">
            <ng-template let-knowledgeArea="knowledgeArea" #knowledgeAreaTemplate>
                <span class="projected-knowledge-area">{{ knowledgeArea.title }}</span>
            </ng-template>
            <ng-template let-competency="competency" #competencyTemplate>
                <span class="projected-competency">{{ competency.title }}</span>
            </ng-template>
        </jhi-knowledge-area-tree>
    `,
    imports: [KnowledgeAreaTreeComponent],
})
class KnowledgeAreaTreeHostComponent {
    readonly nodes = signal<KnowledgeAreaTreeNode[]>([]);
}

describe('KnowledgeAreaTreeComponent (rendering via host)', () => {
    let hostFixture: ComponentFixture<KnowledgeAreaTreeHostComponent>;
    let host: KnowledgeAreaTreeHostComponent;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [KnowledgeAreaTreeHostComponent, MockPipe(ArtemisTranslatePipe)],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        }).compileComponents();

        hostFixture = TestBed.createComponent(KnowledgeAreaTreeHostComponent);
        host = hostFixture.componentInstance;
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    /** Builds the tree nodes from a DTO, marking all knowledge areas expanded so competency leaves render. */
    function buildExpandedNodes(dto: KnowledgeAreaDTO[]): { nodes: KnowledgeAreaTreeNode[]; knowledgeAreas: KnowledgeAreaForTree[] } {
        const knowledgeAreas = dto.map((knowledgeArea) => convertToKnowledgeAreaForTree(knowledgeArea));
        const nodes = convertToTreeNodes(knowledgeAreas, () => true);
        return { nodes, knowledgeAreas };
    }

    it('should render the knowledge-area title and its projected template', () => {
        const { nodes } = buildExpandedNodes([{ id: 1, title: 'My Knowledge Area' }]);
        host.nodes.set(nodes);
        hostFixture.detectChanges();

        const titleHeading = hostFixture.debugElement.query(By.css('.knowledge-area-node h5'));
        expect(titleHeading).not.toBeNull();
        expect(titleHeading.nativeElement.textContent).toContain('My Knowledge Area');

        const projectedKnowledgeArea = hostFixture.debugElement.query(By.css('.projected-knowledge-area'));
        expect(projectedKnowledgeArea).not.toBeNull();
        expect(projectedKnowledgeArea.nativeElement.textContent).toContain('My Knowledge Area');
    });

    it('should render the projected competency template for a competency leaf', () => {
        const { nodes } = buildExpandedNodes([
            {
                id: 1,
                title: 'root',
                competencies: [{ id: 100, title: 'My Competency' }],
            },
        ]);
        host.nodes.set(nodes);
        hostFixture.detectChanges();

        const competencyNode = hostFixture.debugElement.query(By.css('.competency-node'));
        expect(competencyNode).not.toBeNull();
        const projectedCompetency = hostFixture.debugElement.query(By.css('.projected-competency'));
        expect(projectedCompetency).not.toBeNull();
        expect(projectedCompetency.nativeElement.textContent).toContain('My Competency');
    });

    it('should render the empty label for a knowledge area with no children and no competencies', () => {
        const { nodes } = buildExpandedNodes([{ id: 1, title: 'empty knowledge area' }]);
        host.nodes.set(nodes);
        hostFixture.detectChanges();

        const emptyLabel = findEmptyLabel(hostFixture);
        expect(emptyLabel).not.toBeNull();
    });

    it('should NOT render the empty label when competencies are only hidden by filtering', () => {
        // raw domain arrays are non-empty, but the single competency is filtered out (isVisible=false),
        // so the produced node has no competency children. The empty label must still NOT show.
        const { knowledgeAreas } = buildExpandedNodes([
            {
                id: 1,
                title: 'root',
                competencies: [{ id: 100, title: 'hidden competency' }],
            },
        ]);
        knowledgeAreas[0].competencies![0].isVisible = false;
        const filteredNodes = convertToTreeNodes(knowledgeAreas, () => true);
        // sanity: the filtered competency is gone from the rendered children, but the raw array is still there
        expect(filteredNodes[0].children).toHaveLength(0);
        expect((filteredNodes[0].data as KnowledgeAreaForTree).competencies).toHaveLength(1);

        host.nodes.set(filteredNodes);
        hostFixture.detectChanges();

        const emptyLabel = findEmptyLabel(hostFixture);
        expect(emptyLabel).toBeNull();
    });

    /** Finds the empty-label element (rendered via `jhiTranslate="artemisApp.knowledgeArea.empty"`), or null. */
    function findEmptyLabel(fixture: ComponentFixture<KnowledgeAreaTreeHostComponent>) {
        return (
            fixture.debugElement.queryAll(By.css('.knowledge-area-node h6')).find((debugEl) => debugEl.nativeElement.textContent.includes('artemisApp.knowledgeArea.empty')) ?? null
        );
    }
});
