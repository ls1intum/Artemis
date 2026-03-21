import { HttpResponse } from '@angular/common/http';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
import { Subject, of, throwError } from 'rxjs';
import { AlertService } from 'app/shared/service/alert.service';
import { TutorialGroupRegisteredStudentDTO } from 'app/tutorialgroup/shared/entities/tutorial-group.model';
import { TutorialGroupsService } from 'app/tutorialgroup/shared/service/tutorial-groups.service';
import { TutorialGroupRegisteredStudentsService } from './tutorial-group-registered-students.service';

interface TutorialGroupsServiceMock {
    deregisterStudent: ReturnType<typeof vi.fn>;
    getRegisteredStudentDTOs: ReturnType<typeof vi.fn>;
}

interface AlertServiceMock {
    addErrorAlert: ReturnType<typeof vi.fn>;
}

function createRegisteredStudent(id: number, login: string): TutorialGroupRegisteredStudentDTO {
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
    let tutorialGroupsService: TutorialGroupsService;
    let alertService: AlertService;

    const courseId = 1;
    const tutorialGroupId = 2;
    const studentLogin = 'alice';
    let tutorialGroupsServiceMock: TutorialGroupsServiceMock;
    let alertServiceMock: AlertServiceMock;

    beforeEach(() => {
        tutorialGroupsServiceMock = {
            deregisterStudent: vi.fn(),
            getRegisteredStudentDTOs: vi.fn(),
        };
        alertServiceMock = {
            addErrorAlert: vi.fn(),
        };

        TestBed.configureTestingModule({
            providers: [
                TutorialGroupRegisteredStudentsService,
                {
                    provide: TutorialGroupsService,
                    useValue: tutorialGroupsServiceMock,
                },
                {
                    provide: AlertService,
                    useValue: alertServiceMock,
                },
            ],
        });

        service = TestBed.inject(TutorialGroupRegisteredStudentsService);
        tutorialGroupsService = TestBed.inject(TutorialGroupsService);
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
        const response$ = new Subject<TutorialGroupRegisteredStudentDTO[]>();
        vi.spyOn(tutorialGroupsService, 'getRegisteredStudentDTOs').mockReturnValue(response$);

        service.fetchRegisteredStudents(courseId, tutorialGroupId);

        expect(service.isLoading()).toBe(true);
        expect(tutorialGroupsService.getRegisteredStudentDTOs).toHaveBeenCalledOnce();
        expect(tutorialGroupsService.getRegisteredStudentDTOs).toHaveBeenCalledWith(courseId, tutorialGroupId);

        response$.next(registeredStudents);
        response$.complete();

        expect(service.registeredStudents()).toEqual(registeredStudents);
        expect(service.isLoading()).toBe(false);
    });

    it('should show an error alert when fetching registered students fails', () => {
        vi.spyOn(tutorialGroupsService, 'getRegisteredStudentDTOs').mockReturnValue(throwError(() => new Error('network error')));

        service.fetchRegisteredStudents(courseId, tutorialGroupId);

        expect(alertService.addErrorAlert).toHaveBeenCalledOnce();
        expect(alertService.addErrorAlert).toHaveBeenCalledWith('artemisApp.services.tutorialGroupRegisteredStudentsService.networkError.fetchRegisteredStudents');
        expect(service.isLoading()).toBe(false);
        expect(service.registeredStudents()).toEqual([]);
    });

    it('should handle successful fetches with empty results', () => {
        vi.spyOn(tutorialGroupsService, 'getRegisteredStudentDTOs').mockReturnValue(of([]));

        service.fetchRegisteredStudents(courseId, tutorialGroupId);

        expect(service.registeredStudents()).toEqual([]);
        expect(service.isLoading()).toBe(false);
        expect(alertService.addErrorAlert).not.toHaveBeenCalled();
    });

    it('should deregister a student and remove them from the state', () => {
        service.addStudentsToRegisteredStudentsState([createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob')]);
        const response$ = new Subject<HttpResponse<void>>();
        vi.spyOn(tutorialGroupsService, 'deregisterStudent').mockReturnValue(response$);

        service.deregisterStudent(courseId, tutorialGroupId, studentLogin);

        expect(service.isLoading()).toBe(true);
        expect(tutorialGroupsService.deregisterStudent).toHaveBeenCalledOnce();
        expect(tutorialGroupsService.deregisterStudent).toHaveBeenCalledWith(courseId, tutorialGroupId, studentLogin);

        response$.next(new HttpResponse<void>({ status: 200 }));
        response$.complete();

        expect(service.registeredStudents()).toEqual([createRegisteredStudent(2, 'bob')]);
        expect(service.isLoading()).toBe(false);
    });

    it('should show an error alert when deregistering a student fails', () => {
        const existingStudents = [createRegisteredStudent(1, 'alice'), createRegisteredStudent(2, 'bob')];
        service.addStudentsToRegisteredStudentsState(existingStudents);
        vi.spyOn(tutorialGroupsService, 'deregisterStudent').mockReturnValue(throwError(() => new Error('network error')));

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
