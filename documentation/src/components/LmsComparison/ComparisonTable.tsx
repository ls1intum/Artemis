import type { ReactNode } from 'react';

import type { FeatureCategory } from './data/types';
import { PlatformId, SupportLevel } from './data/types';
import CategoryHeader from './CategoryHeader';
import FeatureRow from './FeatureRow';
import SupportBadge from './SupportBadge';
import styles from './LmsComparison.module.css';

interface ComparisonTableProps {
    platforms: [PlatformId, PlatformId, PlatformId];
    categories: FeatureCategory[];
}

export default function ComparisonTable({ platforms, categories }: ComparisonTableProps): ReactNode {
    return (
        <div className={styles.tableContainer}>
            <div className={styles.table}>
                {categories.map((category) => (
                    <div key={category.id}>
                        <CategoryHeader name={category.name} icon={category.icon} />
                        {category.features.map((feature, index) => (
                            <FeatureRow key={feature.id} feature={feature} platforms={platforms} isEven={index % 2 === 0} />
                        ))}
                    </div>
                ))}

                <div className={styles.legend}>
                    <SupportBadge level={SupportLevel.Supported} />
                    <SupportBadge level={SupportLevel.Partial} />
                    <SupportBadge level={SupportLevel.None} />
                </div>
            </div>
        </div>
    );
}
