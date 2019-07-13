import { of } from 'rxjs';
import { Course } from 'app/entities/course';
export class MockAccountService {
    identity = () => Promise.resolve({ id: 99 });
    hasAnyAuthority = (authorities: any[]) => Promise.resolve(true);
    hasAnyAuthorityDirect = (authorities: any[]) => Promise.resolve(true);
    getAuthenticationState = () => of();
    isAtLeastInstructorInCourse = (course: Course) => true;
}
