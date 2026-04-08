import type { ReactNode } from 'react';
import clsx from 'clsx';

import { PlatformId } from './data/types';
import { platforms, selectablePlatforms } from './data/comparisonData';
import styles from './LmsComparison.module.css';

interface PlatformSelectorProps {
    selected: [PlatformId, PlatformId];
    onChange: (next: [PlatformId, PlatformId]) => void;
    isSticky: boolean;
}

export default function PlatformSelector({ selected, onChange, isSticky }: PlatformSelectorProps): ReactNode {
    return (
        <div className={clsx(styles.selectorRow, isSticky && styles.selectorSticky)}>
            <div className={styles.selectorLabel}>Feature</div>
            <div className={styles.selectorPlatform}>
                <span className={styles.artemisName}>Artemis</span>
            </div>
            <div className={styles.selectorPlatform}>
                <select
                    aria-label="Select first comparison platform"
                    className={styles.platformSelect}
                    value={selected[0]}
                    onChange={(e) => onChange([e.target.value as PlatformId, selected[1]])}
                >
                    {selectablePlatforms.map((pid) => (
                        <option key={pid} value={pid} disabled={pid === selected[1]}>
                            {platforms[pid].name}
                        </option>
                    ))}
                </select>
            </div>
            <div className={styles.selectorPlatform}>
                <select
                    aria-label="Select second comparison platform"
                    className={styles.platformSelect}
                    value={selected[1]}
                    onChange={(e) => onChange([selected[0], e.target.value as PlatformId])}
                >
                    {selectablePlatforms.map((pid) => (
                        <option key={pid} value={pid} disabled={pid === selected[0]}>
                            {platforms[pid].name}
                        </option>
                    ))}
                </select>
            </div>
        </div>
    );
}
