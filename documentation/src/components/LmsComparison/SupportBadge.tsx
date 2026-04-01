import type { ReactNode } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleCheck, faCircleExclamation, faCircleXmark } from '@fortawesome/free-solid-svg-icons';

import { SupportLevel } from './data/types';
import styles from './LmsComparison.module.css';

const config = {
    [SupportLevel.Supported]: { icon: faCircleCheck, label: 'Supported', className: styles.supportSupported },
    [SupportLevel.Partial]: { icon: faCircleExclamation, label: 'Limited', className: styles.supportPartial },
    [SupportLevel.None]: { icon: faCircleXmark, label: 'Not available', className: styles.supportNone },
};

interface SupportBadgeProps {
    level: SupportLevel;
    note?: string;
}

export default function SupportBadge({ level, note }: SupportBadgeProps): ReactNode {
    const { icon, label, className } = config[level];

    return (
        <span className={`${styles.badge} ${className}`} title={note}>
            <FontAwesomeIcon icon={icon} />
            <span className={styles.badgeLabel}>{note ?? label}</span>
        </span>
    );
}
