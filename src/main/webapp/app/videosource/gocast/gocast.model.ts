export type GocastBindingStatus = 'UNLINKED' | 'PENDING' | 'EXPIRED' | 'ACTIVE' | 'REVOKED';

export interface GocastBinding {
    available: boolean;
    status: GocastBindingStatus;
    courseId?: number;
    courseName?: string;
    courseSlug?: string;
    courseVisibility?: 'public' | 'hidden' | 'loggedin' | 'enrolled';
    expiresAt?: string;
    upstreamUnavailable?: boolean;
}

export interface GocastApprovalStart {
    approvalUrl: string;
    expiresAt: string;
}
