import { IconProp } from '@fortawesome/fontawesome-svg-core';
import { DifficultyLevel, Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import dayjs from 'dayjs/esm';
import { ConversationDTO } from 'app/communication/shared/entities/conversation/conversation.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';

export type SidebarCardSize = 'S' | 'M' | 'L';
export type TimeGroupCategory = 'past' | 'current' | 'dueSoon' | 'future' | 'noDate';
export type ExamGroupCategory = 'real' | 'test' | 'attempt';
export type TutorialGroupCategory = 'allGroups' | 'registeredGroups' | 'furtherGroups' | 'allTutorialLectures' | 'currentTutorialLecture' | 'furtherTutorialLectures';
export type SidebarTypes = 'exercise' | 'exam' | 'inExam' | 'conversation' | 'default';
export type AccordionGroups = Record<
    TimeGroupCategory | TutorialGroupCategory | ExamGroupCategory | ChannelGroupCategory | string,
    { entityData: SidebarCardElement[]; isHideCount?: boolean }
>;
export type ChannelGroupCategory =
    | 'unreadMessages'
    | 'favoriteChannels'
    | 'recents'
    | 'generalChannels'
    | 'exerciseChannels'
    | 'lectureChannels'
    | 'groupChats'
    | 'directMessages'
    | 'examChannels'
    | 'feedbackDiscussion'
    | 'savedPosts'
    | 'archivedChannels';
export type CollapseState = {
    [key: string]: boolean;
} & (Record<TimeGroupCategory, boolean> | Record<ChannelGroupCategory, boolean> | Record<ExamGroupCategory, boolean> | Record<TutorialGroupCategory, boolean>);
export type ChannelTypeIcons = Record<ChannelGroupCategory, IconProp>;
export type SidebarItemShowAlways = {
    [key: string]: boolean;
} & (Record<TimeGroupCategory, boolean> | Record<ChannelGroupCategory, boolean> | Record<ExamGroupCategory, boolean> | Record<TutorialGroupCategory, boolean>);

export interface SidebarData {
    groupByCategory: boolean;
    sidebarType?: SidebarTypes;
    groupedData?: AccordionGroups;
    ungroupedData?: SidebarCardElement[];
    storageId?: string;
    showAccordionLeadingIcon?: boolean;
    messagingEnabled?: boolean;
    canCreateChannel?: boolean;
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
     * The subroute under which the component should be rendered that is opened once this sidebar card element is clicked
     */
    targetComponentSubRoute?: string;
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
     * Set For Exam, this is a string which may define an icon for the status of the exam.
     * It has to be a valid FontAwesome icon name and will be displayed in the
     * 'regular' style. Needed for future implementation
     */
    statusIcon?: IconProp;
    /**
     * Set for Exam, identifies the color of the status icon. Needed for future implementation
     */
    statusIconColor?: string;
    /**
     * Set for Exam, shows the start date and time
     */
    startDateWithTime?: dayjs.Dayjs;
    /**
     * Set for Exam, shows the working time
     */
    workingTime?: number;
    /**
     * Set for Exam, represents the student exam of a real exam to obtain individual working time
     */
    studentExam?: StudentExam;
    /**
     * Set for Exam, shows the maximum attainable Points
     */
    attainablePoints?: number;
    /**
     * Set for Exam, identifies the current status of an exam exercise for exam sidebar
     */
    rightIcon?: IconProp;
    /**
     * Set for Exam, identifies if it is a test exam attempt
     */
    isAttempt?: boolean;
    /**
     * Set For Exam, identifies the number of attempts for each test exam
     */
    attempts?: number;
    /**
     * Set For Exam, identifies if it is a test exam
     */
    testExam?: boolean;
    /**
     * Set For Exam, identifies the submission date for an attempt of a test exam
     */
    submissionDate?: dayjs.Dayjs;
    /**
     * Set For Exam, identifies used working time of a student for an attempt of a test exam
     */
    usedWorkingTime?: number;
    /**
     * Set for Conversation. Will be removed after refactoring
     */
    conversation?: ConversationDTO;
    /**
     * Set for Lectures, shows the start date
     */
    startDate?: dayjs.Dayjs;

    isCurrent?: boolean;

    attendanceText?: string;

    attendanceChipColor?: string;

    /**
     * Optional nested cards. When set, this card acts as a header for a group of exercises (e.g. a
     * course-level exercise group) and the nested cards are rendered indented underneath it. Existing
     * sidebars do not set this, so their rendering is unaffected.
     */
    groupedItems?: SidebarCardElement[];

    /**
     * How the group header is rendered when {@link groupedItems} is set: 'card' (default) shows the
     * header as a normal sidebar card/tile; 'label' shows just the title (with icon and subtitle) as a
     * plain, non-clickable heading.
     */
    groupHeaderStyle?: 'card' | 'label';

    /**
     * Optional helper line shown below a 'label' group title (e.g. "Pick 1 of 3"). Only rendered when set.
     */
    groupPickHint?: string;

    /**
     * When true on a group header, each nested exercise gets a checkbox for single-select (max one
     * selected per group). Only meaningful together with {@link groupedItems}.
     */
    groupSelectable?: boolean;

    /**
     * Adds a clickable affordance (pointer + hover) to a 'label' group: 'heading' highlights only the
     * title row, 'group' highlights the whole group container. Only meaningful with {@link groupedItems}.
     */
    groupClickable?: 'heading' | 'group';

    /**
     * When true, the group renders as one connected stack of tiles (header tile + flush exercise tiles)
     * with only the outer corners rounded, no padding and no indent. Pair with {@link groupHeaderStyle}
     * 'card'. Only meaningful with {@link groupedItems}.
     */
    groupConnected?: boolean;

    /** Optional icon shown before {@link subtitleLeft} (and the group hint), e.g. a warning triangle. */
    subtitleLeftIcon?: IconProp;

    /** Optional CSS class(es) applied to {@link subtitleLeft} (and the group hint), e.g. 'text-warning'. */
    subtitleLeftClass?: string;

    /** Optional tooltip (native title) for {@link subtitleLeft} (and the group hint). */
    subtitleLeftTooltip?: string;

    /** Whether this (nested) exercise is currently selected within its group. */
    selected?: boolean;
}
