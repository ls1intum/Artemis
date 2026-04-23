import { ApollonEditor, ApollonNode, DiagramNodeType, SVG, UMLModel } from '@tumaet/apollon';
import { Course } from 'app/core/course/shared/entities/course.model';
import { convertRenderedSVGToPNG, trimRenderedSVGToContent } from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { round } from 'app/shared/util/utils';
import { getQuizRelevantElementIds } from 'app/modeling/shared/apollon-model.util';

interface GeneratedDiagramElement {
    dragItem: DragItem;
    dropLocation: DropLocation;
    image: Blob;
    imageName: string;
}

function getInteractiveElements(model: UMLModel): string[] {
    return getQuizRelevantElementIds(model);
}

/**
 * Helper to get all elements (nodes + relationships/edges) from a model.
 * Supports both v3 and v4 formats.
 * v3: elements (Record) + relationships (Record)
 * v4: nodes (array) + edges (array)
 */
function getModelElements(model: UMLModel): any[] {
    const modelAny = model as any;

    // v3 format: elements and relationships are Records
    if (modelAny.elements && typeof modelAny.elements === 'object' && !Array.isArray(modelAny.elements)) {
        const elements = Object.values(modelAny.elements);
        const relationships = modelAny.relationships ? Object.values(modelAny.relationships) : [];
        return [...elements, ...relationships];
    }

    // v4 format: nodes and edges are arrays
    if (Array.isArray(modelAny.nodes)) {
        const nodes = modelAny.nodes;
        const edges = modelAny.edges ?? [];
        return [...nodes, ...edges];
    }

    return [];
}

// Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200]
export const MAX_SIZE_UNIT = 200;
const BACKGROUND_CUTOUT_PADDING_PIXELS = 4;

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
        files.set(generatedElement.imageName, generatedElement.image);
        dragItems.set(element.id, generatedElement.dragItem);
        dropLocations.set(element.id, generatedElement.dropLocation);
    }

    const diagramBackground = await createBlankedDiagramBackground(renderedDiagram, [...dropLocations.values()]);
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

async function createGeneratedDiagramElement(element: ApollonNode, model: UMLModel, svgSize: { width: number; height: number }): Promise<GeneratedDiagramElement> {
    const renderedElement: SVG = trimRenderedSVGToContent(await ApollonEditor.exportModelAsSvg(model, { include: [element.id], svgMode: 'compat', margin: 0 }));
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

async function createBlankedDiagramBackground(renderedDiagram: SVG, dropLocations: DropLocation[]): Promise<Blob> {
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);
    const imageUrl = URL.createObjectURL(diagramBackground);

    try {
        const image = await loadImage(imageUrl);
        const canvas = document.createElement('canvas');
        canvas.width = image.width;
        canvas.height = image.height;

        const context = canvas.getContext('2d');
        if (!context) {
            throw new Error('Failed to create background canvas context');
        }

        context.drawImage(image, 0, 0);
        context.fillStyle = 'white';

        for (const dropLocation of dropLocations) {
            const x = (dropLocation.posX! / MAX_SIZE_UNIT) * canvas.width;
            const y = (dropLocation.posY! / MAX_SIZE_UNIT) * canvas.height;
            const width = (dropLocation.width! / MAX_SIZE_UNIT) * canvas.width;
            const height = (dropLocation.height! / MAX_SIZE_UNIT) * canvas.height;
            const paddedX = Math.max(0, Math.floor(x - BACKGROUND_CUTOUT_PADDING_PIXELS));
            const paddedY = Math.max(0, Math.floor(y - BACKGROUND_CUTOUT_PADDING_PIXELS));
            const paddedRight = Math.min(canvas.width, Math.ceil(x + width + BACKGROUND_CUTOUT_PADDING_PIXELS));
            const paddedBottom = Math.min(canvas.height, Math.ceil(y + height + BACKGROUND_CUTOUT_PADDING_PIXELS));

            // Overpaint only the generated background so antialiased Apollon strokes do not remain visible.
            context.fillRect(paddedX, paddedY, paddedRight - paddedX, paddedBottom - paddedY);
        }

        return await canvasToBlob(canvas);
    } finally {
        URL.revokeObjectURL(imageUrl);
    }
}

function loadImage(source: string): Promise<HTMLImageElement> {
    return new Promise((resolve, reject) => {
        const image = new Image();
        image.onload = () => resolve(image);
        image.onerror = (error) => reject(error);
        image.src = source;
    });
}

function canvasToBlob(canvas: HTMLCanvasElement): Promise<Blob> {
    return new Promise((resolve, reject) => {
        canvas.toBlob((blob) => {
            if (blob) {
                resolve(blob);
                return;
            }
            reject(new Error('Failed to convert background canvas to PNG'));
        }, 'image/png');
    });
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
    const textualElementTypes: DiagramNodeType[] = ['class', 'package'];
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
