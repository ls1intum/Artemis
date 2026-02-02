import { AnswerOption } from 'app/quiz/shared/entities/answer-option.model';
import { DragAndDropMapping } from 'app/quiz/shared/entities/drag-and-drop-mapping.model';
import { DragAndDropQuestion } from 'app/quiz/shared/entities/drag-and-drop-question.model';
import { DragItem } from 'app/quiz/shared/entities/drag-item.model';
import { DropLocation } from 'app/quiz/shared/entities/drop-location.model';
import { MultipleChoiceQuestion } from 'app/quiz/shared/entities/multiple-choice-question.model';
import { QuizQuestion, QuizQuestionType } from 'app/quiz/shared/entities/quiz-question.model';
import { ShortAnswerMapping } from 'app/quiz/shared/entities/short-answer-mapping.model';
import { ShortAnswerQuestion } from 'app/quiz/shared/entities/short-answer-question.model';
import { ShortAnswerSolution } from 'app/quiz/shared/entities/short-answer-solution.model';
import { ShortAnswerSpot } from 'app/quiz/shared/entities/short-answer-spot.model';

import {
    AnswerOptionSnapshotDTO,
    DragAndDropMappingSnapshotDTO,
    DragItemSnapshotDTO,
    DropLocationSnapshotDTO,
    QuizQuestionSnapshotDTO,
    ShortAnswerMappingSnapshotDTO,
    ShortAnswerSolutionSnapshotDTO,
    ShortAnswerSpotSnapshotDTO,
} from 'app/exercise/synchronization/exercise-metadata-snapshot.dto';

export const toQuizQuestions = (questions?: QuizQuestionSnapshotDTO[]): QuizQuestion[] | undefined => {
    if (!questions || questions.length === 0) {
        return undefined;
    }
    const mapped: QuizQuestion[] = [];
    for (const question of questions) {
        const mappedQuestion = toQuizQuestion(question);
        if (mappedQuestion) {
            mapped.push(mappedQuestion);
        }
    }
    return mapped;
};

const toQuizQuestion = (question: QuizQuestionSnapshotDTO | undefined): QuizQuestion | undefined => {
    if (!question || !question.type) {
        return undefined;
    }
    const type = question.type as QuizQuestionType;
    switch (type) {
        case QuizQuestionType.MULTIPLE_CHOICE:
            return toMultipleChoiceQuestion(question);
        case QuizQuestionType.DRAG_AND_DROP:
            return toDragAndDropQuestion(question);
        case QuizQuestionType.SHORT_ANSWER:
            return toShortAnswerQuestion(question);
        default:
            return undefined;
    }
};

const applyQuizQuestionBase = (target: QuizQuestion, question: QuizQuestionSnapshotDTO) => {
    target.id = question.id;
    target.title = question.title;
    target.text = question.text;
    target.hint = question.hint;
    target.explanation = question.explanation;
    target.points = question.points;
    target.scoringType = question.scoringType;
    target.randomizeOrder = question.randomizeOrder ?? true;
    target.invalid = question.invalid ?? false;
};

const toMultipleChoiceQuestion = (question: QuizQuestionSnapshotDTO): MultipleChoiceQuestion => {
    const mc = new MultipleChoiceQuestion();
    applyQuizQuestionBase(mc, question);
    mc.singleChoice = question.singleChoice;
    mc.answerOptions = toAnswerOptions(question.answerOptions, mc);
    return mc;
};

const toAnswerOptions = (options: AnswerOptionSnapshotDTO[] | undefined, question: MultipleChoiceQuestion): AnswerOption[] | undefined => {
    if (!options) {
        return undefined;
    }
    const mapped = options.map((option) => {
        const answerOption = new AnswerOption();
        answerOption.id = option.id;
        answerOption.text = option.text ?? '';
        answerOption.hint = option.hint;
        answerOption.invalid = option.invalid ?? false;
        answerOption.explanation = option.explanation;
        answerOption.isCorrect = option.isCorrect ?? false;
        answerOption.question = question;
        return answerOption;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const toDragAndDropQuestion = (question: QuizQuestionSnapshotDTO): DragAndDropQuestion => {
    const dnd = new DragAndDropQuestion();
    applyQuizQuestionBase(dnd, question);
    dnd.backgroundFilePath = question.backgroundFilePath;
    dnd.dropLocations = toDropLocations(question.dropLocations, dnd);
    dnd.dragItems = toDragItems(question.dragItems, dnd);
    dnd.correctMappings = toDragAndDropMappings(question.correctMappings as DragAndDropMappingSnapshotDTO[] | undefined, dnd);
    return dnd;
};

const toDropLocations = (locations: DropLocationSnapshotDTO[] | undefined, question: DragAndDropQuestion): DropLocation[] | undefined => {
    if (!locations) {
        return undefined;
    }
    const mapped = locations.map((location) => {
        const dropLocation = new DropLocation();
        dropLocation.id = location.id;
        dropLocation.posX = location.posX;
        dropLocation.posY = location.posY;
        dropLocation.width = location.width;
        dropLocation.height = location.height;
        dropLocation.invalid = location.invalid ?? false;
        dropLocation.question = question;
        return dropLocation;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const toDragItems = (items: DragItemSnapshotDTO[] | undefined, question: DragAndDropQuestion): DragItem[] | undefined => {
    if (!items) {
        return undefined;
    }
    const mapped = items.map((item) => {
        const dragItem = new DragItem();
        dragItem.id = item.id;
        dragItem.pictureFilePath = item.pictureFilePath;
        dragItem.text = item.text;
        dragItem.invalid = item.invalid ?? false;
        dragItem.question = question;
        return dragItem;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const toDragAndDropMappings = (mappings: DragAndDropMappingSnapshotDTO[] | undefined, question: DragAndDropQuestion): DragAndDropMapping[] | undefined => {
    if (!mappings) {
        return undefined;
    }
    const dragItems = question.dragItems ?? [];
    const dropLocations = question.dropLocations ?? [];
    const dragItemById = new Map(dragItems.map((item) => [item.id, item]));
    const dropLocationById = new Map(dropLocations.map((location) => [location.id, location]));
    const mapped = mappings.map((mapping) => {
        const dragItem = selectDragItem(mapping, dragItems, dragItemById);
        const dropLocation = selectDropLocation(mapping, dropLocations, dropLocationById);
        const dragAndDropMapping = new DragAndDropMapping(dragItem, dropLocation);
        dragAndDropMapping.id = mapping.id;
        dragAndDropMapping.dragItemIndex = mapping.dragItemIndex;
        dragAndDropMapping.dropLocationIndex = mapping.dropLocationIndex;
        dragAndDropMapping.invalid = mapping.invalid ?? false;
        dragAndDropMapping.question = question;
        return dragAndDropMapping;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const selectDragItem = (mapping: DragAndDropMappingSnapshotDTO, dragItems: DragItem[], dragItemById: Map<number | undefined, DragItem>): DragItem | undefined => {
    if (mapping.dragItemIndex != undefined && dragItems[mapping.dragItemIndex]) {
        return dragItems[mapping.dragItemIndex];
    }
    if (mapping.dragItem?.id != undefined) {
        return dragItemById.get(mapping.dragItem.id);
    }
    return undefined;
};

const selectDropLocation = (
    mapping: DragAndDropMappingSnapshotDTO,
    dropLocations: DropLocation[],
    dropLocationById: Map<number | undefined, DropLocation>,
): DropLocation | undefined => {
    if (mapping.dropLocationIndex != undefined && dropLocations[mapping.dropLocationIndex]) {
        return dropLocations[mapping.dropLocationIndex];
    }
    if (mapping.dropLocation?.id != undefined) {
        return dropLocationById.get(mapping.dropLocation.id);
    }
    return undefined;
};

const toShortAnswerQuestion = (question: QuizQuestionSnapshotDTO): ShortAnswerQuestion => {
    const shortAnswer = new ShortAnswerQuestion();
    applyQuizQuestionBase(shortAnswer, question);
    shortAnswer.similarityValue = question.similarityValue ?? shortAnswer.similarityValue;
    shortAnswer.matchLetterCase = question.matchLetterCase ?? shortAnswer.matchLetterCase;
    shortAnswer.spots = toShortAnswerSpots(question.spots, shortAnswer);
    shortAnswer.solutions = toShortAnswerSolutions(question.solutions, shortAnswer);
    shortAnswer.correctMappings = toShortAnswerMappings(question.correctMappings as ShortAnswerMappingSnapshotDTO[] | undefined, shortAnswer);
    return shortAnswer;
};

const toShortAnswerSpots = (spots: ShortAnswerSpotSnapshotDTO[] | undefined, question: ShortAnswerQuestion): ShortAnswerSpot[] | undefined => {
    if (!spots) {
        return undefined;
    }
    const mapped = spots.map((spot) => {
        const shortAnswerSpot = new ShortAnswerSpot();
        shortAnswerSpot.id = spot.id;
        shortAnswerSpot.spotNr = spot.spotNr;
        shortAnswerSpot.width = spot.width;
        shortAnswerSpot.invalid = spot.invalid ?? false;
        shortAnswerSpot.question = question;
        return shortAnswerSpot;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const toShortAnswerSolutions = (solutions: ShortAnswerSolutionSnapshotDTO[] | undefined, question: ShortAnswerQuestion): ShortAnswerSolution[] | undefined => {
    if (!solutions) {
        return undefined;
    }
    const mapped = solutions.map((solution) => {
        const shortAnswerSolution = new ShortAnswerSolution();
        shortAnswerSolution.id = solution.id;
        shortAnswerSolution.text = solution.text;
        shortAnswerSolution.invalid = solution.invalid ?? false;
        shortAnswerSolution.question = question;
        return shortAnswerSolution;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const toShortAnswerMappings = (mappings: ShortAnswerMappingSnapshotDTO[] | undefined, question: ShortAnswerQuestion): ShortAnswerMapping[] | undefined => {
    if (!mappings) {
        return undefined;
    }
    const spots = question.spots ?? [];
    const solutions = question.solutions ?? [];
    const spotById = new Map(spots.map((spot) => [spot.id, spot]));
    const solutionById = new Map(solutions.map((solution) => [solution.id, solution]));
    const mapped = mappings.map((mapping) => {
        const spot = selectShortAnswerSpot(mapping, spots, spotById);
        const solution = selectShortAnswerSolution(mapping, solutions, solutionById);
        const shortAnswerMapping = new ShortAnswerMapping(spot, solution);
        shortAnswerMapping.id = mapping.id;
        shortAnswerMapping.shortAnswerSpotIndex = mapping.shortAnswerSpotIndex;
        shortAnswerMapping.shortAnswerSolutionIndex = mapping.shortAnswerSolutionIndex;
        shortAnswerMapping.invalid = mapping.invalid ?? false;
        shortAnswerMapping.question = question;
        return shortAnswerMapping;
    });
    return mapped.length > 0 ? mapped : undefined;
};

const selectShortAnswerSpot = (
    mapping: ShortAnswerMappingSnapshotDTO,
    spots: ShortAnswerSpot[],
    spotById: Map<number | undefined, ShortAnswerSpot>,
): ShortAnswerSpot | undefined => {
    if (mapping.shortAnswerSpotIndex != undefined && spots[mapping.shortAnswerSpotIndex]) {
        return spots[mapping.shortAnswerSpotIndex];
    }
    if (mapping.spot?.id != undefined) {
        return spotById.get(mapping.spot.id);
    }
    return undefined;
};

const selectShortAnswerSolution = (
    mapping: ShortAnswerMappingSnapshotDTO,
    solutions: ShortAnswerSolution[],
    solutionById: Map<number | undefined, ShortAnswerSolution>,
): ShortAnswerSolution | undefined => {
    if (mapping.shortAnswerSolutionIndex != undefined && solutions[mapping.shortAnswerSolutionIndex]) {
        return solutions[mapping.shortAnswerSolutionIndex];
    }
    if (mapping.solution?.id != undefined) {
        return solutionById.get(mapping.solution.id);
    }
    return undefined;
};
