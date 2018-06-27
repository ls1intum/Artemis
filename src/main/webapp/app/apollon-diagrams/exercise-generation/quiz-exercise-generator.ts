import { HttpClient } from '@angular/common/http';
import {
    computeBoundingBox,
    LayoutedDiagram,
    LayoutedEntityMember,
    LayoutedRelationship,
    renderDiagramToSVG,
    renderRelationshipToSVG,
    State,
    LayoutedEntity,
    EntityMember,
    renderEntityToSVG
} from '@ls1intum/apollon';
import {
    DragAndDropMapping,
    DragAndDropQuestion,
    DragAndDropQuizExercise,
    DragItem,
    DropLocation,
    ImageDragItem,
    TextDragItem
} from './quiz-exercise-generation-types';
import { convertRenderedSVGToPNG } from './svg-renderer';
import * as TempID from './temp-id';
import { Course } from '../../entities/course';
import { QuizExerciseService } from '../../entities/quiz-exercise';
import { FileUploaderService } from '../../shared/http/file-uploader.service';

// Drop locations in quiz exercises are relatively positioned and sized
// using integers in the interval [0,200]
const MAX_SIZE_UNIT = 200;

export async function generateDragAndDropQuizExercise(
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    course: Course,
    fileUploaderService: FileUploaderService,
    quizExerciseService: QuizExerciseService
) {
    // Render the layouted diagram as SVG
    const renderedDiagram = renderDiagramToSVG(layoutedDiagram, {
        shouldRenderElement: id => !interactiveElementIds.has(id),
        fontFamily
    });

    // Create a PNG diagram background image from the given diagram SVG
    const diagramBackground = await convertRenderedSVGToPNG(renderedDiagram);

    // Upload the diagram background image
    const backgroundImageUploadResponse = await fileUploaderService.uploadFile(
        diagramBackground,
        'diagram-background.png'
    );

    // Generate a drag-and-drop question object
    const dragAndDropQuestion = await generateDragAndDropQuestion(
        layoutedDiagram,
        interactiveElementIds,
        backgroundImageUploadResponse.path,
        fontFamily,
        fileUploaderService
    );

    // Generate a quiz exercise object
    const exercise = {
        type: 'quiz-exercise',
        title: '<INSERT EXERCISE TITLE HERE>',
        duration: 600,
        isVisibleBeforeStart: false,
        isOpenForPractice: false,
        isPlannedToStart: false,
        releaseDate: new Date(),
        randomizeQuestionOrder: true,
        course,
        questions: [dragAndDropQuestion as any]
    };

    // Create the quiz exercise
    await quizExerciseService.create(exercise).toPromise();
}

async function generateDragAndDropQuestion(
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
    return {
        type: 'drag-and-drop',
        title: '<INSERT QUESTION TITLE HERE>',
        text: '<INSERT QUESTION TEXT HERE>',
        scoringType: 'ALL_OR_NOTHING',
        randomizeOrder: true,
        score: 1,
        dropLocations,
        dragItems,
        correctMappings,
        backgroundFilePath
    };
}

async function generateDragAndDropMappings(
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const entityMappings = await generateMappingsForInteractiveEntities(
        layoutedDiagram,
        interactiveElementIds,
        fontFamily,
        fileUploaderService
    );

    const entityMemberMappings = generateMappingsForInteractiveEntityMembers(
        layoutedDiagram,
        interactiveElementIds,
        fontFamily
    );

    const relationshipMappings = await generateMappingsForInteractiveRelationships(
        layoutedDiagram,
        interactiveElementIds,
        fontFamily,
        fileUploaderService
    );

    return {
        dragItems: [...entityMappings.dragItems, ...entityMemberMappings.dragItems, ...relationshipMappings.dragItems],
        dropLocations: [
            ...entityMappings.dropLocations,
            ...entityMemberMappings.dropLocations,
            ...relationshipMappings.dropLocations
        ],
        correctMappings: [
            ...entityMappings.correctMappings,
            ...entityMemberMappings.correctMappings,
            ...relationshipMappings.correctMappings
        ]
    };
}

async function generateMappingsForInteractiveEntities(
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string,
    fileUploaderService: FileUploaderService
) {
    const dragItems: DragItem[] = [];
    const dropLocations: DropLocation[] = [];
    const correctMappings: DragAndDropMapping[] = [];

    const interactiveEntities = layoutedDiagram.entities.filter(entity => interactiveElementIds.has(entity.id));

    for (const entity of interactiveEntities) {
        const renderedEntity = renderEntityToSVG(entity, { fontFamily, shouldRenderElement: () => true });
        const image = await convertRenderedSVGToPNG(renderedEntity);

        const imageUploadResponse = await fileUploaderService.uploadFile(image, `entity-${entity.id}.png`);
        const dragItem: ImageDragItem = {
            tempID: TempID.generate(),
            pictureFilePath: imageUploadResponse.path
        };

        const dropLocation: DropLocation = {
            tempID: TempID.generate(),
            posX: Math.round(MAX_SIZE_UNIT * entity.position.x / layoutedDiagram.size.width),
            posY: Math.round(MAX_SIZE_UNIT * entity.position.y / layoutedDiagram.size.height),
            width: Math.round(MAX_SIZE_UNIT * entity.size.width / layoutedDiagram.size.width),
            height: Math.round(MAX_SIZE_UNIT * entity.size.height / layoutedDiagram.size.height)
        };

        dragItems.push(dragItem);
        dropLocations.push(dropLocation);

        correctMappings.push({ dragItem, dropLocation });
    }

    return { dragItems, dropLocations, correctMappings };
}

function generateMappingsForInteractiveEntityMembers(
    layoutedDiagram: LayoutedDiagram,
    interactiveElementIds: Set<string>,
    fontFamily: string
) {
    const dragItems: TextDragItem[] = [];
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

            dragItems.push(...entityKindDragItems);
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
        dragItems,
        dropLocations,
        correctMappings: getCorrectMappings(dragItems, dropLocations, correctDropLocationIDsByEntityMemberName)
    };
}

function getEntityMemberDragItemsAndDropLocations(
    layoutedDiagram: LayoutedDiagram,
    entity: LayoutedEntity,
    entityMembers: LayoutedEntityMember[],
    interactiveElementIds: Set<string>
): [TextDragItem[], DropLocation[]] {
    const interactiveMembers = entityMembers.filter(member => interactiveElementIds.has(member.id)).map(member => ({
        ...member,
        position: {
            x: entity.position.x + member.position.x,
            y: entity.position.y + member.position.y
        }
    }));

    const dragItems: TextDragItem[] = interactiveMembers.map(member => ({
        tempID: TempID.generate(),
        text: member.name
    }));

    const dropLocations: DropLocation[] = interactiveMembers.map(member => ({
        tempID: TempID.generate(),
        posX: Math.round(MAX_SIZE_UNIT * member.position.x / layoutedDiagram.size.width),
        posY: Math.round(MAX_SIZE_UNIT * member.position.y / layoutedDiagram.size.height),
        width: Math.round(MAX_SIZE_UNIT * member.size.width / layoutedDiagram.size.width),
        height: Math.round(MAX_SIZE_UNIT * member.size.height / layoutedDiagram.size.height)
    }));

    return [dragItems, dropLocations];
}

function getCorrectMappings(
    dragItems: TextDragItem[],
    dropLocations: DropLocation[],
    correctDropLocationIDsByEntityMemberName: Map<string, Set<number>>
) {
    const correctMappings: DragAndDropMapping[] = [];

    correctDropLocationIDsByEntityMemberName.forEach((correctDropLocationTempIDs, entityMemberName) => {
        const dragItemsWithMatchingName = dragItems.filter(dragItem => dragItem.text === entityMemberName);
        const correctDropLocations = dropLocations.filter(dropLocation =>
            correctDropLocationTempIDs.has(dropLocation.tempID)
        );

        for (const dragItem of dragItemsWithMatchingName) {
            for (const dropLocation of correctDropLocations) {
                correctMappings.push({ dragItem, dropLocation });
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

        const dragItem: ImageDragItem = {
            tempID: TempID.generate(),
            pictureFilePath: imageUploadResponse.path
        };

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

        const dropLocation: DropLocation = {
            tempID: TempID.generate(),
            posX: Math.round(MAX_SIZE_UNIT * boundingBox.x / layoutedDiagram.size.width),
            posY: Math.round(MAX_SIZE_UNIT * boundingBox.y / layoutedDiagram.size.height),
            width: Math.round(MAX_SIZE_UNIT * boundingBox.width / layoutedDiagram.size.width),
            height: Math.round(MAX_SIZE_UNIT * boundingBox.height / layoutedDiagram.size.height)
        };

        dragItems.push(dragItem);
        dropLocations.push(dropLocation);

        correctMappings.push({ dragItem, dropLocation });
    }

    return { dragItems, dropLocations, correctMappings };
}

function getInteractiveRelationships(layoutedDiagram: LayoutedDiagram, interactiveElementIds: Set<string>) {
    return layoutedDiagram.relationships.filter(relationship =>
        interactiveElementIds.has(relationship.relationship.id)
    );
}
