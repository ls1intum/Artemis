import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { setupTestBed } from '@analogjs/vitest-angular/setup-testbed';
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
import { StudentParticipation } from 'app/exercise/shared/entities/participation/student-participation.model';

describe('AccountService', () => {
    setupTestBed({ zoneless: true });

    let accountService: AccountService;
    let httpService: MockHttpService;
    let getStub: ReturnType<typeof vi.spyOn>;
    let httpMock: HttpTestingController;

    const getUserUrl = 'api/core/public/account';
    const updateLanguageUrl = 'api/core/public/account/change-language';
    const user = { id: 1, groups: ['USER'] } as User;
    const user2 = { id: 2, groups: ['USER'] } as User;
    const user3 = { id: 3, groups: ['USER', 'TA'], authorities: [Authority.STUDENT] } as User;

    const authorities = [Authority.STUDENT, Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TUTOR];
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
        getStub = vi.spyOn(httpService, 'get');
    });

    afterEach(() => {
        httpMock.verify();
        vi.restoreAllMocks();
    });

    it('should fetch the user on identity if the userIdentity is not defined yet', async () => {
        const identityPromise = accountService.identity();

        const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
        req.flush(user);

        const userReceived = await identityPromise;

        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should handle user SSH public key correctly', () => {
        const sshKey = new UserSshPublicKey();
        sshKey.id = 123;
        sshKey.label = 'test-label';

        expect(sshKey.id).toBe(123);
        expect(sshKey.label).toBe('test-label');
    });

    it('should fetch the user on identity if the userIdentity is defined yet (force=true)', async () => {
        accountService.userIdentity.set(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);

        const identityPromise = accountService.identity(true);

        const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
        req.flush(user2);

        const userReceived = await identityPromise;

        expect(userReceived).toEqual(user2);
        expect(accountService.userIdentity()).toEqual(user2);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should NOT fetch the user on identity if the userIdentity is defined (force=false)', async () => {
        accountService.userIdentity.set(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
        getStub.mockReturnValue(of({ body: user2 }));

        const userReceived = await accountService.identity(false);

        expect(getStub).not.toHaveBeenCalled();
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity()).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should authenticate a user', () => {
        expect(accountService.userIdentity()).toBeUndefined();
        expect(accountService.isAuthenticated()).toBe(false);

        accountService.authenticate(user);

        expect(accountService.userIdentity()).toEqual(user);
    });

    it('should sync user groups', () => {
        accountService.userIdentity.set(user);

        accountService.syncGroups(user3.groups!);

        expect(accountService.userIdentity()?.groups).toEqual(['USER', 'TA']);
    });

    describe('test authority check', () => {
        const usedAuthorities: Authority[] = [];
        it.each(authorities)('should return false if not authenticated, no user id and no authorities are set', async (authority: Authority) => {
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBe(false);
        });

        it.each(authorities)('should return false if authenticated, user id but no authorities are set', async (authority: Authority) => {
            accountService.userIdentity.set(user);
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBe(false);
        });

        it.each(authorities)('should return true if user is required', async (authority: Authority) => {
            accountService.userIdentity.set(user3);
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBe(true);
        });

        it.each(authorities)('should return true if authority matches exactly', async (authority: Authority) => {
            accountService.userIdentity.set({ id: authorities.indexOf(authority), groups: ['USER'], authorities: [authority] } as User);

            await expect(accountService.hasAnyAuthority([authority])).resolves.toBe(true);
        });

        it.each(authorities)('should return false if authority does not match', async (authority: Authority) => {
            const index = authorities.indexOf(authority);
            accountService.userIdentity.set({ id: index + 1, groups: ['USER'], authorities: [authorities[(index + 1) % 5]] } as User);

            await expect(accountService.hasAnyAuthority([authority])).resolves.toBe(false);
        });

        it.each(authorities)('should return false if not authenticated', async (authority: Authority) => {
            await expect(accountService.hasAuthority(authority)).resolves.toBe(false);
        });
    });

    describe('test hasGroup', () => {
        const groups = ['USER', 'EDITOR', 'ADMIN'];
        it.each(groups)('should return false if not authenticated', (group: string) => {
            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return false if no authorities are set', (group: string) => {
            accountService.userIdentity.set(user);
            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return false if no groups are set', (group: string) => {
            accountService.userIdentity.set({ id: 10, authorities } as User);
            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return false if group does not match', (group: string) => {
            const index = groups.indexOf(group);
            accountService.userIdentity.set({ id: 10, groups: [groups[index + (1 % 3)]], authorities } as User);

            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return true if group matchs', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.hasGroup(group);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastTutorInCourse', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBe(false);
        });

        it.each(['TA', 'EDITOR', 'INSTRUCTOR'])('should return true if user is tutor, editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastEditorInCourse', () => {
        it('should return false if user is not editor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBe(false);
        });

        it.each(['EDITOR', 'INSTRUCTOR'])('should return true if user is editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastInstructorInCourse', () => {
        it('should return false if user is not editor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBe(false);
        });

        it('should return true if user is instructor', () => {
            accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastTutorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBe(false);

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBe(false);
        });

        it.each(['TA', 'EDITOR', 'INSTRUCTOR'])('should return true if user is tutor, editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastEditorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBe(false);

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBe(false);
        });

        it.each(['EDITOR', 'INSTRUCTOR'])('should return true if user is editor or instructor', (group: string) => {
            accountService.userIdentity.set({ id: 10, groups: [group], authorities } as User);

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastInstructorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBe(false);

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBe(false);
        });

        it('should return true if user is editor or instructor', () => {
            accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBe(true);
        });
    });

    describe('test isAdmin', () => {
        it('should return false if user is not admin', () => {
            accountService.userIdentity.set(user2);

            result = accountService.isAdmin();

            expect(result).toBe(false);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            result = accountService.isAdmin();

            expect(result).toBe(true);
        });
    });

    it('should set access rights for referenced course', () => {
        accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

        accountService.setAccessRightsForExerciseAndReferencedCourse(exercise);

        expect(exercise.isAtLeastEditor).toBe(true);
        expect(exercise.isAtLeastInstructor).toBe(true);
        expect(exercise.course!.isAtLeastEditor).toBe(true);
        expect(exercise.course!.isAtLeastInstructor).toBe(true);
    });

    it('should set access rights for referenced course in exam mode', () => {
        accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

        accountService.setAccessRightsForExerciseAndReferencedCourse(examExercise);

        expect(examExercise.isAtLeastEditor).toBe(true);
        expect(examExercise.isAtLeastInstructor).toBe(true);
        expect(examExercise.exerciseGroup!.exam!.course!.isAtLeastEditor).toBe(true);
        expect(examExercise.exerciseGroup!.exam!.course!.isAtLeastInstructor).toBe(true);
    });

    it('should set access rights for referenced exercise', () => {
        course.exercises = [exercise];
        accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

        accountService.setAccessRightsForCourseAndReferencedExercises(course);

        expect(exercise.isAtLeastEditor).toBe(true);
        expect(exercise.isAtLeastInstructor).toBe(true);
        expect(exercise.course!.isAtLeastEditor).toBe(true);
        expect(exercise.course!.isAtLeastInstructor).toBe(true);
    });

    describe('test isOwnerOfParticipation', () => {
        let participation: Participation;
        const user4 = { login: 'user' } as User;
        const team = { students: [user4] } as Team;
        it('should return false if student is not owner', () => {
            participation = { student: user } as Participation;
            accountService.userIdentity.set(user4);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(false);
        });

        it('should return true if student is owner', () => {
            participation = { student: user4 } as Participation;
            accountService.userIdentity.set(user4);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(true);
        });

        it('should return false if student is not part of the team', () => {
            participation = { team } as Participation;
            accountService.userIdentity.set(user);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(false);
        });

        it('should return true if student is part of the team', () => {
            participation = { team } as Participation;
            accountService.userIdentity.set(user4);

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(true);
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
        let fetchStub: ReturnType<typeof vi.spyOn>;

        beforeEach(() => {
            // @ts-ignore spying on private method
            fetchStub = vi.spyOn(accountService, 'fetch');
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
            req.flush(token);
        });

        it('should create VCS access token for a participation', () => {
            const participationId = 1;
            const token = 'vcs-token';

            accountService.createVcsAccessToken(participationId).subscribe(() => {});

            const req = httpMock.expectOne({ method: 'PUT', url: `api/core/account/participation-vcs-access-token?participationId=${participationId}` });
            req.flush(token);
        });
    });

    describe('test external LLM usage acceptance', () => {
        beforeEach(() => {
            vi.useFakeTimers();
            // Set a fixed date for consistent testing
            vi.setSystemTime(new Date('2024-02-06'));
        });

        afterEach(() => {
            vi.useRealTimers();
        });

        it('should set externalLLMUsageAccepted when user identity exists', () => {
            // Setup user identity
            accountService.userIdentity.set({ id: 1, groups: ['USER'] } as User);

            // Call the function
            accountService.setUserAcceptedExternalLLMUsage();

            // Check if the date was set correctly
            const acceptedDate = accountService.userIdentity()?.externalLLMUsageAccepted;
            expect(acceptedDate).toBeDefined();
            expect(acceptedDate?.format('YYYY-MM-DD')).toBe('2024-02-06');
        });

        it('should not throw error when user identity is undefined', () => {
            // Ensure userIdentity is undefined
            accountService.userIdentity.set(undefined);

            // Verify that calling the function doesn't throw an error
            expect(() => accountService.setUserAcceptedExternalLLMUsage()).not.toThrow();
        });

        it('should clear externalLLMUsageAccepted when accepted is false', () => {
            // Setup user identity with an existing date
            accountService.userIdentity.set({ id: 1, groups: ['USER'] } as User);
            accountService.setUserAcceptedExternalLLMUsage(true);
            expect(accountService.userIdentity()?.externalLLMUsageAccepted).toBeDefined();

            // Clear it
            accountService.setUserAcceptedExternalLLMUsage(false);

            expect(accountService.userIdentity()?.externalLLMUsageAccepted).toBeUndefined();
        });
    });

    describe('test save', () => {
        it('should save user data', () => {
            const userToSave = { id: 1, login: 'testuser' } as User;

            accountService.save(userToSave).subscribe((response) => {
                expect(response.body).toEqual(userToSave);
            });

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/core/account' });
            req.flush(userToSave);
        });
    });

    describe('test setImageUrl', () => {
        it('should set image url when user identity exists', () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'] } as User);

            accountService.setImageUrl('new-image.png');

            expect(accountService.userIdentity()?.imageUrl).toBe('new-image.png');
        });

        it('should not throw error when user identity is undefined', () => {
            accountService.userIdentity.set(undefined);

            expect(() => accountService.setImageUrl('new-image.png')).not.toThrow();
            expect(accountService.userIdentity()).toBeUndefined();
        });
    });

    describe('test syncGroups', () => {
        it('should not throw error when user identity is undefined', () => {
            accountService.userIdentity.set(undefined);

            expect(() => accountService.syncGroups(['USER', 'ADMIN'])).not.toThrow();
            expect(accountService.userIdentity()).toBeUndefined();
        });
    });

    describe('test isSuperAdmin', () => {
        it('should return false if user is not super admin', () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'], authorities: [Authority.STUDENT] } as User);

            expect(accountService.isSuperAdmin()).toBe(false);
        });

        it('should return true if user is super admin', () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'], authorities: [Authority.SUPER_ADMIN] } as User);

            expect(accountService.isSuperAdmin()).toBe(true);
        });
    });

    describe('test getAuthenticationState', () => {
        it('should return observable of user', () => {
            accountService.userIdentity.set(user);

            accountService.getAuthenticationState().subscribe((userState) => {
                expect(userState).toEqual(user);
            });
        });

        it('should return an observable from the authentication state', () => {
            const authState$ = accountService.getAuthenticationState();

            expect(authState$).toBeDefined();
            expect(typeof authState$.subscribe).toBe('function');
        });
    });

    describe('test hasAuthority with identity', () => {
        it('should resolve true when user has the authority', async () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'], authorities: [Authority.ADMIN] } as User);

            const result = await accountService.hasAuthority(Authority.ADMIN);

            expect(result).toBe(true);
        });

        it('should resolve false when user does not have the authority', async () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'], authorities: [Authority.STUDENT] } as User);

            const result = await accountService.hasAuthority(Authority.ADMIN);

            expect(result).toBe(false);
        });
    });

    describe('test identity with error handling', () => {
        it('should set userIdentity to undefined on fetch error', async () => {
            const identityPromise = accountService.identity();

            const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
            req.error(new ProgressEvent('error'));

            const userReceived = await identityPromise;

            expect(userReceived).toBeUndefined();
            expect(accountService.userIdentity()).toBeUndefined();
        });

        it('should set userIdentity to undefined when response body is null', async () => {
            const identityPromise = accountService.identity();

            const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
            req.flush(null);

            const userReceived = await identityPromise;

            expect(userReceived).toBeUndefined();
            expect(accountService.userIdentity()).toBeUndefined();
        });

        it('should set language from session storage when user has no langKey', async () => {
            const sessionStorage = TestBed.inject(SessionStorageService);
            vi.spyOn(sessionStorage, 'retrieve').mockReturnValue('de');

            const translateService = TestBed.inject(TranslateService);
            const translateUseSpy = vi.spyOn(translateService, 'use');

            const userWithoutLang = { id: 1, groups: ['USER'] } as User;
            const identityPromise = accountService.identity();

            const req = httpMock.expectOne({ method: 'GET', url: getUserUrl });
            req.flush(userWithoutLang);

            await identityPromise;

            expect(translateUseSpy).toHaveBeenCalledWith('de');
        });
    });

    describe('test isOwnerOfParticipation edge cases', () => {
        it('should throw error when participation has no student and no team', () => {
            const participation = {} as StudentParticipation;
            accountService.userIdentity.set({ login: 'testuser' } as User);

            expect(() => accountService.isOwnerOfParticipation(participation)).toThrow('Participation does not have any owners');
        });
    });

    describe('test computed properties', () => {
        it('should return askToSetupPasskey as false when user is undefined', () => {
            accountService.userIdentity.set(undefined);
            expect(accountService.askToSetupPasskey()).toBe(false);
        });

        it('should return askToSetupPasskey as true when user has it set', () => {
            accountService.userIdentity.set({ id: 1, askToSetupPasskey: true } as User);
            expect(accountService.askToSetupPasskey()).toBe(true);
        });

        it('should return isLoggedInWithPasskey as false when user is undefined', () => {
            accountService.userIdentity.set(undefined);
            expect(accountService.isLoggedInWithPasskey()).toBe(false);
        });

        it('should return isLoggedInWithPasskey as true when user logged in with passkey', () => {
            accountService.userIdentity.set({ id: 1, loggedInWithPasskey: true } as User);
            expect(accountService.isLoggedInWithPasskey()).toBe(true);
        });

        it('should return isPasskeySuperAdminApproved as false when user is undefined', () => {
            accountService.userIdentity.set(undefined);
            expect(accountService.isPasskeySuperAdminApproved()).toBe(false);
        });

        it('should return isPasskeySuperAdminApproved as true when passkey is approved', () => {
            accountService.userIdentity.set({ id: 1, passkeySuperAdminApproved: true } as User);
            expect(accountService.isPasskeySuperAdminApproved()).toBe(true);
        });

        it('should return isUserLoggedInWithApprovedPasskey as false when either condition is false', () => {
            accountService.userIdentity.set({ id: 1, loggedInWithPasskey: true, passkeySuperAdminApproved: false } as User);
            expect(accountService.isUserLoggedInWithApprovedPasskey()).toBe(false);

            accountService.userIdentity.set({ id: 1, loggedInWithPasskey: false, passkeySuperAdminApproved: true } as User);
            expect(accountService.isUserLoggedInWithApprovedPasskey()).toBe(false);
        });

        it('should return isUserLoggedInWithApprovedPasskey as true when both conditions are true', () => {
            accountService.userIdentity.set({ id: 1, loggedInWithPasskey: true, passkeySuperAdminApproved: true } as User);
            expect(accountService.isUserLoggedInWithApprovedPasskey()).toBe(true);
        });
    });

    describe('test setUserEnabledMemiris', () => {
        it('should update user memiris setting on success', () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'], memirisEnabled: false } as User);

            accountService.setUserEnabledMemiris(true);

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/core/account/enable-memiris' });
            req.flush({});

            expect(accountService.userIdentity()?.memirisEnabled).toBe(true);
        });

        it('should not update user when user identity is undefined', () => {
            accountService.userIdentity.set(undefined);

            accountService.setUserEnabledMemiris(true);

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/core/account/enable-memiris' });
            req.flush({});

            expect(accountService.userIdentity()).toBeUndefined();
        });

        it('should handle error gracefully', () => {
            accountService.userIdentity.set({ id: 1, groups: ['USER'], memirisEnabled: false } as User);

            accountService.setUserEnabledMemiris(true);

            const req = httpMock.expectOne({ method: 'PUT', url: 'api/core/account/enable-memiris' });
            req.error(new ProgressEvent('error'));

            // User should remain unchanged
            expect(accountService.userIdentity()?.memirisEnabled).toBe(false);
        });
    });

    describe('test getToolToken', () => {
        it('should get tool token', () => {
            const expectedToken = 'test-tool-token';

            accountService.getToolToken('vscode').subscribe((token) => {
                expect(token).toBe(expectedToken);
            });

            const req = httpMock.expectOne({ method: 'POST', url: 'api/core/tool-token?tool=vscode' });
            req.flush(expectedToken);
        });
    });

    describe('test setAccessRightsForExerciseAndReferencedCourse edge cases', () => {
        it('should handle exercise without course', () => {
            accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

            const exerciseWithoutCourse = {} as Exercise;
            accountService.setAccessRightsForExerciseAndReferencedCourse(exerciseWithoutCourse);

            expect(exerciseWithoutCourse.isAtLeastTutor).toBe(true);
            expect(exerciseWithoutCourse.isAtLeastEditor).toBe(true);
            expect(exerciseWithoutCourse.isAtLeastInstructor).toBe(true);
        });
    });

    describe('test setAccessRightsForCourseAndReferencedExercises edge cases', () => {
        it('should handle course without exercises', () => {
            accountService.userIdentity.set({ id: 10, groups: ['INSTRUCTOR'], authorities } as User);

            const courseWithoutExercises = {
                instructorGroupName: 'INSTRUCTOR',
            } as Course;
            accountService.setAccessRightsForCourseAndReferencedExercises(courseWithoutExercises);

            expect(courseWithoutExercises.isAtLeastInstructor).toBe(true);
        });
    });

    describe('test hasGroup edge cases', () => {
        it('should return false when group is undefined', () => {
            accountService.userIdentity.set({ id: 10, groups: ['USER'], authorities } as User);

            const result = accountService.hasGroup(undefined);

            expect(result).toBe(false);
        });
    });
});
