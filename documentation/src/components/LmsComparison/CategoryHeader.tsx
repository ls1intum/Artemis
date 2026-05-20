import type { ReactNode } from 'react';
import type { IconDefinition } from '@fortawesome/free-solid-svg-icons';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import styles from './LmsComparison.module.css';

interface CategoryHeaderProps {
    name: string;
    icon: IconDefinition;
}

export default function CategoryHeader({ name, icon }: CategoryHeaderProps): ReactNode {
    return (
        <div className={styles.categoryHeader}>
            <FontAwesomeIcon icon={icon} className={styles.categoryIcon} />
            <span>{name}</span>
        </div>
    );
}
