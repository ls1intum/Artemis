import { TestBed } from '@angular/core/testing';
import { CourseOverviewService } from 'app/overview/course-overview.service';
import { ModelingExercise } from 'app/entities/modeling-exercise.model';
import { Exercise } from 'app/entities/exercise.model';
import { UMLDiagramType } from '@ls1intum/apollon';
import { Course } from 'app/entities/course.model';
import dayjs from 'dayjs/esm';
import { Lecture } from 'app/entities/lecture.model';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../../helpers/mocks/service/mock-translate.service';
import { LocalStorageService, SessionStorageService } from 'ngx-webstorage';
import { MockSyncStorage } from '../../helpers/mocks/service/mock-sync-storage.service';
import { TextExercise } from 'app/entities/text/text-exercise.model';
import { Exam } from 'app/entities/exam/exam.model';
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

describe('CourseOverviewService', () => {
    let service: CourseOverviewService;
    let pastExercise: Exercise;
    let dueSoonExercise: Exercise;
    let currentExercise: Exercise;
    let currentExerciseNoDueDate: Exercise;
    let futureExercise: Exercise;
    let futureExercise2: Exercise;
    let course: Course;
    let pastLecture: Lecture;
    let futureLecture: Lecture;
    let currentLecture: Lecture;
    let generalChannel: ChannelDTO;
    let examChannel: ChannelDTO;
    let exerciseChannel: ChannelDTO;
    let favoriteChannel: ChannelDTO;
    let hiddenChannel: ChannelDTO;
    let generalChannel2: ChannelDTO;

    beforeEach(() => {
        TestBed.configureTestingModule({
            imports: [HttpClientTestingModule],
            providers: [
                CourseOverviewService,
                { provide: TranslateService, useClass: MockTranslateService },
                { provide: SessionStorageService, useClass: MockSyncStorage },
                { provide: LocalStorageService, useClass: MockSyncStorage },
            ],
        });
        service = TestBed.inject(CourseOverviewService);
        localStorage.clear();
        const lastWeek = dayjs().subtract(1, 'week');
        const yesterday = dayjs().subtract(1, 'day');
        const tomorrow = dayjs().add(1, 'day');
        const nextWeeks = dayjs().add(2, 'week');

        course = new Course();
        course.id = 1;

        pastExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        pastExercise.dueDate = lastWeek;
        pastExercise.releaseDate = lastWeek;
        pastExercise.title = 'Past Exercise';

        dueSoonExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        dueSoonExercise.dueDate = tomorrow;
        dueSoonExercise.releaseDate = lastWeek;
        dueSoonExercise.title = 'DueSoon Exercise';

        currentExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        currentExercise.dueDate = nextWeeks;
        currentExercise.releaseDate = lastWeek;
        currentExercise.title = 'Current Exercise';

        currentExerciseNoDueDate = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        currentExerciseNoDueDate.dueDate = undefined;
        currentExerciseNoDueDate.releaseDate = lastWeek;
        currentExerciseNoDueDate.title = 'Current Exercise No Due Date';

        futureExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        futureExercise.dueDate = nextWeeks;
        futureExercise.releaseDate = tomorrow;
        futureExercise.title = 'Future Exercise';

        futureExercise2 = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        futureExercise2.dueDate = nextWeeks;
        futureExercise2.releaseDate = lastWeek;
        futureExercise2.startDate = tomorrow;
        futureExercise2.title = 'Future Exercise 2';

        pastLecture = new Lecture();
        pastLecture.id = 6;
        pastLecture.startDate = lastWeek;
        pastLecture.endDate = yesterday;
        pastLecture.title = 'Introduction to Computer Science';

        currentLecture = new Lecture();
        currentLecture.id = 4;
        currentLecture.startDate = yesterday;
        currentLecture.endDate = tomorrow;
        currentLecture.title = 'Algorithms';

        futureLecture = new Lecture();
        futureLecture.id = 8;
        futureLecture.startDate = tomorrow;
        futureLecture.title = 'Advanced Topics in Computer Science';

        generalChannel = new ChannelDTO();
        generalChannel.id = 11;
        generalChannel.name = 'General';
        generalChannel.subType = ChannelSubType.GENERAL;

        examChannel = new ChannelDTO();
        examChannel.id = 12;
        examChannel.name = 'exam-test';
        examChannel.subType = ChannelSubType.EXAM;

        exerciseChannel = new ChannelDTO();
        exerciseChannel.id = 13;
        exerciseChannel.name = 'exercise-test';
        exerciseChannel.subType = ChannelSubType.EXERCISE;

        favoriteChannel = new ChannelDTO();
        favoriteChannel.id = 14;
        favoriteChannel.name = 'fav-channel';
        favoriteChannel.subType = ChannelSubType.GENERAL;
        favoriteChannel.isFavorite = true;

        hiddenChannel = new ChannelDTO();
        hiddenChannel.id = 15;
        hiddenChannel.name = 'hidden-channel';
        hiddenChannel.subType = ChannelSubType.GENERAL;
        hiddenChannel.isHidden = true;

        generalChannel2 = new ChannelDTO();
        generalChannel2.id = 16;
        generalChannel2.name = 'General 2';
        generalChannel2.subType = ChannelSubType.GENERAL;
    });

    it('should return true if sidebar collapse state is stored as true in localStorage', () => {
        const storageId = 'testId';
        localStorage.setItem('sidebar.collapseState.' + storageId, JSON.stringify(true));

        expect(service.getSidebarCollapseStateFromStorage(storageId)).toBeTrue();
    });

    it('should return false if there is no stored sidebar collapse state in localStorage', () => {
        const storageId = 'testId';

        expect(service.getSidebarCollapseStateFromStorage(storageId)).toBeFalse();
    });

    it('should sort lectures by startDate and by title if startDates are equal or undefined', () => {
        const lectures = [futureLecture, pastLecture, currentLecture];
        const sortedLectures = service.sortLectures(lectures);

        expect(sortedLectures[0].id).toBe(futureLecture.id);
        expect(sortedLectures[1].id).toBe(currentLecture.id);
        expect(sortedLectures[2].id).toBe(pastLecture.id);
    });

    it('should sort exercises by dueDate and by title if dueDate are equal or undefined', () => {
        const exercises = [dueSoonExercise, pastExercise, futureExercise];
        const sortedExercises = service.sortExercises(exercises);

        expect(sortedExercises[0].id).toBe(futureExercise.id);
        expect(sortedExercises[1].id).toBe(dueSoonExercise.id);
        expect(sortedExercises[2].id).toBe(pastExercise.id);
    });

    it('should group lectures by start date and map to sidebar card elements', () => {
        const sortedLectures = [futureLecture, pastLecture, currentLecture];

        jest.spyOn(service, 'getCorrespondingLectureGroupByDate');

        jest.spyOn(service, 'mapLectureToSidebarCardElement');
        const groupedLectures = service.groupLecturesByStartDate(sortedLectures);

        expect(groupedLectures['current'].entityData).toHaveLength(1);
        expect(groupedLectures['past'].entityData).toHaveLength(1);
        expect(groupedLectures['future'].entityData).toHaveLength(1);
        expect(service.mapLectureToSidebarCardElement).toHaveBeenCalledTimes(3);
        expect(groupedLectures['future'].entityData[0].title).toBe('Advanced Topics in Computer Science');
        expect(groupedLectures['past'].entityData[0].title).toBe('Introduction to Computer Science');
        expect(groupedLectures['current'].entityData[0].title).toBe('Algorithms');
    });

    it('should return undefined if lectures array is undefined', () => {
        expect(service.getUpcomingLecture(undefined)).toBeUndefined();
    });

    it('should return undefined if lectures array is empty', () => {
        expect(service.getUpcomingLecture([])).toBeUndefined();
    });

    it('should handle all past lectures', () => {
        const pastLectures: Lecture[] = [
            { id: 1, title: 'Past Lecture 1', startDate: dayjs().subtract(2, 'day') },
            { id: 2, title: 'Past Lecture 2', startDate: dayjs().subtract(1, 'day') },
        ];
        const upcomingLecture = service.getUpcomingLecture(pastLectures);

        expect(upcomingLecture?.id).toBe(2);
    });

    it('should correctly identify the lecture furthest in the future', () => {
        const lectures: Lecture[] = [
            { id: 1, title: 'Past Lecture', startDate: dayjs().subtract(1, 'day') },
            { id: 2, title: 'Upcoming Lecture', startDate: dayjs().add(1, 'day') },
            { id: 3, title: 'Far Future Lecture', startDate: dayjs().add(2, 'weeks') },
        ];
        const upcomingLecture = service.getUpcomingLecture(lectures);

        expect(upcomingLecture?.id).toBe(3);
    });

    it('should group exercises by start date and map to sidebar card elements', () => {
        const sortedExercises = [futureExercise, pastExercise, dueSoonExercise, currentExercise, futureExercise2, currentExerciseNoDueDate];

        jest.spyOn(service, 'getCorrespondingExerciseGroupByDate');

        jest.spyOn(service, 'mapExerciseToSidebarCardElement');
        const groupedExercises = service.groupExercisesByDueDate(sortedExercises);

        expect(groupedExercises['current'].entityData).toHaveLength(1);
        expect(groupedExercises['dueSoon'].entityData).toHaveLength(1);
        expect(groupedExercises['past'].entityData).toHaveLength(1);
        expect(groupedExercises['future'].entityData).toHaveLength(2);
        expect(groupedExercises['noDate'].entityData).toHaveLength(1);
        expect(service.mapExerciseToSidebarCardElement).toHaveBeenCalledTimes(6);
        expect(groupedExercises['current'].entityData[0].title).toBe('Current Exercise');
        expect(groupedExercises['dueSoon'].entityData[0].title).toBe('DueSoon Exercise');
        expect(groupedExercises['past'].entityData[0].title).toBe('Past Exercise');
        expect(groupedExercises['future'].entityData[0].title).toBe('Future Exercise');
        expect(groupedExercises['future'].entityData[1].title).toBe('Future Exercise 2');
        expect(groupedExercises['noDate'].entityData[0].title).toBe('Current Exercise No Due Date');
    });

    describe.each([
        // no start date
        { start: undefined, end: undefined, group: 'noDate' },
        { start: undefined, end: dayjs().subtract(1, 'hour'), group: 'past' },
        { start: undefined, end: dayjs().add(2, 'days'), group: 'dueSoon' },
        { start: undefined, end: dayjs().add(4, 'days'), group: 'current' },
        // start date in past
        { start: dayjs().subtract(7, 'days'), end: undefined, group: 'noDate' },
        { start: dayjs().subtract(7, 'days'), end: dayjs().subtract(1, 'hour'), group: 'past' },
        { start: dayjs().subtract(7, 'days'), end: dayjs().add(2, 'days'), group: 'dueSoon' },
        { start: dayjs().subtract(7, 'days'), end: dayjs().add(4, 'days'), group: 'current' },
        // start date in the future
        { start: dayjs().add(5, 'minutes'), end: undefined, group: 'future' },
        { start: dayjs().add(5, 'minutes'), end: dayjs().subtract(1, 'hour'), group: 'future' }, // this case should never happen in actual exercises
        { start: dayjs().add(5, 'minutes'), end: dayjs().add(2, 'days'), group: 'future' },
        { start: dayjs().add(5, 'minutes'), end: dayjs().add(4, 'days'), group: 'future' },
    ])('should group exercises into the correct groups based on start and end dates (expected group: $group)', ({ start, end, group }) => {
        it.each([
            { releaseDate: undefined, startDate: start, endDate: end },
            { releaseDate: start, startDate: undefined, endDate: end },
            // release date can only be same as or earlier than start date
            { releaseDate: start, startDate: start, endDate: end },
            { releaseDate: start?.subtract(1, 'hour'), startDate: start, endDate: end },
        ])('should group them according to start or release date (release: $releaseDate, start: $startDate, end: $endDate)', ({ releaseDate, startDate, endDate }) => {
            const exercise = new TextExercise(course, undefined);
            exercise.releaseDate = releaseDate;
            exercise.startDate = startDate;
            exercise.dueDate = endDate;

            const groupedExercises = service.groupExercisesByDueDate([exercise]);
            for (const possibleGroup of ['past', 'current', 'dueSoon', 'future', 'noDate']) {
                if (possibleGroup === group) {
                    expect(groupedExercises[possibleGroup].entityData).toHaveLength(1);
                } else {
                    expect(groupedExercises[possibleGroup].entityData).toHaveLength(0);
                }
            }
        });
    });

    it('should group an exercise with no release date or due date, but start date in future as future', () => {
        const exercise = new TextExercise(course, undefined);
        exercise.releaseDate = undefined;
        exercise.startDate = dayjs().add(1, 'day');
        exercise.dueDate = undefined;

        const groupedExercises = service.groupExercisesByDueDate([exercise]);
        expect(groupedExercises['future'].entityData).toHaveLength(1);
    });

    it('should group all exercises as past when all exercises have past due dates', () => {
        const pastExercises: Exercise[] = [
            { id: 1, title: 'Past Exercise 1', dueDate: dayjs().subtract(1, 'day') } as Exercise,
            { id: 2, title: 'Past Exercise 2', dueDate: dayjs().subtract(2, 'day') } as Exercise,
            { id: 3, title: 'Past Exercise 3', dueDate: dayjs().subtract(3, 'day') } as Exercise,
        ];
        const sortedExercises = service.sortExercises(pastExercises);

        jest.spyOn(service, 'getCorrespondingExerciseGroupByDate');

        jest.spyOn(service, 'mapExerciseToSidebarCardElement');
        const groupedExercises = service.groupExercisesByDueDate(sortedExercises);

        expect(groupedExercises['past'].entityData).toHaveLength(3);
        expect(groupedExercises['current'].entityData).toHaveLength(0);
        expect(groupedExercises['future'].entityData).toHaveLength(0);
        expect(service.mapExerciseToSidebarCardElement).toHaveBeenCalledTimes(3);
        expect(groupedExercises['past'].entityData[0].title).toBe('Past Exercise 1');
        expect(groupedExercises['past'].entityData[1].title).toBe('Past Exercise 2');
        expect(groupedExercises['past'].entityData[2].title).toBe('Past Exercise 3');
    });

    it('should return undefined if exercises array is undefined', () => {
        expect(service.getUpcomingExercise(undefined)).toBeUndefined();
    });

    it('should return undefined if exercises array is empty', () => {
        expect(service.getUpcomingExercise([])).toBeUndefined();
    });

    it('should handle all past exercises', () => {
        const pastLectures: Exercise[] = [
            {
                id: 1,
                title: 'Past Exercise 1',
                dueDate: dayjs().subtract(2, 'day'),
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            },
            {
                id: 2,
                title: 'Past Exercise 2',
                dueDate: dayjs().subtract(1, 'day'),
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            },
        ];
        const upcomingLecture = service.getUpcomingExercise(pastLectures);

        // Assuming the function should return the most recent past lecture if all are in the past
        expect(upcomingLecture?.id).toBe(2);
    });

    it('should handle all past exams', () => {
        const pastExams: Exam[] = [
            {
                id: 1,
                title: 'Past Exam 1',
                endDate: dayjs().subtract(2, 'day'),
            },
            {
                id: 2,
                title: 'Past Exam 2',
                endDate: dayjs().subtract(1, 'day'),
            },
        ];
        const upcomingExam = service.getUpcomingExam(pastExams);

        // Assuming the function should return the most recent past lecture if all are in the past
        expect(upcomingExam?.id).toBe(2);
    });

    it('should correctly identify the exercises furthest in the future', () => {
        const exercises: Exercise[] = [
            {
                id: 1,
                title: 'Past Exercise',
                dueDate: dayjs().subtract(1, 'day'),
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            },
            {
                id: 2,
                title: 'Upcoming Exercise',
                dueDate: dayjs().add(1, 'day'),
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            },
            {
                id: 3,
                title: 'Far Future Exercise',
                dueDate: dayjs().add(2, 'weeks'),
                numberOfAssessmentsOfCorrectionRounds: [],
                secondCorrectionEnabled: false,
                studentAssignedTeamIdComputed: false,
            },
        ];
        const upcomingExercise = service.getUpcomingExercise(exercises);

        expect(upcomingExercise?.id).toBe(3);
    });

    it('should correctly identify the exams furthest in the future', () => {
        const exams: Exam[] = [
            {
                id: 1,
                title: 'Past Exam',
                endDate: dayjs().subtract(1, 'day'),
            },
            {
                id: 2,
                title: 'Upcoming Exam',
                endDate: dayjs().add(1, 'day'),
            },
            {
                id: 3,
                title: 'Far Future Exam',
                endDate: dayjs().add(2, 'weeks'),
            },
        ];
        const upcomingExam = service.getUpcomingExam(exams);

        expect(upcomingExam?.id).toBe(3);
    });

    it('should return undefined if exams array is undefined', () => {
        expect(service.getUpcomingExam(undefined)).toBeUndefined();
    });

    it('should return undefined if exams array is empty', () => {
        expect(service.getUpcomingExam([])).toBeUndefined();
    });

    it('should group conversations by conversation types and map to sidebar card elements', () => {
        const conversations = [generalChannel, examChannel, exerciseChannel];

        jest.spyOn(service, 'getCorrespondingChannelSubType');
        jest.spyOn(service, 'mapConversationToSidebarCardElement');
        const groupedConversations = service.groupConversationsByChannelType(conversations, true);

        expect(groupedConversations['generalChannels'].entityData).toHaveLength(1);
        expect(groupedConversations['examChannels'].entityData).toHaveLength(1);
        expect(groupedConversations['exerciseChannels'].entityData).toHaveLength(1);
        expect(service.mapConversationToSidebarCardElement).toHaveBeenCalledTimes(3);
        expect(getAsChannelDTO(groupedConversations['generalChannels'].entityData[0].conversation)?.name).toBe('General');
        expect(getAsChannelDTO(groupedConversations['examChannels'].entityData[0].conversation)?.name).toBe('exam-test');
        expect(getAsChannelDTO(groupedConversations['exerciseChannels'].entityData[0].conversation)?.name).toBe('exercise-test');
    });

    it('should group conversations together when having the same type', () => {
        const conversations = [generalChannel, generalChannel2];

        jest.spyOn(service, 'getCorrespondingChannelSubType');
        jest.spyOn(service, 'mapConversationToSidebarCardElement');
        const groupedConversations = service.groupConversationsByChannelType(conversations, true);

        expect(groupedConversations['generalChannels'].entityData).toHaveLength(2);
        expect(service.mapConversationToSidebarCardElement).toHaveBeenCalledTimes(2);
        expect(getAsChannelDTO(groupedConversations['generalChannels'].entityData[0].conversation)?.name).toBe('General');
        expect(getAsChannelDTO(groupedConversations['generalChannels'].entityData[1].conversation)?.name).toBe('General 2');
    });

    it('should group favorite and hidden conversations correctly', () => {
        const conversations = [generalChannel, examChannel, exerciseChannel, generalChannel2, favoriteChannel, hiddenChannel];

        jest.spyOn(service, 'getCorrespondingChannelSubType');
        jest.spyOn(service, 'mapConversationToSidebarCardElement');
        jest.spyOn(service, 'getConversationGroup');
        jest.spyOn(service, 'getCorrespondingChannelSubType');
        const groupedConversations = service.groupConversationsByChannelType(conversations, true);

        expect(groupedConversations['generalChannels'].entityData).toHaveLength(2);
        expect(groupedConversations['examChannels'].entityData).toHaveLength(1);
        expect(groupedConversations['exerciseChannels'].entityData).toHaveLength(1);
        expect(groupedConversations['favoriteChannels'].entityData).toHaveLength(1);
        expect(groupedConversations['hiddenChannels'].entityData).toHaveLength(1);
        expect(service.mapConversationToSidebarCardElement).toHaveBeenCalledTimes(6);
        expect(service.getConversationGroup).toHaveBeenCalledTimes(6);
        expect(service.getCorrespondingChannelSubType).toHaveBeenCalledTimes(4);
        expect(getAsChannelDTO(groupedConversations['generalChannels'].entityData[0].conversation)?.name).toBe('General');
        expect(getAsChannelDTO(groupedConversations['generalChannels'].entityData[1].conversation)?.name).toBe('General 2');
        expect(getAsChannelDTO(groupedConversations['examChannels'].entityData[0].conversation)?.name).toBe('exam-test');
        expect(getAsChannelDTO(groupedConversations['exerciseChannels'].entityData[0].conversation)?.name).toBe('exercise-test');
        expect(getAsChannelDTO(groupedConversations['favoriteChannels'].entityData[0].conversation)?.name).toBe('fav-channel');
        expect(getAsChannelDTO(groupedConversations['hiddenChannels'].entityData[0].conversation)?.name).toBe('hidden-channel');
    });
});
