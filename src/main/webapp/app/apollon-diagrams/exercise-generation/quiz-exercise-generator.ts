import { convertRenderedSVGToPNG } from './svg-renderer';
import * as TempID from '../../quiz/edit/temp-id';
import { Course } from '../../entities/course';
import { QuizExercise, QuizExerciseService } from '../../entities/quiz-exercise';
import { FileUploaderService } from '../../shared/http/file-uploader.service';
import { DragAndDropQuestion } from '../../entities/drag-and-drop-question';
import { DropLocation } from '../../entities/drop-location';
import { DragAndDropMapping } from '../../entities/drag-and-drop-mapping';
import { DragItem } from '../../entities/drag-item';
import { ScoringType } from '../../entities/quiz-question';
import * as moment from 'moment';
import { ApollonEditor, Element, UMLModel, UMLClassifier, ElementType, SVG } from '@ls1intum/apollon';

// Drop locations in quiz exercises are relatively positioned and sized
// using integers in the interval [0,200]
const MAX_SIZE_UNIT = 200;

// Add a margin to the exported images
const MARGIN = 20;

export async function generateDragAndDropQuizExercise(
    diagramTitle: string,
    model: UMLModel,
    course: Course,
    fileUploaderService: FileUploaderService,
    quizExerciseService: QuizExerciseService
) {
    // Render the layouted diagram as SVG
    const renderedDiagram = ApollonEditor.exportModelAsSvg(model, {
        margin: MARGIN,
        keepOriginalSize: true,
        exclude: [...model.interactive.elements, ...model.interactive.relationships]
    });

    // Create a PNG diagram background image from the given diagram SVG
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);

    // Upload the diagram background image
    const backgroundImageUploadResponse = await fileUploaderService.uploadFile(diagramBackground, 'diagram-background.png');

    const backgroundFilePath: string = backgroundImageUploadResponse.path;
    const dragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    // Create Drag Items, Drop Locations and their mappings for each interactive element
    for (const id of [...model.interactive.elements, ...model.interactive.relationships]) {
        const element: Element = findElementInModel(model, id);
        const { dragItem, dropLocation, correctMapping } = await generateDragAndDropItem(
            element,
            model,
            renderedDiagram.clip,
            fileUploaderService
        );
        dragItems.push(dragItem);
        dropLocations.push(dropLocation);
        correctMappings.push(correctMapping);
    }

    // Generate a drag-and-drop question object
    const dragAndDropQuestion: DragAndDropQuestion = generateDragAndDropQuestion(
        diagramTitle,
        dragItems,
        dropLocations,
        correctMappings,
        backgroundFilePath
    );

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
    backgroundFilePath: string
): DragAndDropQuestion {
    const dragAndDropQuestion = new DragAndDropQuestion();
    dragAndDropQuestion.title = diagramTitle;
    dragAndDropQuestion.text =
        'Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.';
    dragAndDropQuestion.scoringType = ScoringType.PROPORTIONAL_WITH_PENALTY; // default value
    dragAndDropQuestion.randomizeOrder = true;
    dragAndDropQuestion.score = 1;
    dragAndDropQuestion.dropLocations = dropLocations;
    dragAndDropQuestion.dragItems = dragItems;
    dragAndDropQuestion.correctMappings = correctMappings;
    dragAndDropQuestion.backgroundFilePath = backgroundFilePath;
    return dragAndDropQuestion;
}

async function generateDragAndDropItem(
    element: Element,
    model: UMLModel,
    clip: { x: number; y: number; width: number; height: number },
    fileUploaderService: FileUploaderService
): Promise<{ dragItem: DragItem; dropLocation: DropLocation; correctMapping: DragAndDropMapping }> {
    const isRelationship = Object.keys(model.relationships).includes(element.id);

    const margin = isRelationship ? MARGIN : 0

    const renderedEntity: SVG = ApollonEditor.exportModelAsSvg(model, {
        margin: margin,
        include: [element.id]
    });
    const image = await convertRenderedSVGToPNG(renderedEntity);

    const imageUploadResponse = await fileUploaderService.uploadFile(image, `entity-${element.id}.png`);
    const dragItem = new DragItem();
    dragItem.tempID = TempID.generate();
    dragItem.pictureFilePath = imageUploadResponse.path;

    renderedEntity.clip.x += margin;
    renderedEntity.clip.y += margin;
    renderedEntity.clip.width -= margin * 2;
    renderedEntity.clip.height -= margin * 2;

    if (isRelationship) {
        const MIN_SIDE_LENGTH = 30;

        if (renderedEntity.clip.width < MIN_SIDE_LENGTH) {
            const delta = MIN_SIDE_LENGTH - renderedEntity.clip.width;
            renderedEntity.clip.width = MIN_SIDE_LENGTH;
            renderedEntity.clip.x -= delta / 2;
        }

        if (renderedEntity.clip.height < MIN_SIDE_LENGTH) {
            const delta = MIN_SIDE_LENGTH - renderedEntity.clip.height;
            renderedEntity.clip.height = MIN_SIDE_LENGTH;
            renderedEntity.clip.y -= delta / 2;
        }
    }

    const dropLocation = new DropLocation();
    dropLocation.tempID = TempID.generate();
    dropLocation.posX = Math.round((MAX_SIZE_UNIT * (renderedEntity.clip.x - clip.x)) / clip.width);
    dropLocation.posY = Math.round((MAX_SIZE_UNIT * (renderedEntity.clip.y - clip.y)) / clip.height);
    dropLocation.width = Math.round((MAX_SIZE_UNIT * renderedEntity.clip.width) / clip.width);
    dropLocation.height = Math.round((MAX_SIZE_UNIT * renderedEntity.clip.height) / clip.height);

    const correctMapping = new DragAndDropMapping();
    correctMapping.dragItem = dragItem;
    correctMapping.dropLocation = dropLocation;
    return { dragItem, dropLocation, correctMapping };
}

function findElementInModel(model: UMLModel, id: string): Element {
    const memberElements = Object.values(model.elements)
        .filter(element =>
            ([ElementType.Class, ElementType.AbstractClass, ElementType.Interface, ElementType.Enumeration] as ElementType[]).includes(
                element.type
            )
        )
        .map(element => element as UMLClassifier)
        .reduce<Element[]>((member, element) => [...member, ...element.attributes, ...element.methods], []);
    const elements: { [id: string]: Element } = {
        ...model.elements,
        ...memberElements.reduce((object, member) => ({ ...object, [member.id]: member }), {}),
        ...model.relationships
    };
    return elements[id];
}
