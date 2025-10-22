import { Injectable, inject } from '@angular/core';
import { IconDefinition } from '@fortawesome/fontawesome-svg-core';
import { faBookmark, faBoxArchive, faBullhorn, faGraduationCap, faHashtag, faLock, faSquareCheck } from '@fortawesome/free-solid-svg-icons';
import { TranslateService } from '@ngx-translate/core';
import { ConversationService } from 'app/communication/conversations/service/conversation.service';
import { ChannelSubType, getAsChannelDTO } from 'app/communication/shared/entities/conversation/channel.model';
import { ConversationDTO, ConversationType } from 'app/communication/shared/entities/conversation/conversation.model';
import { isGroupChatDTO } from 'app/communication/shared/entities/conversation/group-chat.model';
import { isOneToOneChatDTO } from 'app/communication/shared/entities/conversation/one-to-one-chat.model';
import { SavedPostStatus } from 'app/communication/shared/entities/posting.model';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exam } from 'app/exam/shared/entities/exam.model';
import { StudentExam } from 'app/exam/shared/entities/student-exam.model';
import { getExerciseDueDate } from 'app/exercise/util/exercise.utils';
import { ParticipationService } from 'app/exercise/participation/participation.service';
import { Exercise, getIcon } from 'app/exercise/shared/entities/exercise/exercise.model';
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';
import { Lecture } from 'app/lecture/shared/entities/lecture.model';
import { AccordionGroups, ChannelGroupCategory, SidebarCardElement, TimeGroupCategory } from 'app/shared/types/sidebar';
import { TutorialGroup } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import dayjs from 'dayjs/esm';
import { cloneDeep } from 'lodash-es';
import { LocalStorageService } from 'app/shared/service/local-storage.service';

const DEFAULT_UNIT_GROUPS: AccordionGroups = {
    future: { entityData: [] },
    dueSoon: { entityData: [] },
    current: { entityData: [] },
    past: { entityData: [] },
    noDate: { entityData: [] },
};

type StartDateGroup = 'none' | 'past' | 'future';
type EndDateGroup = StartDateGroup | 'soon';

/**
 * Decides which time category group an exercise should be put into based on its start and end dates.
 */
const GROUP_DECISION_MATRIX: Record<StartDateGroup, Record<EndDateGroup, TimeGroupCategory>> = {
    none: {
        none: 'noDate',
        past: 'past',
        soon: 'dueSoon',
        future: 'current',
    },
    past: {
        none: 'noDate',
        past: 'past',
        soon: 'dueSoon',
        future: 'current',
    },
    future: {
        none: 'future',
        past: 'future',
        soon: 'future',
        future: 'future',
    },
};

const DEFAULT_CHANNEL_GROUPS: AccordionGroups = {
    favoriteChannels: { entityData: [] },
    recents: { entityData: [] },
    generalChannels: { entityData: [] },
    exerciseChannels: { entityData: [] },
    lectureChannels: { entityData: [] },
    examChannels: { entityData: [] },
    feedbackDiscussion: { entityData: [] },
    archivedChannels: { entityData: [] },
};

@Injectable({
    providedIn: 'root',
})
export class CourseOverviewService {
    private participationService = inject(ParticipationService);
    private translate = inject(TranslateService);
    private conversationService = inject(ConversationService);
    private localStorageService = inject(LocalStorageService);

    readonly faBullhorn = faBullhorn;
    readonly faHashtag = faHashtag;
    readonly faLock = faLock;

    getUpcomingTutorialGroup(tutorialGroups: TutorialGroup[] | undefined): TutorialGroup | undefined {
        if (!tutorialGroups?.length) {
            return undefined;
        }
        const now = dayjs();
        const futureGroups = tutorialGroups.filter((group) => {
            const start = group.nextSession?.start;
            return start?.isAfter(now);
        });
        if (!futureGroups.length) {
            return undefined;
        }
        return futureGroups.reduce((earliest, current) => {
            const earliestStart = earliest.nextSession!.start!;
            const currentStart = current.nextSession!.start!;
            return currentStart.isBefore(earliestStart) ? current : earliest;
        });
    }

    getUpcomingLecture(lectures: Lecture[] | undefined): Lecture | undefined {
        if (lectures && lectures.length) {
            return lectures?.reduce((a, b) => ((a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b));
        }
    }

    getUpcomingExam(exams: Exam[] | undefined): Exam | undefined {
        if (exams && exams.length) {
            return exams?.reduce((a, b) => ((a?.startDate?.valueOf() ?? 0) > (b?.startDate?.valueOf() ?? 0) ? a : b));
        }
        return undefined;
    }

    getUpcomingExercise(exercises: Exercise[] | undefined): Exercise | undefined {
        if (exercises && exercises.length) {
            return exercises?.reduce((a, b) => ((a?.dueDate?.valueOf() ?? 0) > (b?.dueDate?.valueOf() ?? 0) ? a : b));
        }
    }

    getCorrespondingExerciseGroupByDate(exercise: Exercise): TimeGroupCategory {
        const now = dayjs();

        const startGroup = this.getStartDateGroup(exercise, now);
        const endGroup = this.getEndDateGroup(exercise, now);

        return GROUP_DECISION_MATRIX[startGroup][endGroup];
    }

    private getStartDateGroup(exercise: Exercise, now: dayjs.Dayjs): StartDateGroup {
        const start = exercise.startDate ?? exercise.releaseDate;

        if (start === undefined) {
            return 'none';
        }

        if (now.isAfter(dayjs(start))) {
            return 'past';
        }

        return 'future';
    }

    private getEndDateGroup(exercise: Exercise, now: dayjs.Dayjs): EndDateGroup {
        const dueDate = exercise.dueDate ? dayjs(exercise.dueDate) : undefined;

        if (dueDate === undefined) {
            return 'none';
        }

        if (now.isAfter(dueDate)) {
            return 'past';
        }

        const dueDateIsSoon = dueDate.isBefore(now.add(3, 'days'));
        if (dueDateIsSoon) {
            return 'soon';
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

        const isDateCurrent = endDate ? now.isBetween(startDate, endDate, undefined, '[]') : isStartDateWithinLastWeek;
        if (isDateCurrent) {
            return 'current';
        }
        return 'future';
    }

    getConversationGroup(conversation: ConversationDTO): ChannelGroupCategory[] {
        const groups: ChannelGroupCategory[] = [];

        if (conversation.isHidden) {
            groups.push('archivedChannels');
            return groups;
        }

        if (conversation.isFavorite) {
            groups.push('favoriteChannels');
        }

        if (isGroupChatDTO(conversation)) {
            groups.push('groupChats');
        } else if (isOneToOneChatDTO(conversation)) {
            groups.push('directMessages');
        } else {
            const subTypeGroup = this.getCorrespondingChannelSubType(getAsChannelDTO(conversation)?.subType);
            groups.push(subTypeGroup);
        }

        return groups;
    }

    getCorrespondingChannelSubType(channelSubType: ChannelSubType | undefined): ChannelGroupCategory {
        const channelSubTypeMap: { [key in ChannelSubType]: ChannelGroupCategory } = {
            [ChannelSubType.EXERCISE]: 'exerciseChannels',
            [ChannelSubType.GENERAL]: 'generalChannels',
            [ChannelSubType.LECTURE]: 'lectureChannels',
            [ChannelSubType.EXAM]: 'examChannels',
            [ChannelSubType.FEEDBACK_DISCUSSION]: 'feedbackDiscussion',
        };
        return channelSubType ? channelSubTypeMap[channelSubType] : 'generalChannels';
    }

    groupExercisesByDueDate(sortedExercises: Exercise[]): AccordionGroups {
        const groupedExerciseGroups = cloneDeep(DEFAULT_UNIT_GROUPS) as AccordionGroups;

        for (const exercise of sortedExercises) {
            const exerciseGroup = this.getCorrespondingExerciseGroupByDate(exercise);
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

    groupConversationsByChannelType(course: Course, conversations: ConversationDTO[], messagingEnabled: boolean): AccordionGroups {
        const channelGroups = messagingEnabled ? { ...DEFAULT_CHANNEL_GROUPS, groupChats: { entityData: [] }, directMessages: { entityData: [] } } : DEFAULT_CHANNEL_GROUPS;
        const groupedConversationGroups = cloneDeep(channelGroups) as AccordionGroups;

        groupedConversationGroups.savedPosts = {
            isHideCount: true,
            entityData: [
                {
                    title: this.translate.instant('artemisApp.courseOverview.sidebar.progress'),
                    id: SavedPostStatus.IN_PROGRESS.toString().toLowerCase(),
                    type: ConversationType.CHANNEL,
                    icon: faBookmark,
                    size: 'S',
                },
                {
                    title: this.translate.instant('artemisApp.courseOverview.sidebar.completed'),
                    id: SavedPostStatus.COMPLETED.toString().toLowerCase(),
                    type: ConversationType.CHANNEL,
                    icon: faSquareCheck,
                    size: 'S',
                },
                {
                    title: this.translate.instant('artemisApp.courseOverview.sidebar.archived'),
                    id: SavedPostStatus.ARCHIVED.toString().toLowerCase(),
                    type: ConversationType.CHANNEL,
                    icon: faBoxArchive,
                    size: 'S',
                },
            ],
        };

        for (const conversation of conversations) {
            const conversationGroups = this.getConversationGroup(conversation);
            const conversationCardItem = this.mapConversationToSidebarCardElement(course, conversation);

            // Add the conversation card to all applicable sidebar groups
            for (const group of conversationGroups) {
                groupedConversationGroups[group].entityData.push(conversationCardItem);
            }
        }

        for (const group in groupedConversationGroups) {
            // Sort conversations within each group so that favorites are shown on top
            groupedConversationGroups[group].entityData.sort((a, b) => {
                const aIsFavorite = a.conversation?.isFavorite ? 1 : 0;
                const bIsFavorite = b.conversation?.isFavorite ? 1 : 0;
                return bIsFavorite - aIsFavorite;
            });
        }
        return groupedConversationGroups;
    }

    mapLecturesToSidebarCardElements(lectures: Lecture[]) {
        return lectures.map((lecture) => this.mapLectureToSidebarCardElement(lecture));
    }
    mapTutorialGroupsToSidebarCardElements(tutorialGroups: TutorialGroup[]) {
        return tutorialGroups.map((tutorialGroup) => this.mapTutorialGroupToSidebarCardElement(tutorialGroup));
    }

    mapExercisesToSidebarCardElements(exercises: Exercise[]) {
        return exercises.map((exercise) => this.mapExerciseToSidebarCardElement(exercise));
    }
    mapExamsToSidebarCardElements(exams: Exam[], studentExams?: StudentExam[]) {
        return exams.map((exam, index) => this.mapExamToSidebarCardElement(exam, studentExams?.[index]));
    }

    /**
     * Maps an array of conversations to their respective sidebar card representations.
     * This is used to display conversation cards (channels, group chats, etc.) in the sidebar.
     *
     * @param course - The course to which the conversations belong
     * @param conversations - The conversations to be mapped
     * @returns An array of SidebarCardElement objects
     */
    mapConversationsToSidebarCardElements(course: Course, conversations: ConversationDTO[]) {
        return conversations.map((conversation) => this.mapConversationToSidebarCardElement(course, conversation));
    }

    mapTestExamAttemptsToSidebarCardElements(attempts?: StudentExam[], indices?: number[]) {
        if (attempts && indices) {
            return attempts.map((attempt, index) => this.mapAttemptToSidebarCardElement(attempt, index));
        }
    }

    mapLectureToSidebarCardElement(lecture: Lecture): SidebarCardElement {
        return {
            title: lecture.title ?? '',
            id: lecture.id ?? '',
            subtitleLeft: lecture.startDate?.format('MMM DD, YYYY') ?? this.translate.instant('artemisApp.courseOverview.sidebar.noDate'),
            size: 'M',
            startDate: lecture.startDate,
        };
    }

    mapTutorialGroupToSidebarCardElement(tutorialGroup: TutorialGroup): SidebarCardElement {
        const [attendanceText, averageAttendanceRatio] = this.computeAttendanceChipData(tutorialGroup);
        const attendanceChipColor = this.computeAttendanceChipColor(averageAttendanceRatio);
        const [subtitleLeft, subtitleRight] = this.computeTutorialSidebarCardSubtitles(tutorialGroup.nextSession?.start, tutorialGroup.nextSession?.end);
        return {
            title: tutorialGroup.title ?? '',
            id: tutorialGroup.id ?? '',
            size: 'M',
            subtitleLeft: subtitleLeft,
            subtitleRight: subtitleRight,
            attendanceText: attendanceText,
            attendanceChipColor: attendanceChipColor,
        };
    }

    private computeAttendanceChipData(tutorialGroup: TutorialGroup): [attendanceText?: string, averageAttendanceRatio?: number] {
        const capacity = tutorialGroup.capacity;
        if (capacity === undefined) {
            return [undefined, undefined];
        }
        let averageAttendanceRatio: number | undefined = undefined;
        let attendanceText: string | undefined = undefined;
        const sessionsWithAttendance = tutorialGroup.tutorialGroupSessions?.filter((session) => session.attendanceCount !== undefined && session.attendanceCount !== null) ?? [];
        if (sessionsWithAttendance.length !== 0) {
            const averageAttendance = sessionsWithAttendance.reduce((sum, session) => sum + session.attendanceCount!, 0) / sessionsWithAttendance.length;
            averageAttendanceRatio = averageAttendance / capacity;
            attendanceText = `Ø ${(averageAttendanceRatio * 100).toFixed(0)}%`;
            return [attendanceText, averageAttendanceRatio];
        } else {
            const numberOfRegisteredUsers = tutorialGroup.numberOfRegisteredUsers;
            if (numberOfRegisteredUsers) {
                averageAttendanceRatio = numberOfRegisteredUsers / capacity;
                attendanceText = numberOfRegisteredUsers + ' / ' + capacity;
                return [attendanceText, averageAttendanceRatio];
            }
        }
        return [undefined, undefined];
    }

    private computeAttendanceChipColor(averageAttendanceRatio?: number): string | undefined {
        if (averageAttendanceRatio === undefined) {
            return undefined;
        }
        if (averageAttendanceRatio >= 0.9) {
            return 'var(--red)';
        } else if (averageAttendanceRatio >= 0.8) {
            return 'var(--orange)';
        } else if (averageAttendanceRatio >= 0.7) {
            return 'var(--yellow)';
        } else {
            return 'var(--green)';
        }
    }

    private computeTutorialSidebarCardSubtitles(start?: dayjs.Dayjs, end?: dayjs.Dayjs): [string?, string?] {
        const subtitleLeft = start?.format('MMM DD, YYYY');
        if (!subtitleLeft) {
            return [this.translate.instant('artemisApp.courseOverview.sidebar.noUpcomingSession'), undefined];
        }
        if (start && end) {
            return [subtitleLeft, start.format('HH:mm') + '–' + end.format('HH:mm')];
        }
        return [undefined, undefined];
    }

    mapExerciseToSidebarCardElement(exercise: Exercise): SidebarCardElement {
        return {
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
    }

    mapExamToSidebarCardElement(exam: Exam, studentExam?: StudentExam, numberOfAttempts?: number): SidebarCardElement {
        return {
            title: exam.title ?? '',
            id: exam.id ?? '',
            icon: faGraduationCap,
            subtitleLeft: exam.moduleNumber ?? '',
            startDateWithTime: exam.startDate,
            workingTime: exam.workingTime,
            studentExam: studentExam,
            attainablePoints: exam.examMaxPoints ?? 0,
            size: 'L',
            isAttempt: false,
            testExam: exam.testExam,
            attempts: numberOfAttempts ?? 0,
        };
    }

    mapAttemptToSidebarCardElement(attempt: StudentExam, index: number): SidebarCardElement {
        return {
            title: attempt.exam!.title ?? '',
            id: attempt.exam!.id + '/test-exam/' + attempt.id,
            icon: faGraduationCap,
            subtitleLeft: this.translate.instant('artemisApp.courseOverview.sidebar.testExamAttempt') + ' ' + index,
            submissionDate: attempt.submissionDate,
            usedWorkingTime: this.calculateUsedWorkingTime(attempt),
            size: 'L',
            isAttempt: true,
        };
    }

    /**
     * Returns the appropriate FontAwesome icon for a given conversation.
     * - Announcement channels get a bullhorn icon.
     * - Public channels get a hashtag icon.
     * - Private channels get a lock icon.
     *
     * @param conversation - The conversation to determine the icon for
     * @returns A FontAwesome IconDefinition
     */
    getChannelIcon(conversation: ConversationDTO): IconDefinition {
        const channelDTO = getAsChannelDTO(conversation);
        if (channelDTO?.isAnnouncementChannel) {
            return this.faBullhorn;
        } else if (channelDTO?.isPublic) {
            return this.faHashtag;
        } else {
            return this.faLock;
        }
    }

    /**
     * Maps a conversation (e.g., channel, group chat, direct message) to a SidebarCardElement.
     * This includes metadata like title, icon, type, and whether it's currently active ("isCurrent").
     * The "isCurrent" flag is determined based on relevant dates (like due date or start date)
     * of the associated exercise/lecture/exam being within ±1.5 weeks from today.
     *
     * @param course - The course to which the conversation belongs
     * @param conversation - The conversation to map
     * @returns A SidebarCardElement representing the conversation
     */
    mapConversationToSidebarCardElement(course: Course, conversation: ConversationDTO): SidebarCardElement {
        let isCurrent = false;
        const channelDTO = getAsChannelDTO(conversation);
        const subTypeRefId = channelDTO?.subTypeReferenceId;
        const now = dayjs();
        const oneAndHalfWeekBefore = now.subtract(1.5, 'week');
        const oneAndHalfWeekLater = now.add(1.5, 'week');
        let relevantDate = null;

        // Determine relevance of conversation based on associated exercise, lecture, or exam
        if (subTypeRefId && course.exercises && channelDTO?.subType === 'exercise') {
            const exercise = course.exercises.find((exercise) => exercise.id === subTypeRefId);
            const relevantDates = [exercise?.releaseDate, exercise?.dueDate].filter(Boolean);
            // If any date is within ±1.5 weeks, mark as current
            isCurrent = relevantDates.some((date) => dayjs(date).isBetween(oneAndHalfWeekBefore, oneAndHalfWeekLater, 'day', '[]'));
        } else if (subTypeRefId && course.lectures && channelDTO?.subType === 'lecture') {
            const lecture = course.lectures.find((lecture) => lecture.id === subTypeRefId);
            relevantDate = lecture?.startDate || null;
            isCurrent = relevantDate ? dayjs(relevantDate).isBetween(oneAndHalfWeekBefore, oneAndHalfWeekLater, 'day', '[]') : false;
        } else if (subTypeRefId && course.exams && channelDTO?.subType === 'exam') {
            const exam = course.exams.find((exam) => exam.id === subTypeRefId);
            relevantDate = exam?.startDate || null;
            isCurrent = relevantDate ? dayjs(relevantDate).isBetween(oneAndHalfWeekBefore, oneAndHalfWeekLater, 'day', '[]') : false;
        }

        return {
            title: this.conversationService.getConversationName(conversation) ?? '',
            id: conversation.id ?? '',
            type: conversation.type,
            icon: this.getChannelIcon(conversation),
            conversation: conversation,
            size: 'S',
            isCurrent: isCurrent,
        };
    }

    sortLectures(lectures: Lecture[]): Lecture[] {
        return lectures.sort((a, b) => {
            const startDateA = a.startDate ? a.startDate.valueOf() : dayjs().valueOf();
            const startDateB = b.startDate ? b.startDate.valueOf() : dayjs().valueOf();
            // If Due Date is identical or undefined sort by title
            return startDateB - startDateA !== 0 ? startDateB - startDateA : this.sortByTitle(a, b);
        });
    }

    sortExercises(exercises: Exercise[]): Exercise[] {
        return exercises?.sort((a, b) => {
            const dueDateA = getExerciseDueDate(a, this.studentParticipation(a))?.valueOf() ?? 0;
            const dueDateB = getExerciseDueDate(b, this.studentParticipation(b))?.valueOf() ?? 0;
            // If Due Date is identical or undefined sort by title
            return dueDateB - dueDateA !== 0 ? dueDateB - dueDateA : this.sortByTitle(a, b);
        });
    }
    studentParticipation(exercise: Exercise): StudentParticipation | undefined {
        return exercise.studentParticipations?.length ? exercise.studentParticipations[0] : undefined;
    }

    sortByTitle(a: Exercise | Lecture | Exam, b: Exercise | Lecture | Exam): number {
        return a.title && b.title ? a.title.localeCompare(b.title) : 0;
    }

    getSidebarCollapseStateFromStorage(storageId: string): boolean {
        const storedCollapseState: boolean | undefined = this.localStorageService.retrieve<boolean>('sidebar.collapseState.' + storageId);
        return storedCollapseState !== undefined ? storedCollapseState : false;
    }

    setSidebarCollapseState(storageId: string, isCollapsed: boolean) {
        this.localStorageService.store<boolean>('sidebar.collapseState.' + storageId, isCollapsed);
    }

    calculateUsedWorkingTime(studentExam: StudentExam): number {
        let usedWorkingTime = 0;
        if (studentExam.exam!.testExam && studentExam.started && studentExam.submitted && studentExam.workingTime && studentExam.startedDate && studentExam.submissionDate) {
            const regularExamDuration = studentExam.workingTime;
            // As students may submit during the grace period, the workingTime is limited to the regular exam duration
            usedWorkingTime = Math.min(regularExamDuration, dayjs(studentExam.submissionDate).diff(dayjs(studentExam.startedDate), 'seconds'));
        }
        return usedWorkingTime;
    }
}
