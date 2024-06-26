import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { DifficultyLevel, Exercise } from 'app/entities/exercise.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';

export type SidebarCardSize = 'S' | 'M' | 'L';
export type TimeGroupCategory = 'past' | 'current' | 'future' | 'noDate';
export type TutorialGroupCategory = 'all' | 'registered' | 'further';
export type ChannelGroupCategory =
    | 'favoriteChannels'
    | 'generalChannels'
    | 'exerciseChannels'
    | 'lectureChannels'
    | 'groupChats'
    | 'directMessages'
    | 'examChannels'
    | 'hiddenChannels';
export type SidebarTypes = 'exercise' | 'conversation' | 'default';

export type AccordionGroups = Record<TimeGroupCategory | TutorialGroupCategory | ChannelGroupCategory | string, { entityData: SidebarCardElement[] }>;
export type CollapseState = Record<TimeGroupCategory, boolean> | Record<ChannelGroupCategory, boolean> | Record<TutorialGroupCategory, boolean>;
export type ChannelAccordionShowAdd = Record<ChannelGroupCategory, boolean>;
export type ChannelTypeIcons = Record<ChannelGroupCategory, IconProp>;

export interface SidebarData {
    groupByCategory: boolean;
    sidebarType?: SidebarTypes;
    groupedData?: AccordionGroups;
    ungroupedData?: SidebarCardElement[];
    storageId?: string;
    showAccordionAddOption?: boolean;
    showAccordionLeadingIcon?: boolean;
}

export interface SidebarCardElement {
    /**
     * Defines the item's title that will be shown in the card
     */
    title: string;
    /**
     * This is an optional string which may define an icon for the card item.
     * It has to be a valid FontAwesome icon name and will be displayed in the
     * 'regular' style.
     */
    icon?: IconProp;
    /**
     * Defines the item's id that will be used to search for selected
     */
    id: string | number;
    /**
     * If set to true, the icons for quick actions will be displayed on the top right
     */
    quickActionIcons?: any;
    /**
     * If set to true, a subtitle will be displayed on left side
     */
    subtitleLeft?: string;
    /**
     * If set to true, a subtitle will be displayed on right side, special case for exercises will be refactored
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

    // TODO Extra Exercise Part
    /**
     * Set for Exercises
     */
    type?: string;
    /**
     * Sets the size of SidebarCards
     */
    size: SidebarCardSize;
    /**
     * Set for Exercises, shows the colored border on the left side
     */
    difficulty?: DifficultyLevel;
    /**
     * Set for Exercises
     */
    studentParticipation?: StudentParticipation;
    /**
     * Set for Exercises. Will be removed after refactoring
     */
    exercise?: Exercise;
    /**
     * Set for Conversation. Will be removed after refactoring
     */
    conversation?: ConversationDTO;
}
