import type { ReactNode } from 'react';

export type AudienceRole = 'student' | 'instructor' | 'developer' | 'administrator';

interface HomepageAudienceIconProps {
    role: AudienceRole;
}

const iconPaths: Record<AudienceRole, ReactNode> = {
    student: (
        <>
            <path d="m2 10 10-5 10 5-10 5Z" />
            <path d="M6 12.5V17c3.3 2.7 8.7 2.7 12 0v-4.5" />
            <path d="M22 10v6" />
        </>
    ),
    instructor: (
        <>
            <rect x="3" y="3" width="18" height="14" rx="2" />
            <path d="M8 8h8M8 12h5M12 17v4M8 21h8" />
        </>
    ),
    developer: <path d="m8 8-4 4 4 4M16 8l4 4-4 4M14 5l-4 14" />,
    administrator: (
        <>
            <rect x="3" y="4" width="18" height="6" rx="2" />
            <rect x="3" y="14" width="18" height="6" rx="2" />
            <path d="M7 7h.01M7 17h.01M11 7h6M11 17h6" />
        </>
    ),
};

export default function HomepageAudienceIcon({ role }: HomepageAudienceIconProps): ReactNode {
    return (
        <svg viewBox="0 0 24 24" aria-hidden="true" focusable="false">
            {iconPaths[role]}
        </svg>
    );
}
