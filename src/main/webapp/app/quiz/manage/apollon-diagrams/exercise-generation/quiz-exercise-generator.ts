import { ApollonEditor, ApollonNode, DiagramNodeType, SVG, UMLModel } from '@tumaet/apollon';
import { Course } from 'app/core/course/shared/entities/course.model';
import { convertRenderedSVGToPNG } from 'app/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { ScoringType } from 'app/quiz/shared/entities/quiz-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { round } from 'app/shared/util/utils';

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
    const interactiveElements = [
        ...Object.entries(model.nodes)
            .filter(([, include]) => include)
            .map(([id]) => id),
        ...Object.entries(model.edges)
            .filter(([, include]) => include)
            .map(([id]) => id),
    ];
    const elements = [...Object.values(model.nodes)];
    // Render the diagram's background image and store it
    const renderedDiagram = await ApollonEditor.exportModelAsSvg(model, {
        keepOriginalSize: true,
        exclude: interactiveElements,
    });
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);
    const files = new Map<string, Blob>();
    files.set('diagram-background.png', diagramBackground);

    const dragItems = new Map<string, DragItem>();
    const dropLocations = new Map<string, DropLocation>();

    // Create Drag Items and Drop Locations
    for (const elementId of interactiveElements) {
        const element = elements.find((elem) => elem.id === elementId);
        if (!element) {
            continue;
        }
        const { dragItem, dropLocation } = await generateDragAndDropItem(element, model, renderedDiagram.clip, files);
        dragItems.set(element.id, dragItem!);
        dropLocations.set(element.id, dropLocation!);
    }

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
    return generateDragAndDropItemForNode(element, model, svgSize, files);
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
    const renderedElement: SVG = await ApollonEditor.exportModelAsSvg(model, { include: [element.id] });
    const image = await convertRenderedSVGToPNG(renderedElement);
    const imageName = `element-${element.id}.png`;
    files.set(imageName, image);
    const dragItem = new DragItem();
    dragItem.pictureFilePath = imageName;
    const dropLocation = computeDropLocation(renderedElement.clip, svgSize);

    return new DragAndDropMapping(dragItem, dropLocation);
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
    const textualElements = Object.values(model.nodes).filter((element) => textualElementTypes.includes(element.type));

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
        const dragElement = textualElements.find((element) => element.id === dragItemElementId);
        if (!dragElement || !dragElement.parentId) {
            continue;
        }
        const dragElementSiblings = textualElements.filter((element) => element.parentId === dragElement.parentId && element.type === dragElement.type);
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
        const dragElement = textualElements.find((element) => element.id === dragItemElementId);
        if (!dragElement || !dragElement.data.name) {
            continue;
        }
        for (const [dropLocationElementId] of dropLocations.entries()) {
            const dropElement = textualElements.find((element) => element.id === dropLocationElementId);
            if (!dropElement || dropElement.id === dragElement.id || dropElement.parentId === dragElement.parentId || dropElement.data.name !== dragElement.data.name) {
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
