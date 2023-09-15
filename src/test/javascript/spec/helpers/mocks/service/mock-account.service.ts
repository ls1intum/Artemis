import { of } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { IAccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';
import { Exercise } from 'app/entities/exercise.model';

export class MockAccountService implements IAccountService {
    userIdentityValue: User | undefined;

    get userIdentity() {
        return this.userIdentityValue;
    }
    set userIdentity(user: User | undefined) {
        this.userIdentityValue = user;
    }

    identity = () => Promise.resolve({ id: 99, login: 'admin' } as User);
    getAndClearPrefilledUsername = () => 'prefilledUsername';
    setPrefilledUsername = (username: string) => ({});
    hasAnyAuthority = (authorities: any[]) => Promise.resolve(true);
    hasAnyAuthorityDirect = (authorities: any[]) => authorities.length !== 0;
    getAuthenticationState = () => of({ id: 99 } as User);
    authenticate = (identity: User | undefined) => {};
    fetch = () => of({ body: { id: 99 } as User } as any);
    updateLanguage = (languageKey: string) => of({});
    getImageUrl = () => 'blob';
    hasAuthority = (authority: string) => Promise.resolve(true);
    isAtLeastTutorInCourse = (course: Course) => true;
    isAtLeastEditorInCourse = (course: Course) => course.isAtLeastEditor!;
    isAtLeastInstructorInCourse = (course: Course) => course.isAtLeastInstructor!;
    isAtLeastTutorForExercise = (exercise?: Exercise) => true;
    isAtLeastEditorForExercise = (exercise?: Exercise) => true;
    isAtLeastInstructorForExercise = (exercise?: Exercise) => true;
    setAccessRightsForExercise = (exercise?: Exercise) => ({}) as any;
    setAccessRightsForCourse = (course?: Course) => ({}) as any;
    setAccessRightsForExerciseAndReferencedCourse = (exercise?: Exercise) => {};
    setAccessRightsForCourseAndReferencedExercises = (course?: Course) => {};
    isAuthenticated = () => true;
    isOwnerOfParticipation = () => true;
    isAdmin = () => true;
    save = (account: any) => ({}) as any;
}
