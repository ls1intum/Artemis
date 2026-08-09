import type { ReactNode } from 'react';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';
import HomepageAudienceIcon, { type AudienceRole } from '../components/HomepageAudienceIcon';
import { SearchModalTrigger } from '../components/SearchModal';

import styles from './index.module.css';

interface AudienceDestination {
    role: AudienceRole;
    title: string;
    description: string;
    to: string;
}

const audienceDestinations: AudienceDestination[] = [
    { role: 'student', title: 'Student', description: 'Learn with Artemis', to: '/student/intro' },
    { role: 'instructor', title: 'Instructor', description: 'Teach with Artemis', to: '/instructor/intro' },
    { role: 'developer', title: 'Developer', description: 'Build Artemis', to: '/developer/intro' },
    { role: 'administrator', title: 'Administrator', description: 'Run Artemis', to: '/admin/intro' },
];

function ArrowIcon(): ReactNode {
    return (
        <svg viewBox="0 0 20 20" aria-hidden="true" focusable="false">
            <path d="M5 15 15 5M7 5h8v8" />
        </svg>
    );
}

function AudienceCard({ destination }: { destination: AudienceDestination }): ReactNode {
    return (
        <Link to={destination.to} className={styles.audienceCard}>
            <span className={styles.cardTop}>
                <span className={styles.iconChip}>
                    <HomepageAudienceIcon role={destination.role} />
                </span>
                <span className={styles.cardArrow}>
                    <ArrowIcon />
                </span>
            </span>
            <span className={styles.cardCopy}>
                <Heading as="h2" className={styles.cardTitle}>
                    {destination.title}
                </Heading>
                <span className={styles.cardDescription}>{destination.description}</span>
            </span>
        </Link>
    );
}

function ArtemisMark(): ReactNode {
    return (
        <svg className={styles.mark} viewBox="72 58 135 112" aria-hidden="true" focusable="false">
            <defs>
                <linearGradient id="homepage-mark-blue" x1="72" y1="170" x2="156" y2="58" gradientUnits="userSpaceOnUse">
                    <stop offset="0" stopColor="#0065bd" />
                    <stop offset="0.58" stopColor="#3070b3" />
                    <stop offset="1" stopColor="#4c8ac6" />
                </linearGradient>
                <linearGradient id="homepage-mark-light" x1="135" y1="115" x2="199" y2="170" gradientUnits="userSpaceOnUse">
                    <stop offset="0" stopColor="#fffffc" />
                    <stop offset="0.62" stopColor="#f5fbff" />
                    <stop offset="1" stopColor="#d9edf9" />
                </linearGradient>
            </defs>
            <path d="M135 58 L72 170 L135 115 L156 95 Z" fill="url(#homepage-mark-blue)" />
            <path d="M156 95 L199 170 L135 115 Z" fill="url(#homepage-mark-light)" />
            <path className={styles.markEdge} d="M135 58 L72 170 L135 115 L199 170 L156 95 Z" />
            <path className={styles.markEdge} d="M135 115 L156 95" />
        </svg>
    );
}

function HomepageContent(): ReactNode {
    return (
        <main className={`${styles.homepage} homepage-landing`}>
            <section className={styles.hero} aria-labelledby="homepage-title">
                <div className={styles.heroDecoration} aria-hidden="true">
                    <ArtemisMark />
                </div>
                <div className="container">
                    <div className={styles.heroContent}>
                        <div className={styles.introduction}>
                            <div className={styles.eyebrow}>Artemis documentation</div>
                            <Heading as="h1" id="homepage-title" className={styles.headline}>
                                Find your way through <em>Artemis.</em>
                            </Heading>
                            <p className={styles.lead}>Guides for learning, teaching, developing, and operating the platform.</p>
                            <SearchModalTrigger variant="hero" />
                        </div>

                        <nav className={styles.audienceGrid} aria-label="Documentation by audience">
                            {audienceDestinations.map((destination) => (
                                <AudienceCard key={destination.role} destination={destination} />
                            ))}
                        </nav>
                    </div>
                </div>
            </section>

            <div className={styles.secondaryStrip}>
                <div className="container">
                    <nav className={styles.secondaryGrid} aria-label="Additional resources">
                        <Link to="/compare" className={styles.secondaryLink}>
                            <span className={styles.secondaryCopy}>
                                <strong>Compare Artemis</strong>
                                <span>See how Artemis compares with other learning platforms</span>
                            </span>
                            <span className={styles.secondaryAction}>
                                Explore comparison
                                <ArrowIcon />
                            </span>
                        </Link>
                        <Link to="/publications" className={styles.secondaryLink}>
                            <span className={styles.secondaryCopy}>
                                <strong>Research &amp; publications</strong>
                                <span>The evidence behind the platform</span>
                            </span>
                            <span className={styles.secondaryAction}>
                                Explore research
                                <ArrowIcon />
                            </span>
                        </Link>
                    </nav>
                </div>
            </div>
        </main>
    );
}

export default function Home(): ReactNode {
    const { siteConfig } = useDocusaurusContext();

    return (
        <Layout title={String(siteConfig.customFields?.pageTitle ?? '')} description={siteConfig.tagline}>
            <HomepageContent />
        </Layout>
    );
}
