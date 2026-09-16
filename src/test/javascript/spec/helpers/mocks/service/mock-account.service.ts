import { Observable, of } from 'rxjs';
import { Course } from 'app/course/shared/entities/course.model';
import { IAccountService } from 'app/core/auth/account.service';
import { User } from 'app/account/user/user.model';
import { Exercise } from 'app/exercise/shared/entities/exercise/exercise.model';
import { signal } from '@angular/core';
import dayjs from 'dayjs/esm';
import { deepClone } from 'app/foundation/util/deep-clone.util';
import { LLMSelectionDecision } from 'app/account/user/shared/dto/updateLLMSelectionDecision.dto';
import { Authority } from 'app/foundation/constants/authority.constants';

export class MockAccountService implements IAccountService {
    userIdentity = signal<User | undefined>(undefined);

    identity = () => Promise.resolve({ id: 99, login: 'admin' } as User);
    getAndClearPrefilledUsername = () => 'prefilledUsername';
    setPrefilledUsername = (username: string) => ({});
    hasAnyAuthority = (authorities: readonly Authority[]) => Promise.resolve(true);
    hasAnyAuthorityDirect = (authorities: readonly Authority[]) => authorities.length !== 0;
    getAuthenticationState: () => Observable<User | undefined> = () => of({ id: 99 } as User);
    authenticate = (identity: User | undefined) => {};
    fetch = () => of({ body: { id: 99 } as User } as any);
    updateLanguage = (languageKey: string) => of({});
    getImageUrl = () => 'blob';
    hasAuthority = (authority: string) => Promise.resolve(true);
    isAtLeastTutor = () => this.hasAnyAuthorityDirect([Authority.TUTOR]);
    isAtLeastTutorInCourse = (course: Course) => true;
    isAtLeastTutorInCourseWithId = (courseId?: number) => true;
    isAtLeastEditorInCourse = (course?: Course) => course?.isAtLeastEditor ?? false;
    isAtLeastEditorInCourseWithId = (courseId?: number) => true;
    isAtLeastInstructorInCourse = (course?: Course) => course?.isAtLeastInstructor ?? false;
    isAtLeastInstructorInCourseWithId = (courseId?: number) => true;
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
    getVcsAccessToken = (participationId: number) => of();
    createVcsAccessToken = (participationId: number) => of();
    getToolToken = () => of();
    setUserEnabledMemiris = (enabled: boolean) => of();
    setUserAcceptedExternalLLMUsage = (accepted: boolean) => of();
    // Both mirror the real service so specs can observe the cached decision (the Iris chatbot gates its
    // AI-selection modal on it). restore… keeps an absent timestamp absent, exactly like production.
    // Caveat: a spec that stubs reads via `vi.spyOn(accountService, 'userIdentity')` will not see these
    // writes — they land on the real signal while reads come from the spy. Set `userIdentity` directly
    // instead when the spec needs to observe a decision change.
    setUserLLMSelectionDecision = (accepted: LLMSelectionDecision) => this.applyLLMSelectionDecision(accepted, dayjs());

    restoreUserLLMSelectionDecision = (accepted: LLMSelectionDecision | undefined, timestamp: dayjs.Dayjs | undefined) => this.applyLLMSelectionDecision(accepted, timestamp);

    private applyLLMSelectionDecision = (accepted: LLMSelectionDecision | undefined, timestamp: dayjs.Dayjs | undefined) => {
        this.userIdentity.update((currentUserIdentity) => {
            if (!currentUserIdentity) {
                return currentUserIdentity;
            }
            const updatedUserIdentity = deepClone(currentUserIdentity);
            updatedUserIdentity.selectedLLMUsage = accepted;
            updatedUserIdentity.selectedLLMUsageTimestamp = timestamp;
            return updatedUserIdentity;
        });
    };

    askToSetupPasskey = () => false;
    isLoggedInWithPasskey = () => true;
    isPasskeySuperAdminApproved = () => true;
    isUserLoggedInWithApprovedPasskey = () => true;
}
