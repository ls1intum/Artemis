export enum Authority {
    ADMIN = 'ROLE_ADMIN',
    INSTRUCTOR = 'ROLE_INSTRUCTOR',
    EDITOR = 'ROLE_EDITOR',
    TA = 'ROLE_TA',
    USER = 'ROLE_USER',
}

export const IS_AT_LEAST_TUTOR: readonly Authority[] = [Authority.ADMIN, Authority.INSTRUCTOR, Authority.EDITOR, Authority.TA];
