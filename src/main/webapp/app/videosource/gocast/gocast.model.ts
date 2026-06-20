/**
 * Models for the gocast (TUM Live) integration.
 * These mirror the Java DTOs: GocastCourseDTO, GocastStreamDTO, GocastPlaybackTokenDTO, GocastBindingDTO.
 */

export interface GocastCourse {
    id: number;
    name: string;
    slug: string;
    year: number;
    teachingTerm: string;
    vodEnabled: boolean;
    visibility: string;
}

export interface GocastStream {
    streamId: number;
    name: string;
    private: boolean;
    start?: string;
    end?: string;
}

export type GocastBindingStatus = 'PENDING' | 'ACTIVE' | 'REVOKED';

export interface GocastBinding {
    courseId: number;
    gocastCourseId: number;
    gocastCourseSlug: string;
    status: GocastBindingStatus;
    approvalUrl?: string;
}

export interface GocastPlaybackToken {
    playlistUrl?: string;
    playlistUrlPres?: string;
    playlistUrlCam?: string;
    expiresIn: number;
}
