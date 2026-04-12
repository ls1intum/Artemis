import type { ReactNode } from 'react';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';

import type { HighlightCardData } from './data/types';
import styles from './LmsComparison.module.css';

interface HighlightCardsProps {
    cards: HighlightCardData[];
}

export default function HighlightCards({ cards }: HighlightCardsProps): ReactNode {
    return (
        <div className={styles.highlightGrid}>
            {cards.map((card) => (
                <div key={card.title} className={styles.highlightCard} style={{ borderLeftColor: card.borderColor }}>
                    <h3 className={styles.highlightTitle}>
                        <FontAwesomeIcon icon={card.icon} className={styles.highlightIcon} />
                        {card.title}
                    </h3>
                    <p className={styles.highlightDesc}>{card.description}</p>
                </div>
            ))}
        </div>
    );
}
