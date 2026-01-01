import type { ReactNode } from 'react';
import clsx from 'clsx';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import Link from '@docusaurus/Link';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faGraduationCap, faChalkboardTeacher, faCode, faUserShield } from '@fortawesome/free-solid-svg-icons';

import styles from './index.module.css';

interface TileProps {
    title: string;
    link: string;
    icon: any;
}

function Tile({ title, link, icon }: TileProps) {
    return (
        <Link to={link} className={clsx('card', styles.tile)}>
            <div className="card__body">
                <div className={styles.tileIcon}>
                    <FontAwesomeIcon icon={icon} size="3x" />
                </div>
                <Heading as="h3" className={styles.tileTitle}>
                    {title}
                </Heading>
            </div>
        </Link>
    );
}

function HomepageHeader() {
    const { siteConfig } = useDocusaurusContext();
    return (
        <header className={clsx('hero hero--primary', styles.heroBanner)}>
            <div className="container">
                <Heading as="h1" className="hero__title">
                    {siteConfig.title}
                </Heading>
                <p className="hero__subtitle">{siteConfig.tagline}</p>
                <div className={styles.tilesContainer}>
                    <Tile
                        title="Student"
                        link="/student/intro"
                        icon={faGraduationCap}
                    />
                    <Tile
                        title="Instructor"
                        link="/instructor/intro"
                        icon={faChalkboardTeacher}
                    />
                    <Tile
                        title="Developer"
                        link="/staff/intro"
                        icon={faCode}
                    />
                    <Tile
                        title="Admin"
                        link="/admin/intro"
                        icon={faUserShield}
                    />
                </div>
            </div>
        </header>
    );
}

export default function Home(): ReactNode {
    const { siteConfig } = useDocusaurusContext();
    return (
        <Layout
            title={siteConfig.customFields.pageTitle.toString()}
            description={siteConfig.tagline}>
            <HomepageHeader />
        </Layout>
    );
}
