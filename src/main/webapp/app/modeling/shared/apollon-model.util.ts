import { ApollonEdge, ApollonNode, UMLModel, importDiagram } from '@tumaet/apollon';
import { deepClone } from 'app/foundation/util/deep-clone.util';

interface V3Element {
    id: string;
    name?: string;
    type: string;
    [key: string]: unknown;
}

export interface ApollonModelData {
    version?: string;
    nodes?: ApollonNode[] | Record<string, V3Element>;
    edges?: ApollonEdge[] | Record<string, V3Element>;
    elements?: Record<string, V3Element>;
    relationships?: Record<string, V3Element>;
    interactive?: {
        elements?: Record<string, boolean>;
        relationships?: Record<string, boolean>;
    };
}

export function normalizeApollonModel(model: UMLModel | ApollonModelData): UMLModel {
    const hadExplicitInteractiveSelection = model.interactive !== undefined;
    const normalized = importDiagram(deepClone(model));

    if (hadExplicitInteractiveSelection && normalized.interactive === undefined) {
        normalized.interactive = { elements: {}, relationships: {} };
    }
    return normalized;
}

export function getModelNodes(model: UMLModel | ApollonModelData | undefined): Array<{ id: string; [key: string]: unknown }> {
    if (!model) {
        return [];
    }

    const data = model as ApollonModelData;

    const collection = data.nodes ?? data.elements;

    if (!collection) {
        return [];
    }

    return Array.isArray(collection) ? collection : Object.values(collection);
}

export function getModelEdges(model: UMLModel | ApollonModelData | undefined): Array<{ id: string; [key: string]: unknown }> {
    if (!model) {
        return [];
    }

    const data = model as ApollonModelData;

    const collection = data.edges ?? data.relationships;

    if (!collection) {
        return [];
    }

    return Array.isArray(collection) ? collection : Object.values(collection);
}

function getNestedNodeElements(model: UMLModel | ApollonModelData | undefined): Array<{ id: string; [key: string]: unknown }> {
    return getModelNodes(model).flatMap((node) => {
        const data = node.data as Record<string, unknown> | undefined;
        const nestedCollections = [data?.attributes, data?.methods, data?.actionRows];

        return nestedCollections.flatMap((collection) => {
            if (!Array.isArray(collection)) {
                return [];
            }

            return collection.filter(
                (item): item is { id: string; [key: string]: unknown } => !!item && typeof item === 'object' && typeof (item as { id?: unknown }).id === 'string',
            );
        });
    });
}

export function countModelElements(model: UMLModel | ApollonModelData | undefined): number {
    return getModelNodes(model).length + getNestedNodeElements(model).length + getModelEdges(model).length;
}

export function isModelEmpty(model: UMLModel | ApollonModelData | undefined): boolean {
    return getModelNodes(model).length === 0;
}

/** Returns the stable UML type used in feedback references across stored v3 and imported v4 models. */
export function getModelElementType(model: UMLModel | ApollonModelData | undefined, elementId: string): string | undefined {
    if (!model) {
        return undefined;
    }

    const candidates = [...getModelNodes(model), ...getModelEdges(model), ...getNestedNodeElements(model)];
    const match = candidates.find((element) => element.id === elementId);
    const type = match?.type;
    return typeof type === 'string' ? type : undefined;
}

export function getModelElementIds(model: UMLModel | ApollonModelData | undefined): Set<string> {
    const nodeIds = getModelNodes(model).map((node) => node.id);
    const nestedNodeElementIds = getNestedNodeElements(model).map((element) => element.id);
    const edgeIds = getModelEdges(model).map((edge) => edge.id);
    return new Set([...nodeIds, ...nestedNodeElementIds, ...edgeIds]);
}

export function hasExplicitInteractiveConfig(model: UMLModel | ApollonModelData | undefined): boolean {
    return !!model?.interactive;
}

export function getExplicitInteractiveElementIds(model: UMLModel | ApollonModelData | undefined): string[] | undefined {
    if (!model) {
        return undefined;
    }

    const interactive = (model as ApollonModelData).interactive;
    if (!interactive) {
        return undefined;
    }

    const validIds = getModelElementIds(model);
    const elementIds = Object.entries(interactive.elements ?? {})
        .filter(([id, included]) => included && validIds.has(id))
        .map(([id]) => id);
    const relationshipIds = Object.entries(interactive.relationships ?? {})
        .filter(([id, included]) => included && validIds.has(id))
        .map(([id]) => id);

    return [...elementIds, ...relationshipIds];
}

export function getQuizRelevantElementIds(model: UMLModel | ApollonModelData | undefined): string[] {
    if (!model) {
        return [];
    }

    if (hasExplicitInteractiveConfig(model)) {
        return getExplicitInteractiveElementIds(model) ?? [];
    }

    return [...getModelElementIds(model)];
}

export function hasModelElements(model: UMLModel | ApollonModelData | undefined): boolean {
    return getModelNodes(model).length > 0;
}

export function hasQuizRelevantElements(model: UMLModel | ApollonModelData | undefined): boolean {
    return getQuizRelevantElementIds(model).length > 0;
}
