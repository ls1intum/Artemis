import { ArtemisTestModule } from '../../../test.module';
import { ArtemisTranslatePipe } from 'app/shared/pipes/artemis-translate.pipe';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MockDirective, MockPipe } from 'ng-mocks';
import { Component } from '@angular/core';
import { Competency, CompetencyRelation, CompetencyRelationError, CompetencyRelationType } from 'app/entities/competency.model';
import { CompetencyRelationGraphComponent } from 'app/course/competencies/competency-management/competency-relation-graph.component';
import { NgbAccordionBody, NgbAccordionButton, NgbAccordionCollapse, NgbAccordionDirective, NgbAccordionHeader, NgbAccordionItem } from '@ng-bootstrap/ng-bootstrap';
import { Edge, Node } from '@swimlane/ngx-graph';

// eslint-disable-next-line @angular-eslint/component-selector
@Component({ selector: 'ngx-graph', template: '' })
class NgxGraphStubComponent {}

describe('CompetencyRelationGraphComponent', () => {
    let componentFixture: ComponentFixture<CompetencyRelationGraphComponent>;
    let component: CompetencyRelationGraphComponent;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [ArtemisTestModule],
            declarations: [
                CompetencyRelationGraphComponent,
                MockPipe(ArtemisTranslatePipe),
                MockDirective(NgbAccordionDirective),
                MockDirective(NgbAccordionItem),
                MockDirective(NgbAccordionHeader),
                MockDirective(NgbAccordionButton),
                MockDirective(NgbAccordionCollapse),
                MockDirective(NgbAccordionBody),
                NgxGraphStubComponent,
            ],
            providers: [],
        })
            .compileComponents()
            .then(() => {
                componentFixture = TestBed.createComponent(CompetencyRelationGraphComponent);
                component = componentFixture.componentInstance;
            });
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should initialize', () => {
        componentFixture.detectChanges();
        expect(component).toBeDefined();
    });

    it('should correctly update nodes and edges from input', () => {
        componentFixture.componentRef.setInput('competencies', [createCompetency(1, 'Competency 1'), createCompetency(2, 'Competency 2')]);
        componentFixture.componentRef.setInput('relations', [createRelation(1, 1, 2, CompetencyRelationType.EXTENDS)]);
        componentFixture.detectChanges();

        const expectedNodes: Node[] = [
            { id: '1', label: 'Competency 1' },
            { id: '2', label: 'Competency 2' },
        ];
        const expectedEdges: Edge[] = [{ id: 'edge1', source: '1', target: '2', label: CompetencyRelationType.EXTENDS, data: { id: 1 } }];

        expect(component.nodes()).toEqual(expectedNodes);
        expect(component.edges()).toEqual(expectedEdges);
    });

    it('should create competency relation', () => {
        componentFixture.componentRef.setInput('competencies', [createCompetency(1, 'Competency 1'), createCompetency(2, 'Competency 2')]);
        component.tailCompetencyId = 1;
        component.headCompetencyId = 2;
        component.relationType = CompetencyRelationType.ASSUMES;
        const relation: CompetencyRelation = {
            tailCompetency: { id: component.tailCompetencyId },
            headCompetency: { id: component.headCompetencyId },
            type: component.relationType,
        };
        const onCreateRelationSpy = jest.spyOn(component.onCreateRelation, 'emit');

        componentFixture.detectChanges();

        component.createRelation();
        expect(onCreateRelationSpy).toHaveBeenCalledWith(relation);
    });

    it('should not competency relation on error', () => {
        component.tailCompetencyId = 1;
        component.headCompetencyId = 1;
        component.relationType = CompetencyRelationType.ASSUMES;
        const onCreateRelationSpy = jest.spyOn(component.onCreateRelation, 'emit');
        const validateSpy = jest.spyOn(component, 'validate');

        component.createRelation();
        expect(onCreateRelationSpy).not.toHaveBeenCalled();
        expect(validateSpy).toHaveBeenCalledOnce();
    });

    it('should remove competency relation', () => {
        const onRemoveRelationSpy = jest.spyOn(component.onRemoveRelation, 'emit');

        component.removeRelation({ source: '123', data: { id: 456 } } as Edge);
        expect(onRemoveRelationSpy).toHaveBeenCalledWith(456);
    });

    it('should detect circles on relations', () => {
        const competencies = [createCompetency(16, '16'), createCompetency(17, '17'), createCompetency(18, '18')];
        const relations = [createRelation(1, 16, 17, CompetencyRelationType.EXTENDS), createRelation(1, 17, 18, CompetencyRelationType.MATCHES)];
        componentFixture.componentRef.setInput('competencies', competencies);
        componentFixture.componentRef.setInput('relations', relations);
        componentFixture.detectChanges();

        component.tailCompetencyId = 18;
        component.headCompetencyId = 16;
        component.relationType = CompetencyRelationType.ASSUMES;

        component.validate();

        expect(component.relationError).toBe(CompetencyRelationError.CIRCULAR);
    });

    it('should not detect circles on arbitrary relations', () => {
        const competencies = [createCompetency(16, '16'), createCompetency(17, '17')];
        const relations: CompetencyRelation[] = [];
        componentFixture.componentRef.setInput('competencies', competencies);
        componentFixture.componentRef.setInput('relations', relations);
        componentFixture.detectChanges();

        component.tailCompetencyId = 17;
        component.headCompetencyId = 16;
        component.relationType = CompetencyRelationType.ASSUMES;

        component.validate();

        expect(component.relationError).toBeUndefined();
    });

    it('should prevent creating already existing relations', () => {
        const competencies = [createCompetency(16, '16'), createCompetency(17, '17')];
        const relations = [createRelation(1, 16, 17, CompetencyRelationType.EXTENDS)];
        componentFixture.componentRef.setInput('competencies', competencies);
        componentFixture.componentRef.setInput('relations', relations);
        componentFixture.detectChanges();

        component.tailCompetencyId = 16;
        component.headCompetencyId = 17;
        component.relationType = CompetencyRelationType.EXTENDS;

        component.validate();

        expect(component.relationError).toBe(CompetencyRelationError.EXISTING);
    });

    it('should prevent creating self relations', () => {
        const competencies = [createCompetency(16, '16'), createCompetency(17, '17')];
        const relations: CompetencyRelation[] = [];
        componentFixture.componentRef.setInput('competencies', competencies);
        componentFixture.componentRef.setInput('relations', relations);
        componentFixture.detectChanges();

        component.tailCompetencyId = 16;
        component.headCompetencyId = 16;
        component.relationType = CompetencyRelationType.EXTENDS;

        component.validate();

        expect(component.relationError).toBe(CompetencyRelationError.SELF);
    });

    it('should zoom to fit and center on centerView', () => {
        const zoomToFitStub = jest.spyOn(component.zoomToFit$, 'next');
        const centerStub = jest.spyOn(component.center$, 'next');
        componentFixture.detectChanges();
        component.centerView();
        expect(zoomToFitStub).toHaveBeenCalledExactlyOnceWith({ autoCenter: true });
        expect(centerStub).toHaveBeenCalledExactlyOnceWith(true);
    });

    function createCompetency(id: number, title: string) {
        const competency: Competency = {
            id: id,
            title: title,
        };
        return competency;
    }

    function createRelation(id: number, tailCompetencyId: number, headCompetencyId: number, relationType: CompetencyRelationType) {
        const relation: CompetencyRelation = {
            id: id,
            tailCompetency: { id: tailCompetencyId },
            headCompetency: { id: headCompetencyId },
            type: relationType,
        };
        return relation;
    }
});
