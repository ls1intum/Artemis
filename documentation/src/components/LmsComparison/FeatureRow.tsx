import type { ReactNode } from 'react';

import type { Feature } from './data/types';
import { PlatformId } from './data/types';
import SupportBadge from './SupportBadge';
import styles from './LmsComparison.module.css';

interface FeatureRowProps {
    feature: Feature;
    platforms: [PlatformId, PlatformId, PlatformId];
    isEven: boolean;
}

export default function FeatureRow({ feature, platforms, isEven }: FeatureRowProps): ReactNode {
    return (
        <div className={`${styles.featureRow} ${isEven ? styles.featureRowEven : ''}`}>
            <div className={styles.featureName} title={feature.tooltip}>
                {feature.name}
            </div>
            {platforms.map((pid) => (
                <div key={pid} className={styles.featureCell}>
                    <SupportBadge level={feature.support[pid]} note={feature.notes?.[pid]} />
                </div>
            ))}
        </div>
    );
}
