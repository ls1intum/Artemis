import { Injectable } from '@angular/core';
import { TranslateService } from '@ngx-translate/core';
import { Exercise, getIcon } from 'app/entities/exercise.model';
import { Lecture } from 'app/entities/lecture.model';
import { StudentParticipation } from 'app/entities/participation/student-participation.model';
import { TutorialGroup } from 'app/entities/tutorial-group/tutorial-group.model';
import { getExerciseDueDate } from 'app/exercises/shared/exercise/exercise.utils';
import { ParticipationService } from 'app/exercises/shared/participation/participation.service';
import { AccordionGroups, ChannelGroupCategory, SidebarCardElement, TimeGroupCategory } from 'app/types/sidebar';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';
import { ConversationDTO } from 'app/entities/metis/conversation/conversation.model';
import { ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';
import { faBullhorn, faHashtag } from '@fortawesome/free-solid-svg-icons';
import { isOneToOneChatDTO } from 'app/entities/metis/conversation/one-to-one-chat.model';
import { isGroupChatDTO } from 'app/entities/metis/conversation/group-chat.model';
import { ConversationService } from 'app/shared/metis/conversations/conversation.service';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

const CHANNEL_GROUPS_MESSAGING_ENABLED: AccordionGroups = {
    favoriteChannels: { entityData: [] },
    generalChannels: { entityData: [] },
    exerciseChannels: { entityData: [] },
    lectureChannels: { entityData: [] },
    examChannels: { entityData: [] },
    groupChats: { entityData: [] },
    directMessages: { entityData: [] },
    hiddenChannels: { entityData: [] },
};

const CHANNEL_GROUPS_MESSAGING_DISABLED: AccordionGroups = {
    favoriteChannels: { entityData: [] },
    generalChannels: { entityData: [] },
    exerciseChannels: { entityData: [] },
    lectureChannels: { entityData: [] },
    examChannels: { entityData: [] },
    groupChats: { entityData: [] },
};

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewService {
    constructor(
        private participationService: ParticipationService,
        private translate: TranslateService,
        private conversationService: ConversationService,
    ) {}

    faBullhorn = faBullhorn;
    faHashtag = faHashtag;

    getUpcomingTutorialGroup(tutorialGroups: TutorialGroup[] | undefined): TutorialGroup | undefined {
        if (tutorialGroups && tutorialGroups.length) {
            const upcomingTutorialGroup = tutorialGroups?.reduce((a, b) => ((a?.nextSession?.start?.valueOf() ?? 0) > (b?.nextSession?.start?.valueOf() ?? 0) ? a : b));
            return upcomingTutorialGroup;
        }
    }
    getUpcomingLecture(lectures: Lecture[] | undefined): Lecture | undefined {
        if (lectures && lectures.length) {
            const upcomingLecture = lectures?.reduce((a, b) => ((a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b));
            return upcomingLecture;
        }
    }
    getUpcomingExercise(exercises: Exercise[] | undefined): Exercise | undefined {
        if (exercises && exercises.length) {
            const upcomingLecture = exercises?.reduce((a, b) => ((a?.dueDate?.valueOf() ?? 0) > (b?.dueDate?.valueOf() ?? 0) ? a : b));
            return upcomingLecture;
        }
    }

    getCorrespondingExerciseGroupByDate(date: dayjs.Dayjs | undefined): TimeGroupCategory {
        if (!date) {
            return 'noDate';
        }

        const dueDate = dayjs(date);
        const now = dayjs();

        const dueDateIsInThePast = dueDate.isBefore(now);
        if (dueDateIsInThePast) {
            return 'past';
        }

        const dueDateIsWithinNextWeek = dueDate.isBefore(now.add(1, 'week'));
        if (dueDateIsWithinNextWeek) {
            return 'current';
        }

        return 'future';
    }

    getCorrespondingLectureGroupByDate(startDate: dayjs.Dayjs | undefined, endDate?: dayjs.Dayjs | undefined): TimeGroupCategory {
        if (!startDate) {
            return 'noDate';
        }

        const now = dayjs();
        const isStartDateWithinLastWeek = startDate.isBetween(now.subtract(1, 'week'), now);
        const isDateInThePast = endDate ? endDate.isBefore(now) : startDate.isBefore(now.subtract(1, 'week'));

        if (isDateInThePast) {
            return 'past';
        }

        const isDateCurrent = endDate ? startDate.isBefore(now) && endDate.isAfter(now) : isStartDateWithinLastWeek;
        if (isDateCurrent) {
            return 'current';
        }
        return 'future';
    }

    getConversationGroup(conversation: ConversationDTO): ChannelGroupCategory {
        if (conversation.isFavorite) {
            return 'favoriteChannels';
        }
        if (conversation.isHidden) {
            return 'hiddenChannels';
        }
        if (isGroupChatDTO(conversation)) {
            return 'groupChats';
        }
        if (isOneToOneChatDTO(conversation)) {
            return 'directMessages';
        }
        return this.getCorrespondingChannelSubType(getAsChannelDTO(conversation)?.subType);
    }

    getCorrespondingChannelSubType(channelSubType: ChannelSubType | undefined): ChannelGroupCategory {
        const channelSubTypeMap: { [key in ChannelSubType]: ChannelGroupCategory } = {
            [ChannelSubType.EXERCISE]: 'exerciseChannels',
            [ChannelSubType.GENERAL]: 'generalChannels',
            [ChannelSubType.LECTURE]: 'lectureChannels',
            [ChannelSubType.EXAM]: 'examChannels',
        };
        return channelSubType ? channelSubTypeMap[channelSubType] : 'generalChannels';
    }

    groupExercisesByDueDate(sortedExercises: Exercise[]): AccordionGroups {
        const groupedExerciseGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;

        for (const exercise of sortedExercises) {
            const exerciseGroup = this.getCorrespondingExerciseGroupByDate(exercise.dueDate);
            const exerciseCardItem = this.mapExerciseToSidebarCardElement(exercise);
            groupedExerciseGroups[exerciseGroup].entityData.push(exerciseCardItem);
        }

        return groupedExerciseGroups;
    }

    groupLecturesByStartDate(sortedLectures: Lecture[]): AccordionGroups {
        const groupedLectureGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;

        for (const lecture of sortedLectures) {
            const lectureGroup = this.getCorrespondingLectureGroupByDate(lecture.startDate, lecture?.endDate);
            const lectureCardItem = this.mapLectureToSidebarCardElement(lecture);
            groupedLectureGroups[lectureGroup].entityData.push(lectureCardItem);
        }

        return groupedLectureGroups;
    }

    groupConversationsByChannelType(conversations: ConversationDTO[], messagingEnabled: boolean): AccordionGroups {
        const channelGroups = messagingEnabled ? CHANNEL_GROUPS_MESSAGING_ENABLED : CHANNEL_GROUPS_MESSAGING_DISABLED;
        const groupedConversationGroups = cloneDeep(channelGroups) as AccordionGroups;

        for (const conversation of conversations) {
            const conversationGroup = this.getConversationGroup(conversation);
            const conversationCardItem = this.mapConversationToSidebarCardElement(conversation);
            groupedConversationGroups[conversationGroup].entityData.push(conversationCardItem);
        }

        return groupedConversationGroups;
    }

    mapLecturesToSidebarCardElements(lectures: Lecture[]) {
        return lectures.map((lecture) => this.mapLectureToSidebarCardElement(lecture));
    }
    mapTutorialGroupsToSidebarCardElements(tutorialGroups: Lecture[]) {
        return tutorialGroups.map((tutorialGroup) => this.mapTutorialGroupToSidebarCardElement(tutorialGroup));
    }

    mapExercisesToSidebarCardElements(exercises: Exercise[]) {
        return exercises.map((exercise) => this.mapExerciseToSidebarCardElement(exercise));
    }

    mapConversationsToSidebarCardElements(conversations: ConversationDTO[]) {
        return conversations.map((conversation) => this.mapConversationToSidebarCardElement(conversation));
    }

    mapLectureToSidebarCardElement(lecture: Lecture): SidebarCardElement {
        const lectureCardItem: SidebarCardElement = {
            title: lecture.title ?? '',
            id: lecture.id ?? '',
            subtitleLeft: lecture.startDate?.format('MMM DD, YYYY') ?? this.translate.instant('artemisApp.courseOverview.sidebar.noDate'),
            size: 'M',
        };
        return lectureCardItem;
    }
    mapTutorialGroupToSidebarCardElement(tutorialGroup: TutorialGroup): SidebarCardElement {
        const tutorialGroupCardItem: SidebarCardElement = {
            title: tutorialGroup.title ?? '',
            id: tutorialGroup.id ?? '',
            size: 'M',
            subtitleLeft: tutorialGroup.nextSession?.start?.format('MMM DD, YYYY') ?? this.translate.instant('artemisApp.courseOverview.sidebar.noUpcomingSession'),
            subtitleRight: this.getUtilization(tutorialGroup),
        };
        return tutorialGroupCardItem;
    }

    getUtilization(tutorialGroup: TutorialGroup): string {
        if (tutorialGroup.capacity && tutorialGroup.averageAttendance) {
            const utilization = Math.round((tutorialGroup.averageAttendance / tutorialGroup.capacity) * 100);
            return this.translate.instant('artemisApp.entities.tutorialGroup.utilization') + ': ' + utilization + '%';
        } else {
            return tutorialGroup?.averageAttendance ? 'Ø ' + this.translate.instant('artemisApp.entities.tutorialGroup.attendance') + ': ' + tutorialGroup.averageAttendance : '';
        }
    }

    mapExerciseToSidebarCardElement(exercise: Exercise): SidebarCardElement {
        const exerciseCardItem: SidebarCardElement = {
            title: exercise.title ?? '',
            id: exercise.id ?? '',
            subtitleLeft: exercise.dueDate?.format('MMM DD, YYYY') ?? this.translate.instant('artemisApp.courseOverview.sidebar.noDueDate'),
            type: exercise.type,
            icon: getIcon(exercise.type),
            difficulty: exercise.difficulty,
            exercise: exercise,
            studentParticipation: exercise?.studentParticipations?.length
                ? this.participationService.getSpecificStudentParticipation(exercise.studentParticipations, false)
                : undefined,
            size: 'M',
        };
        return exerciseCardItem;
    }

    mapConversationToSidebarCardElement(conversation: ConversationDTO): SidebarCardElement {
        const conversationCardItem: SidebarCardElement = {
            title: this.conversationService.getConversationName(conversation) ?? '',
            id: conversation.id ?? '',
            type: conversation.type,
            icon: getAsChannelDTO(conversation)?.name === 'announcement' ? this.faBullhorn : this.faHashtag,
            conversation: conversation,
            size: 'S',
        };
        return conversationCardItem;
    }

    sortLectures(lectures: Lecture[]): Lecture[] {
        const sortedLecturesByStartDate = lectures.sort((a, b) => {
            const startDateA = a.startDate ? a.startDate.valueOf() : dayjs().valueOf();
            const startDateB = b.startDate ? b.startDate.valueOf() : dayjs().valueOf();
            // If Due Date is identical or undefined sort by title
            return startDateB - startDateA !== 0 ? startDateB - startDateA : this.sortByTitle(a, b);
        });

        return sortedLecturesByStartDate;
    }

    sortExercises(exercises: Exercise[]): Exercise[] {
        const sortedExercisesByDueDate = exercises?.sort((a, b) => {
            const dueDateA = getExerciseDueDate(a, this.studentParticipation(a))?.valueOf() ?? 0;
            const dueDateB = getExerciseDueDate(b, this.studentParticipation(b))?.valueOf() ?? 0;
            // If Due Date is identical or undefined sort by title
            return dueDateB - dueDateA !== 0 ? dueDateB - dueDateA : this.sortByTitle(a, b);
        });

        return sortedExercisesByDueDate;
    }
    studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.length ? exercise.studentParticipations[0] : undefined;
    }

    sortByTitle(a: Exercise | Lecture, b: Exercise | Lecture): number {
        return a.title && b.title ? a.title.localeCompare(b.title) : 0;
    }

    getSidebarCollapseStateFromStorage(storageId: string): boolean {
        const storedCollapseState: string | null = localStorage.getItem('sidebar.collapseState.' + storageId);
        return storedCollapseState ? JSON.parse(storedCollapseState) : false;
    }

    setSidebarCollapseState(storageId: string, isCollapsed: boolean) {
        localStorage.setItem('sidebar.collapseState.' + storageId, JSON.stringify(isCollapsed));
    }
}
