import { ApollonEditor, SVG, UMLElementType, UMLModel, UMLModelElement, UMLRelationshipType } from '@ls1intum/apollon';
import dayjs from 'dayjs/esm';
import { Course } from 'app/entities/course.model';
import { convertRenderedSVGToPNG } from 'app/exercises/quiz/manage/apollon-diagrams/exercise-generation/svg-renderer';
import { QuizExerciseService } from 'app/exercises/quiz/manage/quiz-exercise.service';
import { FileUploaderService } from 'app/shared/http/file-uploader.service';
import { DragAndDropMapping } from 'app/entities/quiz/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/entities/quiz/drag-and-drop-question.model';
import { ScoringType } from 'app/entities/quiz/quiz-question.model';
import { DragItem } from 'app/entities/quiz/drag-item.model';
import { DropLocation } from 'app/entities/quiz/drop-location.model';
import { QuizExercise } from 'app/entities/quiz/quiz-exercise.model';
import { lastValueFrom } from 'rxjs';
import { round } from 'app/shared/util/utils';

// Drop locations in quiz exercises are relatively positioned and sized using integers in the interval [0, 200]
export const MAX_SIZE_UNIT = 200;

/**
 * Generates a new Drag and Drop Quiz Exercise based on a UML model.
 *
 * @param {Course} course The selected `Course` in which the new `QuizExercise` should be created.
 * @param {string} title The title of the new `QuizExercise`.
 * @param {UMLModel} model The complete UML model the quiz exercise is based on.
 * @param {FileUploaderService} fileUploaderService To upload images like the background.
 * @param {QuizExerciseService} quizExerciseService To submit the new `QuizExercise`.
 */
export async function generateDragAndDropQuizExercise(
    course: Course,
    title: string,
    model: UMLModel,
    fileUploaderService: FileUploaderService,
    quizExerciseService: QuizExerciseService,
): Promise<QuizExercise> {
    const interactiveElements = [...model.interactive.elements, ...model.interactive.relationships];
    const elements = [...model.elements, ...model.relationships];

    // Render the diagram's background image and store it
    const renderedDiagram = ApollonEditor.exportModelAsSvg(model, {
        keepOriginalSize: true,
        exclude: interactiveElements,
    });
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);
    const backgroundImageUploadResponse = await fileUploaderService.uploadFile(diagramBackground, 'diagram-background.png');

    const backgroundFilePath = backgroundImageUploadResponse.path;
    const dragItems = new Map<string, DragItem>();
    const dropLocations = new Map<string, DropLocation>();

    // Create Drag Items and Drop Locations
    for (const elementId of interactiveElements) {
        const element = elements.find((elem) => elem.id === elementId);
        if (!element) {
            continue;
        }
        const { dragItem, dropLocation } = await generateDragAndDropItem(element, model, renderedDiagram.clip, fileUploaderService);
        dragItems.set(element.id, dragItem!);
        dropLocations.set(element.id, dropLocation!);
    }

    // Create all possible correct mappings between drag items and drop locations
    const correctMappings = createCorrectMappings(dragItems, dropLocations, model);

    // Generate a drag-and-drop question object
    const dragAndDropQuestion = createDragAndDropQuestion(title, backgroundFilePath, [...dragItems.values()], [...dropLocations.values()], correctMappings);

    // Generate a quiz exercise object
    const quizExercise = createDragAndDropQuizExercise(course, title, dragAndDropQuestion);

    // Save the quiz exercise
    await lastValueFrom(quizExerciseService.create(quizExercise));

    return quizExercise;
}

/**
 * Create a new Drag and Drop `QuizExercise`.
 *
 * @param {Course} course The selected `Course` in which the new `QuizExercise` should be created.
 * @param {string} title The title of the new `QuizExercise`.
 * @param {DragAndDropQuestion} question The `DragAndDropQuestion` of the new `QuizExercise`.
 *
 * @return {QuizExercise} A new Drag and Drop `QuizExercise`.
 */
function createDragAndDropQuizExercise(course: Course, title: string, question: DragAndDropQuestion): QuizExercise {
    const quizExercise = new QuizExercise(course, undefined);
    quizExercise.title = title;
    quizExercise.duration = 600;
    quizExercise.releaseDate = dayjs();
    quizExercise.quizQuestions = [question];
    return quizExercise;
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
 * @param {FileUploaderService} fileUploaderService To upload image base drag items.
 *
 * @return {Promise<DragAndDropMapping>} A Promise resolving to a Drag and Drop mapping
 */
async function generateDragAndDropItem(
    element: UMLModelElement,
    model: UMLModel,
    svgSize: { width: number; height: number },
    fileUploaderService: FileUploaderService,
): Promise<DragAndDropMapping> {
    const textualElementTypes: UMLElementType[] = [UMLElementType.ClassAttribute, UMLElementType.ClassMethod, UMLElementType.ObjectAttribute];
    if (element.type in UMLRelationshipType) {
        return generateDragAndDropItemForRelationship(element, model, svgSize, fileUploaderService);
    } else if (textualElementTypes.includes(element.type as UMLElementType)) {
        return generateDragAndDropItemForText(element, model, svgSize);
    } else {
        return generateDragAndDropItemForElement(element, model, svgSize, fileUploaderService);
    }
}

/**
 * Create a mapping of a `DragItem` and a `DropLocation` for a `UMLElement`.
 *
 * @param {UMLModelElement} element An element of the UML model.
 * @param {UMLModel} model The complete UML model.
 * @param svgSize actual size of the generated svg
 * @param {FileUploaderService} fileUploaderService To upload image base drag items.
 *
 * @return {Promise<DragAndDropMapping>} A Promise resolving to a Drag and Drop mapping
 */
async function generateDragAndDropItemForElement(
    element: UMLModelElement,
    model: UMLModel,
    svgSize: { width: number; height: number },
    fileUploaderService: FileUploaderService,
): Promise<DragAndDropMapping> {
    const renderedElement: SVG = ApollonEditor.exportModelAsSvg(model, { include: [element.id] });
    const image = await convertRenderedSVGToPNG(renderedElement);
    const imageUploadResponse = await fileUploaderService.uploadFile(image, `element-${element.id}.png`);

    const dragItem = new DragItem();
    dragItem.pictureFilePath = imageUploadResponse.path;
    const dropLocation = computeDropLocation(renderedElement.clip, svgSize);

    return new DragAndDropMapping(dragItem, dropLocation);
}

/**
 * Create a mapping of a `DragItem` and a `DropLocation` for a textual based `UMLElement`.
 *
 * @param {UMLModelElement} element A textual based element of the UML model.
 * @param {UMLModel} model The complete UML model.
 * @param svgSize actual size of the generated svg
 *
 * @return {Promise<DragAndDropMapping>} A Promise resolving to a Drag and Drop mapping
 */
async function generateDragAndDropItemForText(element: UMLModelElement, model: UMLModel, svgSize: { width: number; height: number }): Promise<DragAndDropMapping> {
    const dragItem = new DragItem();
    dragItem.text = element.name;
    const dropLocation = computeDropLocation(element.bounds, svgSize);

    return new DragAndDropMapping(dragItem, dropLocation);
}

/**
 * Create a mapping of a `DragItem` and a `DropLocation` for a `UMLRelationship`.
 *
 * @param {UMLModelElement} element A relationship of the UML model.
 * @param {UMLModel} model The complete UML model.
 * @param svgSize actual size of the generated svg
 * @param {FileUploaderService} fileUploaderService To upload image base drag items.
 *
 * @return {Promise<DragAndDropMapping>} A Promise resolving to a Drag and Drop mapping
 */
async function generateDragAndDropItemForRelationship(
    element: UMLModelElement,
    model: UMLModel,
    svgSize: { width: number; height: number },
    fileUploaderService: FileUploaderService,
): Promise<DragAndDropMapping> {
    const MIN_SIZE = 30;

    let margin = {};
    if (element.bounds.width < MIN_SIZE) {
        const delta = MIN_SIZE - element.bounds.width;
        margin = { ...margin, right: delta / 2, left: delta / 2 };
    }
    if (element.bounds.height < MIN_SIZE) {
        const delta = MIN_SIZE - element.bounds.height;
        margin = { ...margin, top: delta / 2, bottom: delta / 2 };
    }

    const renderedElement: SVG = ApollonEditor.exportModelAsSvg(model, { margin, include: [element.id] });
    const image = await convertRenderedSVGToPNG(renderedElement);
    const imageUploadResponse = await fileUploaderService.uploadFile(image, `relationship-${element.id}.png`);

    const dragItem = new DragItem();
    dragItem.pictureFilePath = imageUploadResponse.path;
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
function computeDropLocation(elementLocation: { x: number; y: number; width: number; height: number }, totalSize: { width: number; height: number }): DropLocation {
    const dropLocation = new DropLocation();
    // round to second decimal
    dropLocation.posX = round((elementLocation.x / totalSize.width) * MAX_SIZE_UNIT, 2);
    dropLocation.posY = round((elementLocation.y / totalSize.height) * MAX_SIZE_UNIT, 2);
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
    const textualElementTypes: UMLElementType[] = [UMLElementType.ClassAttribute, UMLElementType.ClassMethod, UMLElementType.ObjectAttribute];
    const mappings = new Map<string, DragAndDropMapping[]>();
    const textualElements = model.elements.filter((element) => textualElementTypes.includes(element.type));

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
        if (!dragElement || !dragElement.owner) {
            continue;
        }
        const dragElementSiblings = textualElements.filter((element) => element.owner === dragElement.owner && element.type === dragElement.type);
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
        if (!dragElement || !dragElement.name) {
            continue;
        }
        for (const [dropLocationElementId] of dropLocations.entries()) {
            const dropElement = textualElements.find((element) => element.id === dropLocationElementId);
            if (!dropElement || dropElement.id === dragElement.id || dropElement.owner === dragElement.owner || dropElement.name !== dragElement.name) {
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
