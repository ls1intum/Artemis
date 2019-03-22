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
import { ApollonEditor, UMLModel, UMLElement, UMLClassifier } from '@ls1intum/apollon';

// Drop locations in quiz exercises are relatively positioned and sized
// using integers in the interval [0,200]
const MAX_SIZE_UNIT = 200;

export async function generateDragAndDropQuizExercise(
    diagramTitle: string,
    model: UMLModel,
    interactiveElements: Set<string>,
    interactiveRelationships: Set<string>,
    fontFamily: string,
    course: Course,
    fileUploaderService: FileUploaderService,
    quizExerciseService: QuizExerciseService
) {
    const div = document.createElement('div');
    const exporter = new ApollonEditor(div, { model });
    // Render the layouted diagram as SVG
    const renderedDiagram = exporter.exportAsSVG({
        filter: [
            ...Object.values(model.elements)
                .filter(element => interactiveElements.has(element.id))
                .map(element => element.id),
            ...Object.values(model.relationships)
                .filter(relationship => interactiveRelationships.has(relationship.id))
                .map(relationship => relationship.id)
        ]
    });
    exporter.destroy();

    // Create a PNG diagram background image from the given diagram SVG
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);

    // Upload the diagram background image
    const backgroundImageUploadResponse = await fileUploaderService.uploadFile(diagramBackground, 'diagram-background.png');

    // Generate a drag-and-drop question object
    const dragAndDropQuestion = await generateDragAndDropQuestion(
        diagramTitle,
        model,
        interactiveElements,
        interactiveRelationships,
        backgroundImageUploadResponse.path,
        fontFamily,
        fileUploaderService
    );

    // Generate a quiz exercise object
    const quizExercise = new QuizExercise();
    quizExercise.title = diagramTitle;
    quizExercise.duration = 600;
    quizExercise.isVisibleBeforeStart = false;
    quizExercise.isOpenForPractice = false;
    quizExercise.isPlannedToStart = false;
    quizExercise.releaseDate = moment();
    quizExercise.randomizeQuestionOrder = true;
    quizExercise.course = course;
    quizExercise.quizQuestions = [dragAndDropQuestion];

    // Create the quiz exercise
    await quizExerciseService.create(quizExercise).toPromise();
}

async function generateDragAndDropQuestion(
    diagramTitle: string,
    model: UMLModel,
    interactiveElementIds: Set<string>,
    interactiveRelationshipIds: Set<string>,
    backgroundFilePath: string,
    fontFamily: string,
    fileUploaderService: FileUploaderService
): Promise<DragAndDropQuestion> {
    const { dragItems, dropLocations, correctMappings } = await generateDragAndDropMappings(
        model,
        interactiveElementIds,
        interactiveRelationshipIds,
        fontFamily,
        fileUploaderService
    );

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

async function generateDragAndDropMappings(
    model: UMLModel,
    interactiveElementIds: Set<string>,
    interactiveRelationshipIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const entityMappings = await generateMappingsForInteractiveEntitiesImages(
        model,
        interactiveElementIds,
        fontFamily,
        fileUploaderService
    );

    const entityMemberMappings = generateMappingsForInteractiveEntitiesTexts(model, interactiveElementIds, fontFamily);

    const relationshipMappings = await generateMappingsForInteractiveRelationships(
        model,
        interactiveRelationshipIds,
        fontFamily,
        fileUploaderService
    );

    return {
        dragItems: [...entityMappings.dragItems, ...entityMemberMappings.dragItems, ...relationshipMappings.dragItems],
        dropLocations: [...entityMappings.dropLocations, ...entityMemberMappings.dropLocations, ...relationshipMappings.dropLocations],
        correctMappings: [
            ...entityMappings.correctMappings,
            ...entityMemberMappings.correctMappings,
            ...relationshipMappings.correctMappings
        ]
    };
}

async function generateMappingsForInteractiveEntitiesImages(
    model: UMLModel,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const imageDragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    const interactiveEntities = [...interactiveElementIds].map(id => model.elements[id]);

    const div = document.createElement('div');
    const exporter = new ApollonEditor(div, { model });

    for (const entity of interactiveEntities) {
        const renderedEntity = exporter.exportAsSVG({ filter: [entity.id] })
        const image = await convertRenderedSVGToPNG(renderedEntity);

        const imageUploadResponse = await fileUploaderService.uploadFile(image, `entity-${entity.id}.png`);
        const dragItem = new DragItem();
        dragItem.tempID = TempID.generate();
        dragItem.pictureFilePath = imageUploadResponse.path;

        const dropLocation = createDropLocation(
            entity.bounds.x,
            entity.bounds.y,
            entity.bounds.width,
            entity.bounds.height,
            model.size
        );

        imageDragItems.push(dragItem);
        dropLocations.push(dropLocation);

        const correctMapping = new DragAndDropMapping();
        correctMapping.dragItem = dragItem;
        correctMapping.dropLocation = dropLocation;
        correctMappings.push(correctMapping);
    }
    exporter.destroy()

    return { dragItems: imageDragItems, dropLocations, correctMappings };
}

function generateMappingsForInteractiveEntitiesTexts(
    model: UMLModel,
    interactiveElementIds: Set<string>,
    fontFamily: string
) {
    const textDragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];

    // Since there might be multiple interactive entity members with the same name,
    // we collect all correct drop locations for each entity member name
    const correctDropLocationIDsByEntityMemberName = new Map<string, Set<number>>();

    for (const id in model.elements) {
        const entity = model.elements[id] as UMLClassifier;
        for (const entityMembers of [entity.attributes, entity.methods]) {
            const [entityKindDragItems, entityKindDropLocations] = getEntityMemberDragItemsAndDropLocations(
                model,
                entity,
                entityMembers,
                interactiveElementIds
            );

            textDragItems.push(...entityKindDragItems);
            dropLocations.push(...entityKindDropLocations);

            for (const dragItem of entityKindDragItems) {
                for (const dropLocation of entityKindDropLocations) {
                    const correctDropLocationTempIDs = (
                        correctDropLocationIDsByEntityMemberName.get(dragItem.text) || new Set<number>()
                    ).add(dropLocation.tempID);
                    correctDropLocationIDsByEntityMemberName.set(dragItem.text, correctDropLocationTempIDs);
                }
            }
        }
    }

    return {
        dragItems: textDragItems,
        dropLocations,
        correctMappings: getCorrectMappings(textDragItems, dropLocations, correctDropLocationIDsByEntityMemberName)
    };
}

function getEntityMemberDragItemsAndDropLocations(
    model: UMLModel,
    entity: UMLElement,
    entityMembers: { id: string, name: string, bounds: { x: number; y: number; width: number; height: number} }[],
    interactiveElementIds: Set<string>
): [DragItem[], DropLocation[]] {
    const interactiveMembers = entityMembers
        .filter(member => interactiveElementIds.has(member.id))
        .map(member => ({
            ...member,
            position: {
                x: entity.bounds.x + member.bounds.x,
                y: entity.bounds.y + member.bounds.y
            }
        }));

    const textDragItems: DragItem[] = interactiveMembers.map(member => {
        const dragItem = new DragItem();
        dragItem.tempID = TempID.generate();
        dragItem.text = member.name;
        return dragItem;
    });

    const dropLocations: DropLocation[] = interactiveMembers.map(member => {
        return createDropLocation(member.position.x, member.position.y, member.bounds.width, member.bounds.height, model.size);
    });

    return [textDragItems, dropLocations];
}

function getCorrectMappings(
    textDragItems: DragItem[],
    dropLocations: DropLocation[],
    correctDropLocationIDsByEntityMemberName: Map<string, Set<number>>
) {
    const correctMappings: DragAndDropMapping[] = [];

    correctDropLocationIDsByEntityMemberName.forEach((correctDropLocationTempIDs, entityMemberName) => {
        const dragItemsWithMatchingName = textDragItems.filter(dragItem => dragItem.text === entityMemberName);
        const correctDropLocations = dropLocations.filter(dropLocation => correctDropLocationTempIDs.has(dropLocation.tempID));

        for (const dragItem of dragItemsWithMatchingName) {
            for (const dropLocation of correctDropLocations) {
                const correctMapping = new DragAndDropMapping();
                correctMapping.dragItem = dragItem;
                correctMapping.dropLocation = dropLocation;
                correctMappings.push(correctMapping);
            }
        }
    });

    return correctMappings;
}

async function generateMappingsForInteractiveRelationships(
    model: UMLModel,
    interactiveRelationshipIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const dragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    const interactiveRelationships = [...interactiveRelationshipIds].map(id => model.relationships[id]);

    const div = document.createElement('div');
    const exporter = new ApollonEditor(div, { model });

    for (const relationship of interactiveRelationships) {
        const renderedRelationship = exporter.exportAsSVG({ filter: [relationship.id] })

        const image = await convertRenderedSVGToPNG(renderedRelationship);

        const imageUploadResponse = await fileUploaderService.uploadFile(
            image,
            `relationship-${relationship.id}.png`
        );

        const imageDragItem = new DragItem();
        imageDragItem.tempID = TempID.generate();
        imageDragItem.pictureFilePath = imageUploadResponse.path;

        const boundingBox = relationship.bounds;
        const MIN_SIDE_LENGTH = 30;

        if (boundingBox.width < MIN_SIDE_LENGTH) {
            const delta = MIN_SIDE_LENGTH - boundingBox.width;
            boundingBox.width = MIN_SIDE_LENGTH;
            boundingBox.x -= delta / 2;
        }

        if (boundingBox.height < MIN_SIDE_LENGTH) {
            const delta = MIN_SIDE_LENGTH - boundingBox.height;
            boundingBox.height = MIN_SIDE_LENGTH;
            boundingBox.y -= delta / 2;
        }

        const dropLocation = createDropLocation(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height, model.size);

        dragItems.push(imageDragItem);
        dropLocations.push(dropLocation);

        const mapping = new DragAndDropMapping();
        mapping.dragItem = imageDragItem;
        mapping.dropLocation = dropLocation;

        correctMappings.push(mapping);
    }

    return { dragItems, dropLocations, correctMappings };
}

function createDropLocation(x: number, y: number, width: number, height: number, totalSize: { width: number, height: number }): DropLocation {
    const dropLocation = new DropLocation();
    dropLocation.tempID = TempID.generate();
    dropLocation.posX = Math.round((MAX_SIZE_UNIT * x) / totalSize.width);
    dropLocation.posY = Math.round((MAX_SIZE_UNIT * y) / totalSize.height);
    dropLocation.width = Math.round((MAX_SIZE_UNIT * width) / totalSize.width);
    dropLocation.height = Math.round((MAX_SIZE_UNIT * height) / totalSize.height);
    return dropLocation;
}
