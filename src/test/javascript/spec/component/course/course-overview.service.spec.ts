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
import { ChannelDTO, ChannelSubType, getAsChannelDTO } from 'app/entities/metis/conversation/channel.model';

describe('CourseOverviewService', () => {
    let service: CourseOverviewService;
    let pastExercise: Exercise;
    let currentExercise: Exercise;
    let futureExercise: Exercise;
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

        course.id = 2;
        currentExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        currentExercise.dueDate = tomorrow;
        currentExercise.releaseDate = lastWeek;
        currentExercise.title = 'Current Exercise';

        course.id = 3;
        futureExercise = new ModelingExercise(UMLDiagramType.ClassDiagram, course, undefined) as Exercise;
        futureExercise.dueDate = nextWeeks;
        futureExercise.releaseDate = tomorrow;
        futureExercise.title = 'Future Exercise';

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
        const exercises = [currentExercise, pastExercise, futureExercise];
        const sortedExercises = service.sortExercises(exercises);

        expect(sortedExercises[0].id).toBe(futureExercise.id);
        expect(sortedExercises[1].id).toBe(currentExercise.id);
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
        const sortedExercises = [futureExercise, pastExercise, currentExercise];

        jest.spyOn(service, 'getCorrespondingExerciseGroupByDate');

        jest.spyOn(service, 'mapExerciseToSidebarCardElement');
        const groupedExercises = service.groupExercisesByDueDate(sortedExercises);

        expect(groupedExercises['current'].entityData).toHaveLength(1);
        expect(groupedExercises['past'].entityData).toHaveLength(1);
        expect(groupedExercises['future'].entityData).toHaveLength(1);
        expect(service.mapExerciseToSidebarCardElement).toHaveBeenCalledTimes(3);
        expect(groupedExercises['current'].entityData[0].title).toBe('Current Exercise');
        expect(groupedExercises['past'].entityData[0].title).toBe('Past Exercise');
        expect(groupedExercises['future'].entityData[0].title).toBe('Future Exercise');
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

    it('should group conversations by conversation types and map to sidebar card elements', () => {
        const conversations = [generalChannel, examChannel, exerciseChannel];

        jest.spyOn(service, 'getCorrespondingChannelSubType');
        jest.spyOn(service, 'mapConversationToSidebarCardElement');
        const groupedConversations = service.groupConversationsByChannelType(conversations);

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
        const groupedConversations = service.groupConversationsByChannelType(conversations);

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
        const groupedConversations = service.groupConversationsByChannelType(conversations);

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
