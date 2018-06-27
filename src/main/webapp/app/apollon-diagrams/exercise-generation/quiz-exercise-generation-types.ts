import { Course } from '../../entities/course';

//TODO: all these types already exist in other places, so better reuse them instead of defining them again

export interface DragAndDropQuizExercise {
    type: 'quiz-exercise';
    course: Course;
    title: string;
    duration: number;
    isVisibleBeforeStart: boolean;
    isOpenForPractice: boolean;
    isPlannedToStart: boolean;
    releaseDate: Date;
    randomizeQuestionOrder: boolean;
    questions: DragAndDropQuestion[];
}

export interface DragAndDropQuestion {
    type: 'drag-and-drop';
    title: string;
    text: string;
    scoringType: 'ALL_OR_NOTHING';
    randomizeOrder: boolean;
    score: number;
    dropLocations: DropLocation[];
    dragItems: DragItem[];
    correctMappings: DragAndDropMapping[];
    backgroundFilePath: string;
}

export interface DropLocation {
    tempID: number;
    posX: number;
    posY: number;
    width: number;
    height: number;
}

export interface TextDragItem {
    tempID: number;
    text: string;
}

export interface ImageDragItem {
    tempID: number;
    pictureFilePath: string;
}

export type DragItem = TextDragItem | ImageDragItem;

export interface DragAndDropMapping {
    dropLocation: DropLocation;
    dragItem: DragItem;
}
