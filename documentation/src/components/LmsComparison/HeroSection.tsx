import type { ReactNode } from 'react';
import Heading from '@theme/Heading';

import styles from './LmsComparison.module.css';

export default function HeroSection(): ReactNode {
    return (
        <header className={styles.hero}>
            <Heading as="h1" className={styles.heroTitle}>
                How Does Artemis Compare?
            </Heading>
            <p className={styles.heroSubtitle}>See how Artemis measures up against other learning management systems used in universities</p>
        </header>
    );
}
