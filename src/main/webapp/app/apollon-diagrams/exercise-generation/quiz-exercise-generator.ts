import { ApollonEditor, Element, SVG, UMLElementType, UMLModel, UMLRelationshipType } from '@ls1intum/apollon';
import * as moment from 'moment';
import { Course } from '../../entities/course';
import { DragAndDropMapping } from '../../entities/drag-and-drop-mapping';
import { DragAndDropQuestion } from '../../entities/drag-and-drop-question';
import { DragItem } from '../../entities/drag-item';
import { DropLocation } from '../../entities/drop-location';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { ScoringType } from '../../entities/quiz-question';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { convertRenderedSVGToPNG } from './svg-renderer';

type Position = { x: number; y: number };
type Size = { width: number; height: number };
type Boundary = Position & Size;

// Drop locations in quiz exercises are relatively positioned and sized
// using integers in the interval [0,200]
const MAX_SIZE_UNIT = 200;

export async function generateDragAndDropQuizExercise(
    diagramTitle: string,
    model: UMLModel,
    course: Course,
    fileUploaderService: FileUploaderService,
    quizExerciseService: QuizExerciseService,
) {
    // Render the layouted diagram as SVG
    const renderedDiagram = ApollonEditor.exportModelAsSvg(model, {
        keepOriginalSize: true,
        exclude: [...model.interactive.elements, ...model.interactive.relationships],
    });

    // Create a PNG diagram background image from the given diagram SVG
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);

    // Upload the diagram background image
    const backgroundImageUploadResponse = await fileUploaderService.uploadFile(diagramBackground, 'diagram-background.png');

    const backgroundFilePath: string = backgroundImageUploadResponse.path;
    const dragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    const elements = [...model.elements, ...model.relationships];
    // Create Drag Items, Drop Locations and their mappings for each interactive element
    for (const elementId of [...model.interactive.elements, ...model.interactive.relationships]) {
        const element = elements.find(elem => elem.id === elementId);
        const { dragItem, dropLocation } = await generateDragAndDropItem(element, model, fileUploaderService);
        const correctMapping = new DragAndDropMapping(dragItem, dropLocation);
        dragItems.push(dragItem);
        dropLocations.push(dropLocation);
        correctMappings.push(correctMapping);
    }

    // Generate a drag-and-drop question object
    const dragAndDropQuestion: DragAndDropQuestion = generateDragAndDropQuestion(diagramTitle, dragItems, dropLocations, correctMappings, backgroundFilePath);

    // Generate a quiz exercise object
    const quizExercise: QuizExercise = generateQuizExercise(course, diagramTitle, dragAndDropQuestion);

    // Create the quiz exercise
    await quizExerciseService.create(quizExercise).toPromise();
}

function generateQuizExercise(course: Course, diagramTitle: string, dragAndDropQuestion: DragAndDropQuestion): QuizExercise {
    const quizExercise = new QuizExercise(course);
    quizExercise.title = diagramTitle;
    quizExercise.duration = 600;
    quizExercise.isVisibleBeforeStart = false;
    quizExercise.isOpenForPractice = false;
    quizExercise.isPlannedToStart = false;
    quizExercise.releaseDate = moment();
    quizExercise.randomizeQuestionOrder = true;
    quizExercise.quizQuestions = [dragAndDropQuestion];
    return quizExercise;
}

function generateDragAndDropQuestion(
    diagramTitle: string,
    dragItems: DragItem[],
    dropLocations: DropLocation[],
    correctMappings: DragAndDropMapping[],
    backgroundFilePath: string,
): DragAndDropQuestion {
    const dragAndDropQuestion = new DragAndDropQuestion();
    dragAndDropQuestion.title = diagramTitle;
    dragAndDropQuestion.text = 'Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.';
    dragAndDropQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY;
    dragAndDropQuestion.randomizeOrder = true;
    dragAndDropQuestion.score = 1;
    dragAndDropQuestion.dropLocations = dropLocations;
    dragAndDropQuestion.dragItems = dragItems;
    dragAndDropQuestion.correctMappings = correctMappings;
    dragAndDropQuestion.backgroundFilePath = backgroundFilePath;
    return dragAndDropQuestion;
}

async function generateDragAndDropItem(element: Element, model: UMLModel, fileUploaderService: FileUploaderService): Promise<{ dragItem: DragItem; dropLocation: DropLocation }> {
    if (element.type in UMLRelationshipType) {
        return generateDragAndDropItemForRelationship(element, model, fileUploaderService);
    } else if (element.type === UMLElementType.ClassAttribute || element.type === UMLElementType.ClassMethod || element.type === UMLElementType.ObjectAttribute) {
        return generateDragAndDropItemForText(element, model);
    } else {
        return generateDragAndDropItemForElement(element, model, fileUploaderService);
    }
}

async function generateDragAndDropItemForElement(
    element: Element,
    model: UMLModel,
    fileUploaderService: FileUploaderService,
): Promise<{ dragItem: DragItem; dropLocation: DropLocation }> {
    const renderedEntity: SVG = ApollonEditor.exportModelAsSvg(model, { include: [element.id] });
    const image = await convertRenderedSVGToPNG(renderedEntity);
    const imageUploadResponse = await fileUploaderService.uploadFile(image, `element-${element.id}.png`);

    const dragItem = new DragItem();
    dragItem.pictureFilePath = imageUploadResponse.path;
    const dropLocation = computeDropLocation(element.bounds, model.size);

    return { dragItem, dropLocation };
}

async function generateDragAndDropItemForText(element: Element, model: UMLModel): Promise<{ dragItem: DragItem; dropLocation: DropLocation }> {
    const dragItem = new DragItem();
    dragItem.text = element.name;
    const dropLocation = computeDropLocation(element.bounds, model.size);

    return { dragItem, dropLocation };
}

async function generateDragAndDropItemForRelationship(
    element: Element,
    model: UMLModel,
    fileUploaderService: FileUploaderService,
): Promise<{ dragItem: DragItem; dropLocation: DropLocation }> {
    const MIN_SIZE = 30;

    let margin = {};
    const bounds = { ...element.bounds };
    if (bounds.width < MIN_SIZE) {
        const delta = MIN_SIZE - element.bounds.width;
        margin = { ...margin, right: delta / 2, left: delta / 2 };
        bounds.x -= delta / 2;
        bounds.width = MIN_SIZE;
    }
    if (bounds.height < MIN_SIZE) {
        const delta = MIN_SIZE - element.bounds.height;
        margin = { ...margin, top: delta / 2, bottom: delta / 2 };
        bounds.y -= delta / 2;
        bounds.height = MIN_SIZE;
    }

    const renderedEntity: SVG = ApollonEditor.exportModelAsSvg(model, { margin, include: [element.id] });
    const image = await convertRenderedSVGToPNG(renderedEntity);
    const imageUploadResponse = await fileUploaderService.uploadFile(image, `relationship-${element.id}.png`);

    const dragItem = new DragItem();
    dragItem.pictureFilePath = imageUploadResponse.path;
    const dropLocation = computeDropLocation(bounds, model.size);

    return { dragItem, dropLocation };
}

function computeDropLocation(elementLocation: Boundary, totalSize: Size): DropLocation {
    const dropLocation = new DropLocation();
    dropLocation.posX = Math.round((MAX_SIZE_UNIT * elementLocation.x) / totalSize.width);
    dropLocation.posY = Math.round((MAX_SIZE_UNIT * elementLocation.y) / totalSize.height);
    dropLocation.width = Math.round((MAX_SIZE_UNIT * elementLocation.width) / totalSize.width) - 1;
    dropLocation.height = Math.round((MAX_SIZE_UNIT * elementLocation.height) / totalSize.height) - 1;
    return dropLocation;
}
