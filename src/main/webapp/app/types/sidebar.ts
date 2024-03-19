import { Exercise } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';

export type TimeGroupCategory = 'past' | 'current' | 'future' | 'noDueDate';
export type SidebarTypes = 'exercise' | 'lecture' | 'default';

export type ExerciseGroups = Record<TimeGroupCategory, { entityData: Exercise[] }>;
export type LectureGroups = Record<TimeGroupCategory, { entityData: Lecture[] }>;
export type DefaultGroups = Record<TimeGroupCategory, { entityData: BaseCardElement[] }>;
export type ExerciseCollapseState = Record<TimeGroupCategory, boolean>;
// TODO
export type ungroupedDataArrayTypes = Exercise[] | Lecture[] | BaseCardElement[];

export interface SidebarData {
    groupByCategory: boolean;
    sidebarType?: SidebarTypes;
    groupedData?: GroupTypes;
    ungroupedData?: ungroupedDataArrayTypes;
    storageId?: string;
}

export interface SidebarAccordionData extends SidebarData {
    // + groupedData,
    // accordionHEadings
    // AccordionCollapseState
    // CdkAccordion
}

export type GroupTypes = ExerciseGroups | LectureGroups | DefaultGroups;
export type CardItemType = 'Exercise' | 'Lecture' | 'Default' | 'Communication';

export interface BaseCardElement {
    /**
     * Defines the item's title that will be shown in the card
     */
    title: string;
    /**
     * Defines the item's id that will be used to search for selected
     */
    id?: string;
    /**
     * If set to true, the icons for quick actions will be displayed on the top right
     */
    quickActionIcons?: any;
    /**
     * If set to true, a subtitle will be displayed on left side
     */
    subtileLeft?: string;
    /**
     * If set to true, a subtitle will be displayed on right side
     */
    subtitleRight?: string;
    /**
     * If set to true, the item will be displayed as active and, thus, overwrites
     * the routerLinkActive flag.
     */
    active?: boolean;
    /**
     * Sets a router link for the nav item which will be activated by clicking
     * the item.
     */
    routerLink?: string;
}

export interface BaseCardItem {
    type: CardItemType;
}
// wenn groupedByGategory

// CardItemsDefault
// title
// showIcons if true, (topright)
// suntitleLeft (belowTitle)
// subtileRight
// routerLink
// active
// action
//
// Exercise extra ??
// Exam has extra Card!

export interface ExerciseCardItem extends BaseCardItem {
    type: 'Exercise';
}
