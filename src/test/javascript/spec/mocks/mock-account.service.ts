import { of, Observable } from 'rxjs';
import { Course } from 'app/entities/course.model';
import { IAccountService } from 'app/core/auth/account.service';
import { User } from 'app/core/user/user.model';

export class MockAccountService implements IAccountService {
    identity = () => Promise.resolve({ id: 99 } as User);
    hasAnyAuthority = (authorities: any[]) => Promise.resolve(true);
    hasAnyAuthorityDirect = (authorities: any[]) => true;
    getAuthenticationState = () => of({ id: 99 } as User);
    isAtLeastInstructorInCourse = (course: Course) => true;
    authenticate = (identity: User | null) => {};
    fetch = () => of({ body: { id: 99 } as User } as any);
    getImageUrl = () => 'blob';
    hasAuthority = (authority: string) => Promise.resolve(true);
    isAtLeastTutorInCourse = (course: Course) => true;
    isAuthenticated = () => true;
    save = (account: any) => ({} as any);
}
