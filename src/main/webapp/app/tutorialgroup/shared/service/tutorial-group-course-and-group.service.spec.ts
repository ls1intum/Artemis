import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { Course } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupCourseAndGroupService } from './tutorial-group-course-and-group.service';
import { TutorialGroupDetailData as RawTutorialGroupDetailData } from 'app/openapi/model/tutorialGroupDetailData';
import { TutorialGroupSession as RawTutorialGroupSession } from 'app/openapi/model/tutorialGroupSession';
import { TutorialGroupSession } from 'app/tutorialgroup/shared/entities/tutorial-group-session.model';
import { TutorialGroupDetailData } from 'app/tutorialgroup/shared/entities/tutorial-group.model';

describe('TutorialGroupCourseAndGroupService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupCourseAndGroupService;

    let tutorialGroupApiService: { getTutorialGroup: ReturnType<typeof vi.fn> };
    let courseManagementService: { find: ReturnType<typeof vi.fn> };
    let alertService: { addErrorAlert: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        tutorialGroupApiService = {
            getTutorialGroup: vi.fn(),
        };
        courseManagementService = {
            find: vi.fn(),
        };
        alertService = {
            addErrorAlert: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [
                TutorialGroupCourseAndGroupService,
                { provide: TutorialGroupApiService, useValue: tutorialGroupApiService },
                { provide: CourseManagementService, useValue: courseManagementService },
                { provide: AlertService, useValue: alertService },
            ],
        });

        service = TestBed.inject(TutorialGroupCourseAndGroupService);
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    function createRawTutorialGroupSession(id: number, start: string, end: string, isCancelled: boolean): RawTutorialGroupSession {
        return {
            id,
            start,
            end,
            location: 'Room 101',
            isCancelled,
            isCancelledByFreePeriod: false,
            locationChanged: false,
            timeChanged: false,
            dateChanged: false,
            attendanceCount: undefined,
        };
    }

    function createTutorialGroupDetailData(sessions: RawTutorialGroupSession[]): RawTutorialGroupDetailData {
        return {
            id: 17,
            title: 'TG Tue 13',
            language: 'Polish',
            isOnline: false,
            sessions,
            tutorName: 'Grace Hopper',
            tutorLogin: 'grace',
            tutorId: 12,
            tutorImageUrl: undefined,
            capacity: undefined,
            campus: undefined,
            additionalInformation: undefined,
            groupChannelId: undefined,
            tutorChatId: undefined,
        };
    }

    it('should update the cancellation status of the matching session', () => {
        const firstSession = createRawTutorialGroupSession(1, '2026-04-20T10:15:00.000Z', '2026-04-20T11:45:00.000Z', false);
        const secondSession = createRawTutorialGroupSession(2, '2026-04-21T10:15:00.000Z', '2026-04-21T11:45:00.000Z', true);
        const tutorialGroup = new TutorialGroupDetailData(createTutorialGroupDetailData([firstSession, secondSession]));
        service.tutorialGroup.set(tutorialGroup);

        service.toggleCancellationStatusOfSession(1);

        expect(service.tutorialGroup()?.sessions[0].isCancelled).toBe(true);
        expect(service.tutorialGroup()?.sessions[1].isCancelled).toBe(true);
    });

    it('should guard toggleCancellationStatusOfSession when no tutorial group is loaded', () => {
        expect(service.tutorialGroup()).toBeUndefined();

        service.toggleCancellationStatusOfSession(1);

        expect(service.tutorialGroup()).toBeUndefined();
    });

    it('should insert a new session at the correct chronological index', () => {
        const firstSession = createRawTutorialGroupSession(1, '2026-04-20T10:15:00.000Z', '2026-04-20T11:45:00.000Z', false);
        const thirdSession = createRawTutorialGroupSession(3, '2026-04-22T10:15:00.000Z', '2026-04-22T11:45:00.000Z', false);
        const insertedSession = new TutorialGroupSession(createRawTutorialGroupSession(2, '2026-04-21T10:15:00.000Z', '2026-04-21T11:45:00.000Z', false));
        const tutorialGroup = new TutorialGroupDetailData(createTutorialGroupDetailData([firstSession, thirdSession]));
        service.tutorialGroup.set(tutorialGroup);

        service.insertSession(insertedSession);

        expect(service.tutorialGroup()?.sessions.map((session) => session.id)).toEqual([1, 2, 3]);
        expect(service.tutorialGroup()?.sessions[1]).toBe(insertedSession);
    });

    it('should guard insertSession when no tutorial group is loaded', () => {
        expect(service.tutorialGroup()).toBeUndefined();

        service.insertSession(new TutorialGroupSession(createRawTutorialGroupSession(1, '2026-04-20T10:15:00.000Z', '2026-04-20T11:45:00.000Z', false)));

        expect(service.tutorialGroup()).toBeUndefined();
    });

    it('should replace the matching session and keep sessions ordered by start time', () => {
        const firstSession = createRawTutorialGroupSession(1, '2026-04-20T10:15:00.000Z', '2026-04-20T11:45:00.000Z', false);
        const secondSession = createRawTutorialGroupSession(2, '2026-04-21T10:15:00.000Z', '2026-04-21T11:45:00.000Z', false);
        const thirdSession = createRawTutorialGroupSession(3, '2026-04-22T10:15:00.000Z', '2026-04-22T11:45:00.000Z', false);
        const updatedSecondSession = new TutorialGroupSession(createRawTutorialGroupSession(2, '2026-04-23T10:15:00.000Z', '2026-04-23T11:45:00.000Z', true));
        const tutorialGroup = new TutorialGroupDetailData(createTutorialGroupDetailData([firstSession, secondSession, thirdSession]));
        service.tutorialGroup.set(tutorialGroup);

        service.insertSession(updatedSecondSession);

        expect(service.tutorialGroup()?.sessions.map((session) => session.id)).toEqual([1, 3, 2]);
        expect(service.tutorialGroup()?.sessions).toHaveLength(3);
        expect(service.tutorialGroup()?.sessions[2]).toBe(updatedSecondSession);
        expect(service.tutorialGroup()?.sessions.find((session) => session.id === 2)?.isCancelled).toBe(true);
    });

    it('should fetch tutorial group on success and clear loading state', () => {
        const session = createRawTutorialGroupSession(1, '2026-04-20T10:15:00.000Z', '2026-04-20T11:45:00.000Z', false);
        const tutorialGroup = createTutorialGroupDetailData([session]);
        tutorialGroupApiService.getTutorialGroup.mockReturnValue(of(tutorialGroup));

        service.fetchTutorialGroup(2, 17);

        expect(tutorialGroupApiService.getTutorialGroup).toHaveBeenCalledWith(2, 17);
        expect(service.tutorialGroup()).toEqual(new TutorialGroupDetailData(tutorialGroup));
        expect(service.isTutorialGroupLoading()).toBe(false);
    });

    it('should show error alert if fetching tutorial group fails and clear loading state', () => {
        tutorialGroupApiService.getTutorialGroup.mockReturnValue(throwError(() => new Error('network error')));

        service.fetchTutorialGroup(2, 17);

        expect(tutorialGroupApiService.getTutorialGroup).toHaveBeenCalledWith(2, 17);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchGroup');
        expect(service.tutorialGroup()).toBeUndefined();
        expect(service.isTutorialGroupLoading()).toBe(false);
    });

    it('should fetch course on success and clear loading state', () => {
        const course = new Course();
        course.id = 2;
        courseManagementService.find.mockReturnValue(of(new HttpResponse({ body: course })));

        service.fetchCourse(2);

        expect(courseManagementService.find).toHaveBeenCalledWith(2);
        expect(service.course()).toBe(course);
        expect(service.isCourseLoading()).toBe(false);
    });

    it('should show error alert if fetched course response body is empty and clear loading state', () => {
        courseManagementService.find.mockReturnValue(of(new HttpResponse({ body: null })));

        service.fetchCourse(2);

        expect(courseManagementService.find).toHaveBeenCalledWith(2);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchCourse');
        expect(service.course()).toBeUndefined();
        expect(service.isCourseLoading()).toBe(false);
    });

    it('should show error alert if fetching course fails and clear loading state', () => {
        courseManagementService.find.mockReturnValue(throwError(() => new Error('network error')));

        service.fetchCourse(2);

        expect(courseManagementService.find).toHaveBeenCalledWith(2);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupCourseAndGroupService.networkError.fetchCourse');
        expect(service.course()).toBeUndefined();
        expect(service.isCourseLoading()).toBe(false);
    });
});
