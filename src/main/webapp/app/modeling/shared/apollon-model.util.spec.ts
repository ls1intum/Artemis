import { describe, expect, it } from 'vitest';
import {
    ApollonModelData,
    countModelElements,
    getModelEdges,
    getModelElementIds,
    getModelElementType,
    getModelNodes,
    hasModelElements,
    isModelEmpty,
    normalizeApollonModel,
} from './apollon-model.util';
import testClassDiagramV3 from 'test/helpers/sample/modeling/test-models/class-diagram.json';
import testClassDiagramV4 from 'test/helpers/sample/modeling/test-models/class-diagram-v4.json';
import { UMLDiagramType } from '@tumaet/apollon';
const CURRENT_MODEL_VERSION = normalizeApollonModel({ version: '4.0.0', type: UMLDiagramType.ClassDiagram, nodes: [], edges: [] } as unknown as ApollonModelData).version;

describe('apollon-model.util', () => {
    describe('normalizeApollonModel', () => {
        it('should clone and normalize a current model without mutating the input', () => {
            const input = testClassDiagramV4 as unknown as ApollonModelData;
            const original = structuredClone(input);

            const normalized = normalizeApollonModel(input);

            expect(input).toEqual(original);
            expect(normalized).not.toBe(input);
            expect(normalized.nodes).not.toBe(input.nodes);
            expect(normalized).toMatchObject({
                version: CURRENT_MODEL_VERSION,
                type: testClassDiagramV4.type,
                nodes: expect.arrayContaining([
                    expect.objectContaining({ id: 'class-in-package', data: expect.objectContaining({ attributes: [expect.objectContaining({ id: 'attr-1' })] }) }),
                ]),
                edges: expect.arrayContaining([expect.objectContaining({ id: 'edge-1', source: 'connected-class', target: 'package-1' })]),
            });
            expect(normalized.nodes).toHaveLength(testClassDiagramV4.nodes.length);
            expect(normalized.edges).toHaveLength(testClassDiagramV4.edges.length);
        });

        it('should convert persisted v3 data to the current model format without mutating the input', () => {
            const input = testClassDiagramV3 as unknown as ApollonModelData;
            const original = structuredClone(input);

            const normalized = normalizeApollonModel(input);

            expect(input).toEqual(original);
            expect(normalized.version).toBe(CURRENT_MODEL_VERSION);
            expect(normalized.type).toBe(testClassDiagramV3.type);
            expect(normalized.nodes).toEqual(
                expect.arrayContaining([
                    expect.objectContaining({
                        id: 'ccac14e5-c828-4afb-ab97-0fb2a67e77d6',
                        data: expect.objectContaining({ attributes: [expect.objectContaining({ id: '6f572312-066b-4678-9c03-5032f3ba9be9' })] }),
                    }),
                ]),
            );
            expect(normalized.edges).toEqual(
                expect.arrayContaining([
                    expect.objectContaining({
                        id: '5a9a4eb3-8281-4de4-b0f2-3e2f164574bd',
                        source: '2f67120e-b491-4222-beb1-79e87c2cf54d',
                        target: 'b234e5cb-33e3-4957-ae04-f7990ce8571a',
                    }),
                ]),
            );
            expect(normalized.interactive).toEqual(testClassDiagramV3.interactive);
        });

        it('should distinguish an explicit empty quiz selection from an omitted selection', () => {
            const source = testClassDiagramV3 as unknown as ApollonModelData;
            const explicitEmpty = normalizeApollonModel({
                ...source,
                interactive: { elements: {}, relationships: {} },
            });
            const implicitAll = normalizeApollonModel({ ...source, interactive: undefined });

            expect(explicitEmpty.interactive).toEqual({ elements: {}, relationships: {} });
            expect(implicitAll.interactive).toBeUndefined();
        });
    });

    describe('getModelNodes', () => {
        it('should return empty array for undefined model', () => {
            expect(getModelNodes(undefined)).toEqual([]);
        });

        it('should return empty array for model without nodes or elements', () => {
            const model: ApollonModelData = { version: '4.0.0' };
            expect(getModelNodes(model)).toEqual([]);
        });

        it('should return nodes from v4 format (array)', () => {
            const model = {
                version: '4.0.0',
                nodes: [
                    { id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                    { id: 'node2', type: 'Interface', width: 100, height: 50, position: { x: 100, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                ],
            } as any as ApollonModelData;
            const nodes = getModelNodes(model);
            expect(nodes).toHaveLength(2);
            expect(nodes[0].id).toBe('node1');
            expect(nodes[1].id).toBe('node2');
        });

        it('should return elements from v3 format (record)', () => {
            const model = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                    elem2: { id: 'elem2', type: 'Interface', name: 'InterfaceB' },
                },
            } as any as ApollonModelData;
            const nodes = getModelNodes(model);
            expect(nodes).toHaveLength(2);
            expect(nodes.map((n) => n.id).sort()).toEqual(['elem1', 'elem2']);
        });

        it('should prefer nodes over elements when both are present', () => {
            const model = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                },
            } as any as ApollonModelData;
            const nodes = getModelNodes(model);
            expect(nodes).toHaveLength(1);
            expect(nodes[0].id).toBe('node1');
        });
    });

    describe('getModelEdges', () => {
        it('should return empty array for undefined model', () => {
            expect(getModelEdges(undefined)).toEqual([]);
        });

        it('should return empty array for model without edges or relationships', () => {
            const model: ApollonModelData = { version: '4.0.0' };
            expect(getModelEdges(model)).toEqual([]);
        });

        it('should return edges from v4 format (array)', () => {
            const model = {
                version: '4.0.0',
                edges: [
                    { id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } },
                    { id: 'edge2', source: 'node2', target: 'node3', type: 'Inheritance', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } },
                ],
            } as any as ApollonModelData;
            const edges = getModelEdges(model);
            expect(edges).toHaveLength(2);
            expect(edges[0].id).toBe('edge1');
            expect(edges[1].id).toBe('edge2');
        });

        it('should return relationships from v3 format (record)', () => {
            const model = {
                version: '3.0.0',
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                    rel2: { id: 'rel2', type: 'Inheritance', name: '' },
                },
            } as any as ApollonModelData;
            const edges = getModelEdges(model);
            expect(edges).toHaveLength(2);
            expect(edges.map((e) => e.id).sort()).toEqual(['rel1', 'rel2']);
        });

        it('should prefer edges over relationships when both are present', () => {
            const model = {
                version: '4.0.0',
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                },
            } as any as ApollonModelData;
            const edges = getModelEdges(model);
            expect(edges).toHaveLength(1);
            expect(edges[0].id).toBe('edge1');
        });
    });

    describe('countModelElements', () => {
        it('should return 0 for undefined model', () => {
            expect(countModelElements(undefined)).toBe(0);
        });

        it('should return 0 for empty model', () => {
            const model: ApollonModelData = { version: '4.0.0' };
            expect(countModelElements(model)).toBe(0);
        });

        it('should count nodes and edges in v4 format', () => {
            const model = {
                version: '4.0.0',
                nodes: [
                    { id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                    { id: 'node2', type: 'Interface', width: 100, height: 50, position: { x: 100, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                ],
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            } as any as ApollonModelData;
            expect(countModelElements(model)).toBe(3);
        });

        it('should count elements and relationships in v3 format', () => {
            const model = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                    elem2: { id: 'elem2', type: 'Interface', name: 'InterfaceB' },
                    elem3: { id: 'elem3', type: 'Class', name: 'ClassC' },
                },
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                    rel2: { id: 'rel2', type: 'Inheritance', name: '' },
                },
            } as any as ApollonModelData;
            expect(countModelElements(model)).toBe(5);
        });
    });

    describe('isModelEmpty', () => {
        it('should return true for undefined model', () => {
            expect(isModelEmpty(undefined)).toBe(true);
        });

        it('should return true for model with no nodes', () => {
            const model = {
                version: '4.0.0',
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            } as any as ApollonModelData;
            expect(isModelEmpty(model)).toBe(true);
        });

        it('should return false for model with nodes', () => {
            const model = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
            } as any as ApollonModelData;
            expect(isModelEmpty(model)).toBe(false);
        });

        it('should return false for v3 model with elements', () => {
            const model = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                },
            } as any as ApollonModelData;
            expect(isModelEmpty(model)).toBe(false);
        });
    });

    describe('hasModelElements', () => {
        it('should return false for undefined model', () => {
            expect(hasModelElements(undefined)).toBe(false);
        });

        it('should return false for empty model', () => {
            const model: ApollonModelData = { version: '4.0.0' };
            expect(hasModelElements(model)).toBe(false);
        });

        it('should return true for model with nodes', () => {
            const model = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
            } as any as ApollonModelData;
            expect(hasModelElements(model)).toBe(true);
        });

        it('should be the inverse of isModelEmpty', () => {
            const modelWithNodes = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
            } as any as ApollonModelData;
            const emptyModel: ApollonModelData = { version: '4.0.0' };

            expect(hasModelElements(modelWithNodes)).toBe(!isModelEmpty(modelWithNodes));
            expect(hasModelElements(emptyModel)).toBe(!isModelEmpty(emptyModel));
            expect(hasModelElements(undefined)).toBe(!isModelEmpty(undefined));
        });
    });

    describe('getModelElementType', () => {
        it('should answer the UML type of a node, an edge and a nested member in v4 format', () => {
            const model = {
                version: '4.0.0',
                nodes: [
                    {
                        id: 'node1',
                        type: 'Class',
                        width: 100,
                        height: 50,
                        position: { x: 0, y: 0 },
                        data: { attributes: [{ id: 'attr1', type: 'ClassAttribute', name: '+ a: T' }] },
                        measured: { width: 100, height: 50 },
                    },
                ],
                edges: [{ id: 'edge1', source: 'node1', target: 'node1', type: 'ClassAggregation', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            } as any as ApollonModelData;

            expect(getModelElementType(model, 'node1')).toBe('Class');
            expect(getModelElementType(model, 'edge1')).toBe('ClassAggregation');
            expect(getModelElementType(model, 'attr1')).toBe('ClassAttribute');
        });

        it('should answer the same type for a model still in v3 format', () => {
            const model = {
                version: '3.0.0',
                elements: { elem1: { id: 'elem1', type: 'AbstractClass', name: 'Abstract' } },
                relationships: { rel1: { id: 'rel1', type: 'ClassAssociation', name: '' } },
            } as any as ApollonModelData;

            expect(getModelElementType(model, 'elem1')).toBe('AbstractClass');
            expect(getModelElementType(model, 'rel1')).toBe('ClassAssociation');
        });

        it('should answer undefined for an unknown element and for no model', () => {
            expect(getModelElementType(undefined, 'node1')).toBeUndefined();
            expect(getModelElementType({ version: '4.0.0', nodes: [], edges: [] } as any as ApollonModelData, 'gone')).toBeUndefined();
        });
    });

    describe('getModelElementIds', () => {
        it('should return empty set for undefined model', () => {
            const ids = getModelElementIds(undefined);
            expect(ids.size).toBe(0);
        });

        it('should return all node and edge IDs from v4 format', () => {
            const model = {
                version: '4.0.0',
                nodes: [
                    { id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                    { id: 'node2', type: 'Interface', width: 100, height: 50, position: { x: 100, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                ],
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            } as any as ApollonModelData;
            const ids = getModelElementIds(model);
            expect(ids.size).toBe(3);
            expect(ids.has('node1')).toBe(true);
            expect(ids.has('node2')).toBe(true);
            expect(ids.has('edge1')).toBe(true);
        });

        it('should return all element and relationship IDs from v3 format', () => {
            const model = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                    elem2: { id: 'elem2', type: 'Interface', name: 'InterfaceB' },
                },
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                },
            } as any as ApollonModelData;
            const ids = getModelElementIds(model);
            expect(ids.size).toBe(3);
            expect(ids.has('elem1')).toBe(true);
            expect(ids.has('elem2')).toBe(true);
            expect(ids.has('rel1')).toBe(true);
        });
    });
});
