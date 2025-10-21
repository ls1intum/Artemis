import { TestBed, fakeAsync, tick } from '@angular/core/testing';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { SessionStorageService } from 'app/shared/service/session-storage.service';
import { of } from 'rxjs';
import { MockService } from 'ng-mocks';
import { WebsocketService } from 'app/shared/service/websocket.service';
import { FeatureToggleService } from 'app/shared/feature-toggle/feature-toggle.service';
import { MockHttpService } from 'test/helpers/mocks/service/mock-http.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from 'test/helpers/mocks/service/mock-translate.service';
import { Authority } from 'app/shared/constants/authority.constants';
import { Course } from 'app/core/course/shared/entities/course.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { Participation } from 'app/exercise/shared/entities/participation/participation.model';
import { Team } from 'app/exercise/shared/entities/team/team.model';
import { provideHttpClient } from '@angular/common/http';
import { UserSshPublicKey } from 'app/programming/shared/entities/user-ssh-public-key.model';

describe('AccountService', () => {
    let accountService: AccountService;
    let httpService: MockHttpService;
    let getStub: jest.SpyInstance;
    let httpMock: HttpTestingController;

    const getUserUrl = 'api/core/public/account';
    const updateLanguageUrl = 'api/core/public/account/change-language';
    const user = { id: 1, groups: ['USER'] } as User;
    const user2 = { id: 2, groups: ['USER'] } as User;
    const user3 = { id: 3, groups: ['USER', 'TA'], authorities: [Authority.USER] } as User;

    const authorities = [Authority.USER, Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA];
    const course = {
        instructorGroupName: 'INSTRUCTOR',
        editorGroupName: 'EDITOR',
        teachingAssistantGroupName: 'TA',
    } as Course;
    const exercise = { course } as Exercise;
    const examExercise = { exerciseGroup: { exam: { course } } } as Exercise;
    let result: boolean;

    beforeEach(() => {
        TestBed.configureTestingModule({
            providers: [
                { provide: TranslateService, useClass: MockTranslateService },
                SessionStorageService,
                { provide: WebsocketService, useValue: MockService(WebsocketService) },
                { provide: FeatureToggleService, useValue: MockService(FeatureToggleService) },
                provideHttpClient(),
                provideHttpClientTesting(),
            ],
        });
        accountService = TestBed.inject(AccountService);
        httpService = new MockHttpService();
        httpMock = TestBed.inject(HttpTestingController);
        getStub = jest.spyOn(httpService, 'get');
    });

    afterEach(() => {
        httpMock.verify();
        jest.restoreAllMocks();
    });

    it('should fetch the user on identity if the userIdentity is not defined yet', fakeAsync(() => {
        let userReceived: User;
        accountService.identity().then((returnedUser: User) => {
            userReceived = returnedUser;
        });

        const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
        req.flush(user);

        // Use tick() to simulate the passage of time and allow promise resolution
        tick();

        // @ts-ignore
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBeTrue();
    }));

    it('should handle user SSH public key correctly', () => {
        const sshKey = new UserSshPublicKey();
        sshKey.id = 123;
        sshKey.label = 'test-label';

        expect(sshKey.id).toBe(123);
        expect(sshKey.label).toBe('test-label');
    });

    it('should fetch the user on identity if the userIdentity is defined yet (force=true)', fakeAsync(() => {
        let userReceived: User;
        accountService.userIdentity.set(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBeTrue();

        accountService.identity(true).then((returnedUser: User) => {
            userReceived = returnedUser;
        });

        const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
        req.flush(user2);

        // Use tick() to simulate the passage of time and allow promise resolution
        tick();

        // @ts-ignore
        expect(userReceived).toEqual(user2);
        expect(accountService.userIdentity()).toEqual(user2);
        expect(accountService.isAuthenticated()).toBeTrue();
    }));

    it('should NOT fetch the user on identity if the userIdentity is defined (force=false)', async () => {
        accountService.userIdentity.set(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBeTrue();
        getStub.mockReturnValue(of({ body: user2 }));

        const userReceived = await accountService.identity(false);

        expect(getStub).not.toHaveBeenCalled();
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBeTrue();
    });

    it('should authenticate a user', () => {
        expect(accountService.userIdentity()).toBeUndefined();
        expect(accountService.isAuthenticated()).toBeFalse();

        accountService.authenticate(user);

        expect(accountService.userIdentity()).toEqual(user);
    });

    it('should sync user groups', () => {
        accountService.userIdentity.set(user);

        accountService.syncGroups(user3.groups!);

        expect(accountService.userIdentity().groups).toEqual(['USER', 'TA']);
    });

    describe('test authority check', () => {
        const usedAuthorities: Authority[] = [];
        it.each(authorities)('should return false if not authenticated, no user id and no authorities are set', async (authority: Authority) => {
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBeFalse();
        });

        it.each(authorities)('should return false if authenticated, user id but no authorities are set', async (authority: Authority) => {
            accountService.userIdentity.set(user);
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBeFalse();
        });

        it.each(authorities)('should return true if user is required', async (authority: Authority) => {
            accountService.userIdentity.set(user3);
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBeTrue();
        });

        it.each(authorities)('should return true if authority matches exactly', async (authority: Authority) => {
            accountService.userIdentity.set({ id: authorities.indexOf(authority), groups: ['USER'], authorities: [authority] } as User);

            await expect(accountService.hasAnyAuthority([authority])).resolves.toBeTrue();
        });

        it.each(authorities)('should return false if authority does not match', async (authority: Authority) => {
            const index = authorities.indexOf(authority);
            accountService.userIdentity.set({ id: index + 1, groups: ['USER'], authorities: [authorities[(index + 1) % 5]] } as User);

            await expect(accountService.hasAnyAuthority([authority])).resolves.toBeFalse();
        });

        it.each(authorities)('should return false if not authenticated', async (authority: Authority) => {
            await expect(accountService.hasAuthority(authority)).resolves.toBeFalse();
        });
    });

    describe('test hasGroup', () => {
        const groups = ['USER', 'EDITOR', 'ADMIN'];
        it.each(groups)('should return false if not authenticated', (group: string) => {
            result = accountService.hasGroup(group);

            expect(result).toBeFalse();
        });

        it.each(groups)('should return false if no authorities are set', (group: string) => {
            accountService.userIdentity.set(user);
            result = accountService.hasGroup(group);

            expect(result).toBeFalse();
        });

        it.each(groups)('should return false if no groups are set', (group: string) => {
            accountService.userIdentity.set({ id: 10, authorities } as User);
            result = accountService.hasGroup(group);

            expect(result).toBeFalse();
        });

        it.each(groups)('should return false if group does not match', (group: string) => {
            const index = groups.indexOf(group);
            accountService.userIdentity.set({ id: 10, groups: [groups[index + (1 % 3)]], authorities } as User);

            result = accountService.hasGroup(group);

            expect(result).toBeFalse();
        });

        it.each(groups)('should return true if group matchs', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.hasGroup(group);

            expect(result).toBeTrue();
        });
    });

    describe('test isAtLeastTutorInCourse', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBeFalse();
        });

        it.each(['TA', 'EDITOR', 'INSTRUCTOR'])('should return true if user is tutor, editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBeTrue();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBeTrue();
        });
    });

    describe('test isAtLeastEditorInCourse', () => {
        it('should return false if user is not editor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBeFalse();
        });

        it.each(['EDITOR', 'INSTRUCTOR'])('should return true if user is editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBeTrue();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBeTrue();
        });
    });

    describe('test isAtLeastInstructorInCourse', () => {
        it('should return false if user is not editor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBeFalse();
        });

        it('should return true if user is instructor', () => {
            accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBeTrue();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBeTrue();
        });
    });

    describe('test isAtLeastTutorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBeFalse();

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBeFalse();
        });

        it.each(['TA', 'EDITOR', 'INSTRUCTOR'])('should return true if user is tutor, editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBeTrue();

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBeTrue();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBeTrue();

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBeTrue();
        });
    });

    describe('test isAtLeastEditorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBeFalse();

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBeFalse();
        });

        it.each(['EDITOR', 'INSTRUCTOR'])('should return true if user is editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBeTrue();

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBeTrue();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBeTrue();

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBeTrue();
        });
    });

    describe('test isAtLeastInstructorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBeFalse();

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBeFalse();
        });

        it('should return true if user is editor or instructor', () => {
            accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBeTrue();

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBeTrue();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBeTrue();

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBeTrue();
        });
    });

    describe('test isAdmin', () => {
        it('should return false if user is not admin', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAdmin();

            expect(result).toBeFalse();
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAdmin();

            expect(result).toBeTrue();
        });
    });

    it('should set access rights for referenced course', () => {
        accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

        accountService.setAccessRightsForExerciseAndReferencedCourse(exercise);

        expect(exercise.isAtLeastEditor).toBeTrue();
        expect(exercise.isAtLeastInstructor).toBeTrue();
        expect(exercise.course!.isAtLeastEditor).toBeTrue();
        expect(exercise.course!.isAtLeastInstructor).toBeTrue();
    });

    it('should set access rights for referenced course in exam mode', () => {
        accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

        accountService.setAccessRightsForExerciseAndReferencedCourse(examExercise);

        expect(examExercise.isAtLeastEditor).toBeTrue();
        expect(examExercise.isAtLeastInstructor).toBeTrue();
        expect(examExercise.exerciseGroup!.exam!.course!.isAtLeastEditor).toBeTrue();
        expect(examExercise.exerciseGroup!.exam!.course!.isAtLeastInstructor).toBeTrue();
    });

    it('should set access rights for referenced exercise', () => {
        course.exercises = [exercise];
        accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

        accountService.setAccessRightsForCourseAndReferencedExercises(course);

        expect(exercise.isAtLeastEditor).toBeTrue();
        expect(exercise.isAtLeastInstructor).toBeTrue();
        expect(exercise.course!.isAtLeastEditor).toBeTrue();
        expect(exercise.course!.isAtLeastInstructor).toBeTrue();
    });

    describe('test isOwnerOfParticipation', () => {
        let participation: Participation;
        const user4 = { login: 'user' } as User;
        const team = { students: [user4] } as Team;
        it('should return false if student is not owner', () => {
            participation = { student: user } as Participation;
            accountService.userIdentity.set(user4);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBeFalse();
        });

        it('should return true if student is owner', () => {
            participation = { student: user4 } as Participation;
            accountService.userIdentity.set(user4);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBeTrue();
        });

        it('should return false if student is not part of the team', () => {
            participation = { team } as Participation;
            accountService.userIdentity.set(user);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBeFalse();
        });

        it('should return true if student is part of the team', () => {
            participation = { team } as Participation;
            accountService.userIdentity.set(user4);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBeTrue();
        });
    });

    describe('test getImageUrl', () => {
        let url: string | undefined;
        it('should return undefined if not authenticated', () => {
            url = accountService.getImageUrl();

            expect(url).toBeUndefined();
        });

        it('should return image url if authenticated', () => {
            const expectedUrl = 'profiles-pictures/example.png';
            accountService.userIdentity.set({ imageUrl: expectedUrl } as User);

            url = accountService.getImageUrl();

            expect(url).toBe(`api/core/files/${expectedUrl}`);
        });
    });

    it('should call update language url with language key', () => {
        accountService.updateLanguage('EN').subscribe(() => {});

        const req = httpMock.expectOne({ method: 'POST', url: updateLanguageUrl });
        req.flush({});
        expect(req.request.body).toBe('EN');
    });

    describe('test prefilled username', () => {
        it('should set prefilled username', () => {
            accountService.setPrefilledUsername('user');

            expect(accountService.getAndClearPrefilledUsername()).toBe('user');
        });

        it('should clear prefilledusername after get', () => {
            accountService.setPrefilledUsername('test');

            expect(accountService.getAndClearPrefilledUsername()).toBe('test');
            expect(accountService.getAndClearPrefilledUsername()).toBeUndefined();
        });
    });

    describe('test token related user retrieval logic', () => {
        let fetchStub: jest.SpyInstance;

        beforeEach(() => {
            // @ts-ignore spying on private method
            fetchStub = jest.spyOn(accountService, 'fetch');
        });

        it('should not retrieve user if vcs token is missing but not required', () => {
            user.vcsAccessToken = undefined;
            accountService.userIdentity.set(user);

            accountService.identity();
            expect(fetchStub).not.toHaveBeenCalled();
        });

        it('should not retrieve user if vcs token is present', () => {
            user.vcsAccessToken = 'iAmAToken';
            accountService.userIdentity.set(user);

            accountService.identity();
            expect(fetchStub).not.toHaveBeenCalled();
        });
    });

    describe('test vcs token related logic', () => {
        afterEach(() => {
            httpMock.verify();
        });

        it('should delete user VCS access token', () => {
            accountService.deleteUserVcsAccessToken().subscribe(() => {});

            const req = httpMock.expectOne({ method: 'DELETE', url: 'api/core/account/user-vcs-access-token' });
            req.flush(null);
        });

        it('should add a new VCS access token', () => {
            const expiryDate = '2024-10-10';

            accountService.addNewVcsAccessToken(expiryDate).subscribe((response) => {
                expect(response.status).toBe(200);
            });

            const req = httpMock.expectOne({ method: 'PUT', url: `api/core/account/user-vcs-access-token?expiryDate=${expiryDate}` });
            req.flush({ status: 200 });
        });

        it('should get VCS access token for a participation', () => {
            const participationId = 1;
            const token = 'vcs-token';

            accountService.getVcsAccessToken(participationId).subscribe((response) => {
                expect(response.body).toEqual(token);
            });

            const req = httpMock.expectOne({ method: 'GET', url: `api/core/account/participation-vcs-access-token?participationId=${participationId}` });
            req.flush({ body: token });
        });

        it('should create VCS access token for a participation', () => {
            const participationId = 1;
            const token = 'vcs-token';

            accountService.createVcsAccessToken(participationId).subscribe(() => {});

            const req = httpMock.expectOne({ method: 'PUT', url: `api/core/account/participation-vcs-access-token?participationId=${participationId}` });
            req.flush({ body: token });
        });
    });

    describe('test external LLM usage acceptance', () => {
        beforeEach(() => {
            jest.useFakeTimers();
            // Set a fixed date for consistent testing
            jest.setSystemTime(new Date('2024-02-06'));
        });

        afterEach(() => {
            jest.useRealTimers();
        });

        it('should set externalLLMUsageAccepted when user identity exists', () => {
            // Setup user identity
            accountService.userIdentity.set({ id: 1, groups: ['USER'] } as User);

            // Call the function
            accountService.setUserAcceptedExternalLLMUsage();

            // Check if the date was set correctly
            const acceptedDate = accountService.userIdentity().externalLLMUsageAccepted;
            expect(acceptedDate).toBeDefined();
            expect(acceptedDate?.format('YYYY-MM-DD')).toBe('2024-02-06');
        });

        it('should not throw error when user identity is undefined', () => {
            // Ensure userIdentity is undefined
            accountService.userIdentity.set(undefined);

            // Verify that calling the function doesn't throw an error
            expect(() => accountService.setUserAcceptedExternalLLMUsage()).not.toThrow();
        });
    });
});
