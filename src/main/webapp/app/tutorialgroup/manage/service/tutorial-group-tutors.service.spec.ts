import { TestBed } from '@angular/core/testing';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { HttpResponse } from '@angular/common/http';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { User } from 'app/core/user/user.model';
import { CourseGroup } from 'app/core/course/shared/entities/course.model';
import { CourseManagementService } from 'app/core/course/manage/services/course-management.service';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupTutorsService } from './tutorial-group-tutors.service';

describe('TutorialGroupTutorsService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupTutorsService;

    let courseManagementService: { getAllUsersInCourseGroup: ReturnType<typeof vi.fn> };
    let alertService: { addErrorAlert: ReturnType<typeof vi.fn> };

    beforeEach(() => {
        courseManagementService = {
            getAllUsersInCourseGroup: vi.fn(),
        };
        alertService = {
            addErrorAlert: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [TutorialGroupTutorsService, { provide: CourseManagementService, useValue: courseManagementService }, { provide: AlertService, useValue: alertService }],
        });

        service = TestBed.inject(TutorialGroupTutorsService);
    });

    afterEach(() => {
        vi.clearAllMocks();
        vi.restoreAllMocks();
    });

    it('should load tutors on success, map valid users, and clear loading state', () => {
        const firstTutor = { id: 11, login: 'ada', firstName: 'Ada', lastName: 'Lovelace' } as User;
        const secondTutor = { id: 12, login: 'grace', firstName: 'Grace' } as User;
        const thirdTutor = { id: 13, login: 'marlon', lastName: 'Nienaber' } as User;
        const fourthTutor = { id: 14, login: 'plain' } as User;
        const invalidTutorWithoutId = { login: 'missing-id', firstName: 'Broken' } as User;
        const invalidTutorWithoutLogin = { id: 15, firstName: 'Missing', lastName: 'Login' } as User;
        courseManagementService.getAllUsersInCourseGroup.mockReturnValue(
            of(new HttpResponse({ body: [firstTutor, secondTutor, thirdTutor, fourthTutor, invalidTutorWithoutId, invalidTutorWithoutLogin] })),
        );

        service.loadTutors(2);

        expect(courseManagementService.getAllUsersInCourseGroup).toHaveBeenCalledWith(2, CourseGroup.TUTORS);
        expect(service.tutors()).toEqual([
            { id: 11, nameAndLogin: 'ada (Ada Lovelace)' },
            { id: 12, nameAndLogin: 'grace (Grace)' },
            { id: 13, nameAndLogin: 'marlon (Nienaber)' },
            { id: 14, nameAndLogin: 'plain' },
        ]);
        expect(service.isLoading()).toBe(false);
    });

    it('should load no tutors if the response body is empty and clear loading state', () => {
        courseManagementService.getAllUsersInCourseGroup.mockReturnValue(of(new HttpResponse({ body: null })));

        service.loadTutors(2);

        expect(courseManagementService.getAllUsersInCourseGroup).toHaveBeenCalledWith(2, CourseGroup.TUTORS);
        expect(service.tutors()).toEqual([]);
        expect(service.isLoading()).toBe(false);
    });

    it('should show error alert if loading tutors fails and clear loading state', () => {
        courseManagementService.getAllUsersInCourseGroup.mockReturnValue(throwError(() => new Error('network error')));

        service.loadTutors(2);

        expect(courseManagementService.getAllUsersInCourseGroup).toHaveBeenCalledWith(2, CourseGroup.TUTORS);
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupTutorService.networkError.fetchTutors');
        expect(service.tutors()).toEqual([]);
        expect(service.isLoading()).toBe(false);
    });
});
