import type { ReactNode } from 'react';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faCircleCheck, faCircleExclamation, faCircleXmark } from '@fortawesome/free-solid-svg-icons';

import { SupportLevel } from './data/types';
import styles from './LmsComparison.module.css';

interface SupportConfig {
    icon: IconDefinition;
    label: string;
    className: string;
}

const config: Record<SupportLevel, SupportConfig> = {
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

    const displayText = note ?? label;

    return (
        <span className={`${styles.badge} ${className}`} title={note} aria-label={note ? `${label}: ${note}` : label}>
            <FontAwesomeIcon icon={icon} aria-hidden="true" />
            <span className={styles.badgeLabel}>{displayText}</span>
        </span>
    );
}
