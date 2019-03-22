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

// Drop locations in quiz exercises are relatively positioned and sized
// using integers in the interval [0,200]
const MAX_SIZE_UNIT = 200;

export async function generateDragAndDropQuizExercise(
    diagramTitle: string,
    layoutedDiagram: LayoutedDiagram,
    interactiveElements: Set<string>,
    interactiveRelationships: Set<string>,
    fontFamily: string,
    course: Course,
    fileUploaderService: FileUploaderService,
    quizExerciseService: QuizExerciseService
) {
    // Render the layouted diagram as SVG
    const renderedDiagram = renderDiagramToSVG(layoutedDiagram, {
        shouldRenderElement: id => !interactiveElements.has(id),
        fontFamily
    });

    // Create a PNG diagram background image from the given diagram SVG
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);

    // Upload the diagram background image
    const backgroundImageUploadResponse = await fileUploaderService.uploadFile(diagramBackground, 'diagram-background.png');

    // Generate a drag-and-drop question object
    const dragAndDropQuestion = await generateDragAndDropQuestion(
        diagramTitle,
        layoutedDiagram,
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
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    backgroundFilePath: string,
    fontFamily: string,
    fileUploaderService: FileUploaderService
): Promise<DragAndDropQuestion> {
    const { dragItems, dropLocations, correctMappings } = await generateDragAndDropMappings(
        layoutedDiagram,
        interactiveElementIds,
        fontFamily,
        fileUploaderService
    );

    const dragAndDropQuestion = new DragAndDropQuestion();
    dragAndDropQuestion.title = diagramTitle;
    dragAndDropQuestion.text = 'Fill the empty spaces in the UML diagram by dragging and dropping the elements below the diagram into the correct places.';
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
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const entityMappings = await generateMappingsForInteractiveEntitiesImages(
        layoutedDiagram,
        interactiveElementIds,
        fontFamily,
        fileUploaderService
    );

    const entityMemberMappings = generateMappingsForInteractiveEntitiesTexts(layoutedDiagram, interactiveElementIds, fontFamily);

    const relationshipMappings = await generateMappingsForInteractiveRelationships(
        layoutedDiagram,
        interactiveElementIds,
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
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const imageDragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    const interactiveEntities = layoutedDiagram.entities.filter(entity => interactiveElementIds.has(entity.id));

    for (const entity of interactiveEntities) {
        const renderedEntity = renderEntityToSVG(entity, { fontFamily, shouldRenderElement: () => true });
        const image = await convertRenderedSVGToPNG(renderedEntity);

        const imageUploadResponse = await fileUploaderService.uploadFile(image, `entity-${entity.id}.png`);
        const dragItem = new DragItem();
        dragItem.tempID = TempID.generate();
        dragItem.pictureFilePath = imageUploadResponse.path;

        const dropLocation = createDropLocation(
            entity.position.x,
            entity.position.y,
            entity.size.width,
            entity.size.height,
            layoutedDiagram.size
        );

        imageDragItems.push(dragItem);
        dropLocations.push(dropLocation);

        const correctMapping = new DragAndDropMapping();
        correctMapping.dragItem = dragItem;
        correctMapping.dropLocation = dropLocation;
        correctMappings.push(correctMapping);
    }

    return { dragItems: imageDragItems, dropLocations, correctMappings };
}

function generateMappingsForInteractiveEntitiesTexts(
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string
) {
    const textDragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];

    // Since there might be multiple interactive entity members with the same name,
    // we collect all correct drop locations for each entity member name
    const correctDropLocationIDsByEntityMemberName = new Map<string, Set<number>>();

    for (const entity of layoutedDiagram.entities) {
        for (const entityMembers of [entity.attributes, entity.methods]) {
            const [entityKindDragItems, entityKindDropLocations] = getEntityMemberDragItemsAndDropLocations(
                layoutedDiagram,
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
    layoutedDiagram: LayoutedDiagram,
    entity: LayoutedEntity,
    entityMembers: LayoutedEntityMember[],
    interactiveElementIds: Set<string>
): [DragItem[], DropLocation[]] {
    const interactiveMembers = entityMembers.filter(member => interactiveElementIds.has(member.id)).map(member => ({
        ...member,
        position: {
            x: entity.position.x + member.position.x,
            y: entity.position.y + member.position.y
        }
    }));

    const textDragItems: DragItem[] = interactiveMembers.map(member => {
        const dragItem = new DragItem();
        dragItem.tempID = TempID.generate();
        dragItem.text = member.name;
        return dragItem;
    });

    const dropLocations: DropLocation[] = interactiveMembers.map(member => {
        return createDropLocation(member.position.x, member.position.y, member.size.width, member.size.height, layoutedDiagram.size);
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
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const dragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    for (const interactiveRelationship of getInteractiveRelationships(layoutedDiagram, interactiveElementIds)) {
        const renderedRelationship = renderRelationshipToSVG(interactiveRelationship, {
            fontFamily,
            shouldRenderElement: () => true
        });

        const image = await convertRenderedSVGToPNG(renderedRelationship);

        const imageUploadResponse = await fileUploaderService.uploadFile(
            image,
            `relationship-${interactiveRelationship.relationship.id}.png`
        );

        const imageDragItem = new DragItem();
        imageDragItem.tempID = TempID.generate();
        imageDragItem.pictureFilePath = imageUploadResponse.path;

        const boundingBox = computeBoundingBox(interactiveRelationship.path);
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

        const dropLocation = createDropLocation(boundingBox.x, boundingBox.y, boundingBox.width, boundingBox.height, layoutedDiagram.size);

        dragItems.push(imageDragItem);
        dropLocations.push(dropLocation);

        const mapping = new DragAndDropMapping();
        mapping.dragItem = imageDragItem;
        mapping.dropLocation = dropLocation;

        correctMappings.push(mapping);
    }

    return { dragItems, dropLocations, correctMappings };
}

function getInteractiveRelationships(layoutedDiagram: LayoutedDiagram, interactiveElementIds: Set<string>) {
    return layoutedDiagram.relationships.filter(relationship => interactiveElementIds.has(relationship.relationship.id));
}

function createDropLocation(x: number, y: number, width: number, height: number, totalSize: Size): DropLocation {
    const dropLocation = new DropLocation();
    dropLocation.tempID = TempID.generate();
    dropLocation.posX = Math.round((MAX_SIZE_UNIT * x) / totalSize.width);
    dropLocation.posY = Math.round((MAX_SIZE_UNIT * y) / totalSize.height);
    dropLocation.width = Math.round((MAX_SIZE_UNIT * width) / totalSize.width);
    dropLocation.height = Math.round((MAX_SIZE_UNIT * height) / totalSize.height);
    return dropLocation;
}
