import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject, of, throwError } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupRegisteredStudentsService } from './tutorial-group-registered-students.service';
import { TutorialGroupApiService } from 'app/openapi/api/tutorialGroupApi.service';
import { TutorialGroupStudent } from 'app/openapi/model/tutorialGroupStudent';

interface TutorialGroupApiServiceMock {
    deregisterStudent: ReturnType<typeof vi.fn>;
    getRegisteredStudents: ReturnType<typeof vi.fn>;
}

interface AlertServiceMock {
    addErrorAlert: ReturnType<typeof vi.fn>;
}

function createRegisteredStudent(id: number, login: string): TutorialGroupStudent {
    return {
        id,
        login,
        name: `${login} name`,
        email: `${login}@example.com`,
        registrationNumber: `${id}`,
    };
}

describe('TutorialGroupRegisteredStudentsService', () => {
    setupTestBed({ zoneless: true });

    let service: TutorialGroupRegisteredStudentsService;
    let alertService: AlertService;

    const courseId = 1;
    const tutorialGroupId = 2;
    const studentLogin = 'alice';
    let tutorialGroupApiServiceMock: TutorialGroupApiServiceMock;
    let alertServiceMock: AlertServiceMock;

    beforeEach(() => {
        tutorialGroupApiServiceMock = {
            deregisterStudent: vi.fn(),
            getRegisteredStudents: vi.fn(),
        };
        alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [
                TutorialGroupRegisteredStudentsService,
                {
                    provide: TutorialGroupApiService,
                    useValue: tutorialGroupApiServiceMock,
                },
                {
                    provide: AlertService,
                    useValue: alertServiceMock,
                },
            ],
        });

        service = TestBed.inject(TutorialGroupRegisteredStudentsService);
        alertService = TestBed.inject(AlertService);
    });

    afterEach(() => {
        vi.restoreAllMocks();
    });

    it('should be created', () => {
        expect(service).toBeTruthy();
    });

    it('should fetch registered students and update loading state', () => {
        const registeredStudents = [createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob')];
        const response$ = new Subject<TutorialGroupStudent[]>();
        tutorialGroupApiServiceMock.getRegisteredStudents.mockReturnValue(response$);

        service.fetchRegisteredStudents(courseId, tutorialGroupId);

        expect(service.isLoading()).toBe(true);
        expect(tutorialGroupApiServiceMock.getRegisteredStudents).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.getRegisteredStudents).toHaveBeenCalledWith(courseId, tutorialGroupId);

        response$.next(registeredStudents);
        response$.complete();

        expect(service.registeredStudents()).toEqual(registeredStudents);
        expect(service.isLoading()).toBe(false);
    });

    it('should show an error alert when fetching registered students fails', () => {
        tutorialGroupApiServiceMock.getRegisteredStudents.mockReturnValue(throwError(() => new Error('network error')));

        service.fetchRegisteredStudents(courseId, tutorialGroupId);

        expect(alertService.addErrorAlert).toHaveBeenCalledOnce();
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupRegisteredStudentsService.networkError.fetchRegisteredStudents');
        expect(service.isLoading()).toBe(false);
        expect(service.registeredStudents()).toEqual([]);
    });

    it('should handle successful fetches with empty results', () => {
        tutorialGroupApiServiceMock.getRegisteredStudents.mockReturnValue(of([]));

        service.fetchRegisteredStudents(courseId, tutorialGroupId);

        expect(service.registeredStudents()).toEqual([]);
        expect(service.isLoading()).toBe(false);
        expect(alertService.addErrorAlert).not.toHaveBeenCalled();
    });

    it('should deregister a student and remove them from the state', () => {
        service.addStudentsToRegisteredStudentsState([createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob')]);
        const response$ = new Subject<void>();
        tutorialGroupApiServiceMock.deregisterStudent.mockReturnValue(response$);

        service.deregisterStudent(courseId, tutorialGroupId, studentLogin);

        expect(service.isLoading()).toBe(true);
        expect(tutorialGroupApiServiceMock.deregisterStudent).toHaveBeenCalledOnce();
        expect(tutorialGroupApiServiceMock.deregisterStudent).toHaveBeenCalledWith(courseId, tutorialGroupId, studentLogin);

        response$.next();
        response$.complete();

        expect(service.registeredStudents()).toEqual([createRegisteredStudent(2, 'bob')]);
        expect(service.isLoading()).toBe(false);
    });

    it('should show an error alert when deregistering a student fails', () => {
        const existingStudents = [createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob')];
        service.addStudentsToRegisteredStudentsState(existingStudents);
        tutorialGroupApiServiceMock.deregisterStudent.mockReturnValue(throwError(() => new Error('network error')));

        service.deregisterStudent(courseId, tutorialGroupId, studentLogin);

        expect(alertService.addErrorAlert).toHaveBeenCalledOnce();
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupRegisteredStudentsService.networkError.deregisterStudent');
        expect(service.registeredStudents()).toEqual(existingStudents);
        expect(service.isLoading()).toBe(false);
    });

    it('should add only students that are not already present', () => {
        service.addStudentsToRegisteredStudentsState([createRegisteredStudent(1, 'alice')]);

        service.addStudentsToRegisteredStudentsState([createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob'), createRegisteredStudent(3, 'carol')]);

        expect(service.registeredStudents()).toEqual([createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob'), createRegisteredStudent(3, 'carol')]);
    });

    it('should deduplicate students within the same add operation', () => {
        service.addStudentsToRegisteredStudentsState([createRegisteredStudent(1, 'alice'), createRegisteredStudent(1, 'alice-duplicate')]);

        expect(service.registeredStudents()).toEqual([createRegisteredStudent(1, 'alice')]);
    });
});
