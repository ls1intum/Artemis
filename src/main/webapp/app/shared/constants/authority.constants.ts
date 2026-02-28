export enum Authority {
    SUPER_ADMIN = 'ROLE_SUPER_ADMIN',
    ADMIN = 'ROLE_ADMIN',
    INSTRUCTOR = 'ROLE_INSTRUCTOR',
    EDITOR = 'ROLE_EDITOR',
    TUTOR = 'ROLE_TA',
    STUDENT = 'ROLE_USER',
}

export const IS_AT_LEAST_SUPER_ADMIN: readonly Authority[] = [Authority.SUPER_ADMIN];
export const IS_AT_LEAST_ADMIN: readonly Authority[] = [Authority.SUPER_ADMIN, Authority.ADMIN];
export const IS_AT_LEAST_INSTRUCTOR: readonly Authority[] = [Authority.SUPER_ADMIN, Authority.ADMIN, Authority.INSTRUCTOR];
export const IS_AT_LEAST_EDITOR: readonly Authority[] = [Authority.SUPER_ADMIN, Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR];
export const IS_AT_LEAST_TUTOR: readonly Authority[] = [Authority.SUPER_ADMIN, Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TUTOR];
export const IS_AT_LEAST_STUDENT: readonly Authority[] = [Authority.SUPER_ADMIN, Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TUTOR, Authority.STUDENT];
