export enum Authority {
    ADMIN = 'ROLE_ADMIN',
    INSTRUCTOR = 'ROLE_INSTRUCTOR',
    EDITOR = 'ROLE_EDITOR',
    TUTOR = 'ROLE_TA',
    STUDENT = 'ROLE_USER',
}

export const IS_AT_LEAST_ADMIN: readonly Authority[] = [Authority.ADMIN];
export const IS_AT_LEAST_INSTRUCTOR: readonly Authority[] = [Authority.INSTRUCTOR, ...IS_AT_LEAST_ADMIN];
export const IS_AT_LEAST_EDITOR: readonly Authority[] = [Authority.EDITOR, ...IS_AT_LEAST_INSTRUCTOR];
export const IS_AT_LEAST_TUTOR: readonly Authority[] = [Authority.TUTOR, ...IS_AT_LEAST_EDITOR];
export const IS_AT_LEAST_STUDENT: readonly Authority[] = [Authority.STUDENT, ...IS_AT_LEAST_TUTOR];
