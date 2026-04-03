import { ApollonEdge, ApollonNode, UMLModel } from '@tumaet/apollon';

/**
 * Apollon v3 used a different data structure with elements/relationships as Record<string, T>.
 * Apollon v4 uses nodes/edges as arrays.
 *
 * When reading from the database, the JSON may still be in v3 format. The `importDiagram` function
 * from Apollon converts v3 to v4, but in some cases we may need to parse raw JSON before conversion.
 *
 * This utility module provides type-safe access to model elements regardless of the format version.
 */

/**
 * Legacy v3 element structure. This is a simplified type covering the common properties.
 */
interface V3Element {
    id: string;
    name?: string;
    type: string;
    [key: string]: unknown;
}

/**
 * Type representing data that could be in either Apollon v3 or v4 format.
 * This is used when parsing raw JSON from the database before conversion.
 */
export interface ApollonModelData {
    version?: string;
    // v4 format
    nodes?: ApollonNode[] | Record<string, V3Element>;
    edges?: ApollonEdge[] | Record<string, V3Element>;
    // v3 format (legacy)
    elements?: Record<string, V3Element>;
    relationships?: Record<string, V3Element>;
}

/**
 * Extracts nodes from an Apollon model, handling both v3 (elements) and v4 (nodes) formats.
 *
 * @param model The UML model (can be v3 or v4 format, or parsed raw JSON)
 * @returns Array of node objects with at least an 'id' property
 */
export function getModelNodes(model: UMLModel | ApollonModelData | undefined): Array<{ id: string; [key: string]: unknown }> {
    if (!model) {
        return [];
    }

    const data = model as ApollonModelData;

    // Apollon v4 uses 'nodes', v3 uses 'elements'
    const collection = data.nodes ?? data.elements;

    if (!collection) {
        return [];
    }

    // Handle both array (v4) and record (v3 or pre-conversion) formats
    return Array.isArray(collection) ? collection : Object.values(collection);
}

/**
 * Extracts edges from an Apollon model, handling both v3 (relationships) and v4 (edges) formats.
 *
 * @param model The UML model (can be v3 or v4 format, or parsed raw JSON)
 * @returns Array of edge objects with at least an 'id' property
 */
export function getModelEdges(model: UMLModel | ApollonModelData | undefined): Array<{ id: string; [key: string]: unknown }> {
    if (!model) {
        return [];
    }

    const data = model as ApollonModelData;

    // Apollon v4 uses 'edges', v3 uses 'relationships'
    const collection = data.edges ?? data.relationships;

    if (!collection) {
        return [];
    }

    // Handle both array (v4) and record (v3 or pre-conversion) formats
    return Array.isArray(collection) ? collection : Object.values(collection);
}

/**
 * Counts the total number of elements (nodes + edges) in an Apollon model.
 *
 * @param model The UML model (can be v3 or v4 format, or parsed raw JSON)
 * @returns The total count of nodes and edges
 */
export function countModelElements(model: UMLModel | ApollonModelData | undefined): number {
    return getModelNodes(model).length + getModelEdges(model).length;
}

/**
 * Checks if an Apollon model is empty (has no nodes).
 *
 * @param model The UML model (can be v3 or v4 format, or parsed raw JSON)
 * @returns true if the model has no nodes, false otherwise
 */
export function isModelEmpty(model: UMLModel | ApollonModelData | undefined): boolean {
    return getModelNodes(model).length === 0;
}

/**
 * Extracts all element IDs (both nodes and edges) from an Apollon model.
 * Useful for validating feedback references.
 *
 * @param model The UML model (can be v3 or v4 format, or parsed raw JSON)
 * @returns Set of all element IDs in the model
 */
export function getModelElementIds(model: UMLModel | ApollonModelData | undefined): Set<string> {
    const nodeIds = getModelNodes(model).map((node) => node.id);
    const edgeIds = getModelEdges(model).map((edge) => edge.id);
    return new Set([...nodeIds, ...edgeIds]);
}

/**
 * Checks if an Apollon model has elements (nodes).
 * This is the inverse of isModelEmpty for convenience.
 *
 * @param model The UML model (can be v3 or v4 format, or parsed raw JSON)
 * @returns true if the model has at least one node, false otherwise
 */
export function hasModelElements(model: UMLModel | ApollonModelData | undefined): boolean {
    return getModelNodes(model).length > 0;
}
