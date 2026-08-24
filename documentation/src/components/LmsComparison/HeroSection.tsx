import type { ReactNode } from 'react';
import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';

import styles from './LmsComparison.module.css';

export default function HeroSection(): ReactNode {
    return (
        <header className={styles.hero}>
            <Heading as="h1" className={styles.heroTitle}>
                How Does Artemis Compare?
            </Heading>
            <p className={styles.heroSubtitle}>See how Artemis measures up against other learning management systems used in universities</p>
            <p className={styles.heroNote}>
                This comparison is maintained by the Artemis project and describes capabilities that ship with each platform by default, not what a plugin or a paid add-on can add.
                Entries for other platforms are based on their public documentation and can lag behind their releases. For Artemis, security, privacy, accessibility, and AI data
                processing are documented in detail under <Link to="/about/trust">Trust &amp; Transparency</Link>.
            </p>
        </header>
    );
}
