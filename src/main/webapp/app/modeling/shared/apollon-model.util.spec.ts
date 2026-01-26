import { describe, expect, it } from 'vitest';
import { ApollonModelData, countModelElements, getModelEdges, getModelElementIds, getModelNodes, hasModelElements, isModelEmpty } from './apollon-model.util';

describe('apollon-model.util', () => {
    describe('getModelNodes', () => {
        it('should return empty array for undefined model', () => {
            expect(getModelNodes(undefined)).toEqual([]);
        });

        it('should return empty array for model without nodes or elements', () => {
            const model: ApollonModelData = { version: '4.0.0' };
            expect(getModelNodes(model)).toEqual([]);
        });

        it('should return nodes from v4 format (array)', () => {
            const model: ApollonModelData = {
                version: '4.0.0',
                nodes: [
                    { id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                    { id: 'node2', type: 'Interface', width: 100, height: 50, position: { x: 100, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                ],
            };
            const nodes = getModelNodes(model);
            expect(nodes).toHaveLength(2);
            expect(nodes[0].id).toBe('node1');
            expect(nodes[1].id).toBe('node2');
        });

        it('should return elements from v3 format (record)', () => {
            const model: ApollonModelData = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                    elem2: { id: 'elem2', type: 'Interface', name: 'InterfaceB' },
                },
            };
            const nodes = getModelNodes(model);
            expect(nodes).toHaveLength(2);
            expect(nodes.map((n) => n.id).sort()).toEqual(['elem1', 'elem2']);
        });

        it('should prefer nodes over elements when both are present', () => {
            const model: ApollonModelData = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                },
            };
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
            const model: ApollonModelData = {
                version: '4.0.0',
                edges: [
                    { id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } },
                    { id: 'edge2', source: 'node2', target: 'node3', type: 'Inheritance', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } },
                ],
            };
            const edges = getModelEdges(model);
            expect(edges).toHaveLength(2);
            expect(edges[0].id).toBe('edge1');
            expect(edges[1].id).toBe('edge2');
        });

        it('should return relationships from v3 format (record)', () => {
            const model: ApollonModelData = {
                version: '3.0.0',
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                    rel2: { id: 'rel2', type: 'Inheritance', name: '' },
                },
            };
            const edges = getModelEdges(model);
            expect(edges).toHaveLength(2);
            expect(edges.map((e) => e.id).sort()).toEqual(['rel1', 'rel2']);
        });

        it('should prefer edges over relationships when both are present', () => {
            const model: ApollonModelData = {
                version: '4.0.0',
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                },
            };
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
            const model: ApollonModelData = {
                version: '4.0.0',
                nodes: [
                    { id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                    { id: 'node2', type: 'Interface', width: 100, height: 50, position: { x: 100, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                ],
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            };
            expect(countModelElements(model)).toBe(3);
        });

        it('should count elements and relationships in v3 format', () => {
            const model: ApollonModelData = {
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
            };
            expect(countModelElements(model)).toBe(5);
        });
    });

    describe('isModelEmpty', () => {
        it('should return true for undefined model', () => {
            expect(isModelEmpty(undefined)).toBe(true);
        });

        it('should return true for model with no nodes', () => {
            const model: ApollonModelData = {
                version: '4.0.0',
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            };
            expect(isModelEmpty(model)).toBe(true);
        });

        it('should return false for model with nodes', () => {
            const model: ApollonModelData = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
            };
            expect(isModelEmpty(model)).toBe(false);
        });

        it('should return false for v3 model with elements', () => {
            const model: ApollonModelData = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                },
            };
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
            const model: ApollonModelData = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
            };
            expect(hasModelElements(model)).toBe(true);
        });

        it('should be the inverse of isModelEmpty', () => {
            const modelWithNodes: ApollonModelData = {
                version: '4.0.0',
                nodes: [{ id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } }],
            };
            const emptyModel: ApollonModelData = { version: '4.0.0' };

            expect(hasModelElements(modelWithNodes)).toBe(!isModelEmpty(modelWithNodes));
            expect(hasModelElements(emptyModel)).toBe(!isModelEmpty(emptyModel));
            expect(hasModelElements(undefined)).toBe(!isModelEmpty(undefined));
        });
    });

    describe('getModelElementIds', () => {
        it('should return empty set for undefined model', () => {
            const ids = getModelElementIds(undefined);
            expect(ids.size).toBe(0);
        });

        it('should return all node and edge IDs from v4 format', () => {
            const model: ApollonModelData = {
                version: '4.0.0',
                nodes: [
                    { id: 'node1', type: 'Class', width: 100, height: 50, position: { x: 0, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                    { id: 'node2', type: 'Interface', width: 100, height: 50, position: { x: 100, y: 0 }, data: {}, measured: { width: 100, height: 50 } },
                ],
                edges: [{ id: 'edge1', source: 'node1', target: 'node2', type: 'Association', sourceHandle: 'out', targetHandle: 'in', data: { points: [] } }],
            };
            const ids = getModelElementIds(model);
            expect(ids.size).toBe(3);
            expect(ids.has('node1')).toBe(true);
            expect(ids.has('node2')).toBe(true);
            expect(ids.has('edge1')).toBe(true);
        });

        it('should return all element and relationship IDs from v3 format', () => {
            const model: ApollonModelData = {
                version: '3.0.0',
                elements: {
                    elem1: { id: 'elem1', type: 'Class', name: 'ClassA' },
                    elem2: { id: 'elem2', type: 'Interface', name: 'InterfaceB' },
                },
                relationships: {
                    rel1: { id: 'rel1', type: 'Association', name: '' },
                },
            };
            const ids = getModelElementIds(model);
            expect(ids.size).toBe(3);
            expect(ids.has('elem1')).toBe(true);
            expect(ids.has('elem2')).toBe(true);
            expect(ids.has('rel1')).toBe(true);
        });
    });
});
