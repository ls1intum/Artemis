import { ApollonEdge, ApollonEditor, ApollonNode, SVG, UMLModel } from '@tumaet/apollon';
import { Course } from 'app/course/shared/entities/course.model';
import { convertRenderedSVGToPNG, cropRenderedSVGToElement, trimRenderedSVGToContent } from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { round } from 'app/foundation/util/utils';
import { getQuizRelevantElementIds } from 'app/modeling/shared/apollon-model.util';

interface GeneratedDiagramElement {
    dragItem: DragItem;
    dropLocation: DropLocation;
    image: Blob;
    imageName: string;
}

interface NestedNodeElement {
    id: string;
    type: string;
    parentId: string;
    parent: ApollonNode;
    name?: string;
    data?: Record<string, unknown>;
    isNestedNodeElement: true;
}

type DiagramElement = ApollonNode | ApollonEdge | NestedNodeElement;

function getInteractiveElements(model: UMLModel): string[] {
    return getQuizRelevantElementIds(model);
}

/**
 * Helper to get all elements (nodes + relationships/edges) from a model.
 * Supports both v3 and v4 formats.
 * v3: elements (Record) + relationships (Record)
 * v4: nodes (array) + edges (array)
 */
function getNestedNodeElements(node: ApollonNode): NestedNodeElement[] {
    const data = node.data as Record<string, unknown> | undefined;
    const nestedCollections = [
        { type: 'attribute', items: data?.attributes },
        { type: 'method', items: data?.methods },
        { type: 'actionRow', items: data?.actionRows },
    ];

    return nestedCollections.flatMap(({ type, items }) => {
        if (!Array.isArray(items)) {
            return [];
        }

        return items
            .filter(
                (item): item is { id: string; name?: string; [key: string]: unknown } => !!item && typeof item === 'object' && typeof (item as { id?: unknown }).id === 'string',
            )
            .map((item) => ({
                id: item.id,
                type,
                parentId: node.id,
                parent: node,
                name: item.name,
                data: item,
                isNestedNodeElement: true as const,
            }));
    });
}

function isNestedNodeElement(element: DiagramElement): element is NestedNodeElement {
    return (element as NestedNodeElement).isNestedNodeElement === true;
}

function getModelElements(model: UMLModel): DiagramElement[] {
    const modelAny = model as any;

    // v3 format: elements and relationships are Records
    if (modelAny.elements && typeof modelAny.elements === 'object' && !Array.isArray(modelAny.elements)) {
        const elements = Object.values(modelAny.elements) as DiagramElement[];
        const relationships = modelAny.relationships ? (Object.values(modelAny.relationships) as DiagramElement[]) : [];
        return [...elements, ...relationships];
    }

    // v4 format: nodes and edges are arrays
    if (Array.isArray(modelAny.nodes)) {
        const nodes = modelAny.nodes as ApollonNode[];
        const nestedNodeElements = nodes.flatMap(getNestedNodeElements);
        const edges = (modelAny.edges ?? []) as ApollonEdge[];
        return [...nodes, ...nestedNodeElements, ...edges];
    }

    return [];
}

// Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200]
export const MAX_SIZE_UNIT = 200;

/**
 * Generates a new Drag and Drop Quiz Exercise based on a UML model.
 *
 * @param {Course} course The selected `Course` in which the new `QuizExercise` should be created.
 * @param {string} title The title of the new `QuizExercise`.
 * @param {UMLModel} model The complete UML model the quiz exercise is based on.
 */
export async function generateDragAndDropQuizExercise(course: Course, title: string, model: UMLModel): Promise<DragAndDropQuestion> {
    const interactiveElements = getInteractiveElements(model);
    const elements = getModelElements(model);
    const renderedDiagram = await ApollonEditor.exportModelAsSvg(model, {
        keepOriginalSize: true,
        svgMode: 'compat',
    });
    const files = new Map<string, Blob>();

    const dragItems = new Map<string, DragItem>();
    const dropLocations = new Map<string, DropLocation>();

    // Create Drag Items and Drop Locations
    for (const elementId of interactiveElements) {
        const element = elements.find((elem) => elem.id === elementId);
        if (!element) {
            continue;
        }
        const generatedElement = await createGeneratedDiagramElement(element, model, renderedDiagram.clip);
        if (!generatedElement) {
            continue;
        }
        files.set(generatedElement.imageName, generatedElement.image);
        dragItems.set(element.id, generatedElement.dragItem);
        dropLocations.set(element.id, generatedElement.dropLocation);
    }

    const renderedBackground = await createDiagramBackground(model, renderedDiagram, [...dropLocations.keys()]);
    const diagramBackground = await convertRenderedSVGToPNG(renderedBackground);
    files.set('diagram-background.png', diagramBackground);

    // Create all possible correct mappings between drag items and drop locations
    const correctMappings = createCorrectMappings(dragItems, dropLocations, model);

    // Generate a drag-and-drop question object
    const dragAndDropQuestion = createDragAndDropQuestion(title, 'diagram-background.png', [...dragItems.values()], [...dropLocations.values()], correctMappings);
    dragAndDropQuestion.importedFiles = files;

    return dragAndDropQuestion;
}

/**
 * Create a new Drag and Drop Quiz Exercise `DragAndDropQuestion`.
 *
 * @param {string} title The title of the new `DragAndDropQuestion`.
 * @param {string} backgroundFilePath The path to the `DragAndDropQuestion`'s background image.
 * @param {DragItem[]} dragItems A list of all available `DragItem`s.
 * @param {DropLocation[]} dropLocations A list of all available `DropLocation`s.
 * @param {DragAndDropMapping[]} correctMappings A list of mappings between `DragItem`s and `DropLocation`s.
 *
 * @return {QuizExercise} A new Drag and Drop `QuizExercise`.
 */
function createDragAndDropQuestion(
    title?: string,
    backgroundFilePath?: string,
    dragItems?: DragItem[],
    dropLocations?: DropLocation[],
    correctMappings?: DragAndDropMapping[],
): DragAndDropQuestion {
    const dragAndDropQuestion = new DragAndDropQuestion();
    dragAndDropQuestion.title = title;
    dragAndDropQuestion.text = 'Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.';
    dragAndDropQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY;
    dragAndDropQuestion.points = 1;
    dragAndDropQuestion.backgroundFilePath = backgroundFilePath;
    dragAndDropQuestion.dropLocations = dropLocations;
    dragAndDropQuestion.dragItems = dragItems;
    dragAndDropQuestion.correctMappings = correctMappings;
    return dragAndDropQuestion;
}

/**
 * Convenience function to create a mapping of a `DragItem` and a `DropLocation` for any particular element.
 *
 * For each image based drag item the image needs to be uploaded first, therefore the result is returned asynchronously.
 *
 * @param {UMLModelElement} element A particular element of the UML model.
 * @param {UMLModel} model The complete UML model.
 * @param svgSize actual size of the generated svg
 * @param files a map of files that should be uploaded
 *
 * @return {Promise<DragAndDropMapping>} A Promise resolving to a Drag and Drop mapping
 */
async function generateDragAndDropItem(element: ApollonNode, model: UMLModel, svgSize: { width: number; height: number }, files: Map<string, Blob>): Promise<DragAndDropMapping> {
    const generatedElement = await createGeneratedDiagramElement(element, model, svgSize);
    if (!generatedElement) {
        throw new Error(`Could not export Apollon element ${element.id}`);
    }
    files.set(generatedElement.imageName, generatedElement.image);
    return new DragAndDropMapping(generatedElement.dragItem, generatedElement.dropLocation);
}

/**
 * Create a mapping of a `DragItem` and a `DropLocation` for a `UMLElement`.
 *
 * @param {UMLModelElement} element An element of the UML model.
 * @param {UMLModel} model The complete UML model.
 * @param svgSize actual size of the generated svg
 * @param files a map of files that should be uploaded
 *
 * @return {Promise<DragAndDropMapping>} A Promise resolving to a Drag and Drop mapping
 */
export async function generateDragAndDropItemForNode(
    element: ApollonNode,
    model: UMLModel,
    svgSize: { width: number; height: number },
    files: Map<string, Blob>,
): Promise<DragAndDropMapping> {
    return generateDragAndDropItem(element, model, svgSize, files);
}

async function createGeneratedDiagramElement(element: DiagramElement, model: UMLModel, svgSize: { width: number; height: number }): Promise<GeneratedDiagramElement | undefined> {
    const renderedElement = isNestedNodeElement(element)
        ? cropRenderedSVGToElement(await ApollonEditor.exportModelAsSvg(model, { include: [element.parentId], svgMode: 'compat', margin: 0 }), element.id)
        : trimRenderedSVGToContent(await ApollonEditor.exportModelAsSvg(model, { include: [element.id], svgMode: 'compat', margin: 0 }));
    if (!renderedElement) {
        return undefined;
    }
    const image = await convertRenderedSVGToPNG(renderedElement);
    const imageName = `element-${element.id}.png`;
    const dragItem = new DragItem();
    dragItem.pictureFilePath = imageName;

    return {
        dragItem,
        dropLocation: computeDropLocation(renderedElement.clip, svgSize),
        image,
        imageName,
    };
}

/**
 * Create a Drag and Drop Quiz Exercise `DropLocation` for an `Element`.
 *
 * Based on the total size of the complete UML model and the boundaries of an element a drop location is computed. Instead of absolute values
 * for position and size, `DropLocation`s use percentage values to the base of `MAX_SIZE_UNIT`.
 *
 * @param elementLocation The position and size of an element.
 * @param totalSize The total size of the UML model.
 *
 * @return {DropLocation} A Drag and Drop Quiz Exercise `DropLocation`.
 */
export function computeDropLocation(
    elementLocation: { x: number; y: number; width: number; height: number },
    totalSize: { x?: number; y?: number; width: number; height: number },
): DropLocation {
    const dropLocation = new DropLocation();
    // round to second decimal
    dropLocation.posX = round(((elementLocation.x - (totalSize.x ?? 0)) / totalSize.width) * MAX_SIZE_UNIT, 2);
    dropLocation.posY = round(((elementLocation.y - (totalSize.y ?? 0)) / totalSize.height) * MAX_SIZE_UNIT, 2);
    dropLocation.width = round((elementLocation.width / totalSize.width) * MAX_SIZE_UNIT, 2);
    dropLocation.height = round((elementLocation.height / totalSize.height) * MAX_SIZE_UNIT, 2);
    return dropLocation;
}

async function createDiagramBackground(model: UMLModel, renderedDiagram: SVG, excludedElementIds: string[]): Promise<SVG> {
    if (excludedElementIds.length === 0) {
        return renderedDiagram;
    }

    const elements = getModelElements(model);
    const nestedElementIds = new Set(elements.filter(isNestedNodeElement).map((element) => element.id));
    const topLevelExcludedElementIds = excludedElementIds.filter((elementId) => !nestedElementIds.has(elementId));
    const nestedExcludedElementIds = excludedElementIds.filter((elementId) => nestedElementIds.has(elementId));
    const renderedBackground = await ApollonEditor.exportModelAsSvg(model, {
        exclude: topLevelExcludedElementIds,
        keepOriginalSize: true,
        svgMode: 'compat',
    });

    return removeNestedElementsFromSVG(forceSVGClip(renderedBackground, renderedDiagram.clip), nestedExcludedElementIds);
}

function removeNestedElementsFromSVG(renderedSVG: SVG, elementIds: string[]): SVG {
    if (elementIds.length === 0) {
        return renderedSVG;
    }

    const parser = new DOMParser();
    const documentFragment = parser.parseFromString(renderedSVG.svg, 'image/svg+xml');
    const svg = documentFragment.documentElement;
    if (!(svg instanceof SVGSVGElement)) {
        return renderedSVG;
    }

    for (const elementId of elementIds) {
        const target = Array.from(svg.querySelectorAll('[data-apollon-element-id]')).find((element) => element.getAttribute('data-apollon-element-id') === elementId);
        target?.remove();
    }

    return {
        svg: new XMLSerializer().serializeToString(svg),
        clip: renderedSVG.clip,
    };
}

function forceSVGClip(renderedSVG: SVG, clip: SVG['clip']): SVG {
    const parser = new DOMParser();
    const documentFragment = parser.parseFromString(renderedSVG.svg, 'image/svg+xml');
    const svg = documentFragment.documentElement;
    if (!(svg instanceof SVGSVGElement)) {
        return {
            svg: renderedSVG.svg,
            clip,
        };
    }

    svg.setAttribute('viewBox', `${clip.x} ${clip.y} ${clip.width} ${clip.height}`);
    svg.setAttribute('width', `${clip.width}`);
    svg.setAttribute('height', `${clip.height}`);

    return {
        svg: new XMLSerializer().serializeToString(svg),
        clip,
    };
}

/**
 * Creates all permutations for correct `DragAndDropMapping` between `DragItem`s and `DropLocation`s.
 *
 * @param {Map<string, DragItem>} dragItems A mapping of element ids to drag items.
 * @param {Map<string, DropLocation>} dropLocations A mapping of element ids to drop locations.
 * @param {UMLModel} model The complete UML model.
 *
 * @return {DragAndDropMapping} A list of all possible `DragAndDropMapping`s.
 */
function createCorrectMappings(dragItems: Map<string, DragItem>, dropLocations: Map<string, DropLocation>, model: UMLModel): DragAndDropMapping[] {
    const textualElementTypes = ['class', 'package', 'attribute', 'method', 'actionRow'];
    const mappings = new Map<string, DragAndDropMapping[]>();
    const allElements = getModelElements(model);
    // Helper to get parent ID (v3 uses 'owner', v4 uses 'parentId')
    const getParentId = (element: any) => element.parentId ?? element.owner;
    // Helper to get element name (v3 uses 'name', v4 uses 'data.name')
    const getElementName = (element: any) => element.data?.name ?? element.name;
    const textualElements = allElements.filter((element: any) => textualElementTypes.includes(element.type?.toLowerCase?.() ?? element.type));

    // Create all one-on-one mappings
    for (const [dragItemElementId, dragItem] of dragItems.entries()) {
        for (const [dropLocationElementId, dropLocation] of dropLocations.entries()) {
            if (dragItemElementId === dropLocationElementId) {
                const mapping = new DragAndDropMapping(dragItem, dropLocation);
                mappings.set(dragItemElementId, [mapping]);
            }
        }
    }

    // Create all mapping permutations for textual based elements within the same parent and same type
    for (const [dragItemElementId, dragItem] of dragItems.entries()) {
        const dragElement = textualElements.find((element: any) => element.id === dragItemElementId);
        if (!dragElement || !getParentId(dragElement)) {
            continue;
        }
        const dragElementSiblings = textualElements.filter((element: any) => getParentId(element) === getParentId(dragElement) && element.type === dragElement.type);
        for (const dragElementSibling of dragElementSiblings) {
            if (dragElementSibling.id === dragItemElementId) {
                continue;
            }
            if (mappings.has(dragElementSibling.id)) {
                const mapping = new DragAndDropMapping(dragItem, dropLocations.get(dragElementSibling.id)!);
                mappings.set(dragItemElementId, [...mappings.get(dragItemElementId)!, mapping]);
            }
        }
    }

    const intermediateMappings = new Map(mappings);

    // Create all mapping permutations for textual based elements with the same name and different parents
    for (const [dragItemElementId, dragItem] of dragItems.entries()) {
        const dragElement = textualElements.find((element: any) => element.id === dragItemElementId);
        if (!dragElement || !getElementName(dragElement)) {
            continue;
        }
        for (const [dropLocationElementId] of dropLocations.entries()) {
            const dropElement = textualElements.find((element: any) => element.id === dropLocationElementId);
            if (
                !dropElement ||
                dropElement.id === dragElement.id ||
                getParentId(dropElement) === getParentId(dragElement) ||
                getElementName(dropElement) !== getElementName(dragElement)
            ) {
                continue;
            }
            if (intermediateMappings.has(dropLocationElementId)) {
                const currentMappings = [...intermediateMappings.get(dropLocationElementId)!];
                for (const currentMapping of currentMappings) {
                    const mapping = new DragAndDropMapping(dragItem, currentMapping.dropLocation);
                    mappings.set(dragItemElementId, [...mappings.get(dragItemElementId)!, mapping]);
                }
            }
        }
    }

    return new Array<DragAndDropMapping>().concat(...mappings.values());
}
