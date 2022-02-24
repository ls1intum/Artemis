import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { MockWebsocketService } from '../helpers/mocks/service/mock-websocket.service';
import { MockHttpService } from '../helpers/mocks/service/mock-http.service';
import { MockFeatureToggleService } from '../helpers/mocks/service/mock-feature-toggle.service';
import { User } from 'app/core/user/user.model';
import { AccountService } from 'app/core/auth/account.service';
import { MockSyncStorage } from '../helpers/mocks/service/mock-sync-storage.service';
import { TranslateService } from '@ngx-translate/core';
import { MockTranslateService } from '../helpers/mocks/service/mock-translate.service';
import { HttpClientTestingModule } from '@angular/common/http/testing';
import { Authority } from 'app/shared/constants/authority.constants';
import { Course } from 'app/entities/course.model';
import { Exercise } from 'app/entities/exercise.model';
import { Participation } from 'app/entities/participation/participation.model';
import { Team } from 'app/entities/team.model';

describe('AccountService', () => {
    let accountService: AccountService;
    let httpService: MockHttpService;
    let getStub: jest.SpyInstance;
    let translateService: TranslateService;

    const getUserUrl = 'api/account';
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
            imports: [HttpClientTestingModule],
            providers: [{ provide: TranslateService, useClass: MockTranslateService }],
        });
        httpService = new MockHttpService();
        translateService = TestBed.inject(TranslateService);
        // @ts-ignore
        accountService = new AccountService(translateService, new MockSyncStorage(), httpService, new MockWebsocketService(), new MockFeatureToggleService());
        getStub = jest.spyOn(httpService, 'get');

        expect(accountService.userIdentity).toBe(undefined);
        expect(accountService.isAuthenticated()).toBe(false);
    });

    afterEach(() => {
        jest.restoreAllMocks();
    });

    it('should fetch the user on identity if the userIdentity is not defined yet', async () => {
        getStub.mockReturnValue(of({ body: user }));

        const userReceived = await accountService.identity(false);

        expect(getStub).toHaveBeenCalledTimes(1);
        expect(getStub).toHaveBeenCalledWith(getUserUrl, { observe: 'response' });
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should fetch the user on identity if the userIdentity is defined yet (force=true)', async () => {
        accountService.userIdentity = user;
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
        getStub.mockReturnValue(of({ body: user2 }));

        const userReceived = await accountService.identity(true);

        expect(getStub).toHaveBeenCalledTimes(1);
        expect(getStub).toHaveBeenCalledWith(getUserUrl, { observe: 'response' });
        expect(userReceived).toEqual(user2);
        expect(accountService.userIdentity).toEqual(user2);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should NOT fetch the user on identity if the userIdentity is defined (force=false)', async () => {
        accountService.userIdentity = user;
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
        getStub.mockReturnValue(of({ body: user2 }));

        const userReceived = await accountService.identity(false);

        expect(getStub).not.toHaveBeenCalled();
        expect(userReceived).toEqual(user);
        expect(accountService.userIdentity).toEqual(user);
        expect(accountService.isAuthenticated()).toBe(true);
    });

    it('should authenticate a user', () => {
        accountService.userIdentity = undefined;

        accountService.authenticate(user);

        expect(accountService.userIdentity).toEqual(user);
    });

    it('should sync user groups', () => {
        accountService.userIdentity = user;

        accountService.syncGroups(user3);

        expect(accountService.userIdentity.groups).toEqual(['USER', 'TA']);
    });

    describe('test authority check', () => {
        const usedAuthorities: Authority[] = [];
        it.each(authorities)('should return false if not authenticated, no user id and no authorities are set', async (authority: Authority) => {
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBe(false);
        });

        it.each(authorities)('should return false if authenticated, user id but no authorities are set', async (authority: Authority) => {
            accountService.userIdentity = user;
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBe(false);
        });

        it.each(authorities)('should return true if user is required', async (authority: Authority) => {
            accountService.userIdentity = user3;
            usedAuthorities.push(authority);

            await expect(accountService.hasAnyAuthority(usedAuthorities)).resolves.toBe(true);
        });

        it.each(authorities)('should return true if authority matches exactly', async (authority: Authority) => {
            accountService.userIdentity = { id: authorities.indexOf(authority), groups: ['USER'], authorities: [authority] } as User;

            await expect(accountService.hasAnyAuthority([authority])).resolves.toBe(true);
        });

        it.each(authorities)('should return false if authority does not match', async (authority: Authority) => {
            const index = authorities.indexOf(authority);
            accountService.userIdentity = { id: index + 1, groups: ['USER'], authorities: [authorities[(index + 1) % 5]] } as User;

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
            accountService.userIdentity = user;
            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return false if no groups are set', (group: string) => {
            accountService.userIdentity = { id: 10, authorities } as User;
            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return false if group does not match', (group: string) => {
            const index = groups.indexOf(group);
            accountService.userIdentity = { id: 10, groups: [groups[index + (1 % 3)]], authorities } as User;

            result = accountService.hasGroup(group);

            expect(result).toBe(false);
        });

        it.each(groups)('should return true if group matchs', (group: string) => {
            accountService.userIdentity = { id: 10, groups: [group], authorities } as User;

            result = accountService.hasGroup(group);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastTutorInCourse', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity = user2;

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBe(false);
        });

        it.each(['TA', 'EDITOR', 'INSTRUCTOR'])('should return true if user is tutor, editor or instructor', (group: string) => {
            accountService.userIdentity = { id: 10, groups: [group], authorities } as User;

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAtLeastTutorInCourse(course);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastEditorInCourse', () => {
        it('should return false if user is not editor', () => {
            accountService.userIdentity = user2;

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBe(false);
        });

        it.each(['EDITOR', 'INSTRUCTOR'])('should return true if user is editor or instructor', (group: string) => {
            accountService.userIdentity = { id: 10, groups: [group], authorities } as User;

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAtLeastEditorInCourse(course);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastInstructorInCourse', () => {
        it('should return false if user is not editor', () => {
            accountService.userIdentity = user2;

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBe(false);
        });

        it('should return true if user is instructor', () => {
            accountService.userIdentity = { id: 10, groups: ['INSTRUCTOR'], authorities } as User;

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAtLeastInstructorInCourse(course);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastTutorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity = user2;

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBe(false);

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBe(false);
        });

        it.each(['TA', 'EDITOR', 'INSTRUCTOR'])('should return true if user is tutor, editor or instructor', (group: string) => {
            accountService.userIdentity = { id: 10, groups: [group], authorities } as User;

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAtLeastTutorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastTutorForExercise(examExercise);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastEditorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity = user2;

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBe(false);

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBe(false);
        });

        it.each(['EDITOR', 'INSTRUCTOR'])('should return true if user is editor or instructor', (group: string) => {
            accountService.userIdentity = { id: 10, groups: [group], authorities } as User;

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAtLeastEditorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastEditorForExercise(examExercise);

            expect(result).toBe(true);
        });
    });

    describe('test isAtLeastInstructorForExercise', () => {
        it('should return false if user is not tutor', () => {
            accountService.userIdentity = user2;

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBe(false);

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBe(false);
        });

        it('should return true if user is editor or instructor', () => {
            accountService.userIdentity = { id: 10, groups: ['INSTRUCTOR'], authorities } as User;

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBe(true);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAtLeastInstructorForExercise(exercise);

            expect(result).toBe(true);

            result = accountService.isAtLeastInstructorForExercise(examExercise);

            expect(result).toBe(true);
        });
    });

    describe('test isAdmin', () => {
        it('should return false if user is not admin', () => {
            accountService.userIdentity = user2;

            result = accountService.isAdmin();

            expect(result).toBe(false);
        });

        it('should return true if user is system admin', () => {
            accountService.userIdentity = { id: 10, groups: ['USER'], authorities } as User;

            result = accountService.isAdmin();

            expect(result).toBe(true);
        });
    });

    it('should set access rights for referenced course', () => {
        accountService.userIdentity = { id: 10, groups: ['INSTRUCTOR'], authorities } as User;

        accountService.setAccessRightsForExerciseAndReferencedCourse(exercise);

        expect(exercise.isAtLeastEditor).toBe(true);
        expect(exercise.isAtLeastEditor).toBe(true);
        expect(exercise.isAtLeastInstructor).toBe(true);
        expect(exercise.course!.isAtLeastEditor).toBe(true);
        expect(exercise.course!.isAtLeastEditor).toBe(true);
        expect(exercise.course!.isAtLeastInstructor).toBe(true);
    });

    it('should set access rights for referenced exercise', () => {
        course.exercises = [exercise];
        accountService.userIdentity = { id: 10, groups: ['INSTRUCTOR'], authorities } as User;

        accountService.setAccessRightsForCourseAndReferencedExercises(course);

        expect(exercise.isAtLeastEditor).toBe(true);
        expect(exercise.isAtLeastEditor).toBe(true);
        expect(exercise.isAtLeastInstructor).toBe(true);
        expect(exercise.course!.isAtLeastEditor).toBe(true);
        expect(exercise.course!.isAtLeastEditor).toBe(true);
        expect(exercise.course!.isAtLeastInstructor).toBe(true);
    });

    describe('test isOwnerOfParticipation', () => {
        let participation: Participation;
        const user4 = { login: 'user' } as User;
        const team = { students: [user4] } as Team;
        it('should return false if student is not owner', () => {
            participation = { student: user } as Participation;
            accountService.userIdentity = user4;

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(false);
        });

        it('should return true if student is owner', () => {
            participation = { student: user4 } as Participation;
            accountService.userIdentity = user4;

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(true);
        });

        it('should return false if student is not part of the team', () => {
            participation = { team } as Participation;
            accountService.userIdentity = user;

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(false);
        });

        it('should return true if student is part of the team', () => {
            participation = { team } as Participation;
            accountService.userIdentity = user4;

            result = accountService.isOwnerOfParticipation(participation);

            expect(result).toBe(true);
        });
    });

    describe('test getImageUrl', () => {
        let url: string | undefined;
        it('should return undefined if not authenticated', () => {
            url = accountService.getImageUrl();

            expect(url).toBe(undefined);
        });

        it('should return image url if authenticated', () => {
            const expectedUrl = 'www.examp.le';
            accountService.userIdentity = { imageUrl: expectedUrl } as User;

            url = accountService.getImageUrl();

            expect(url).toBe(expectedUrl);
        });
    });
});
