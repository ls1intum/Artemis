import type { ReactNode } from 'react';
import clsx from 'clsx';
import Link from '@docusaurus/Link';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import HomepageFeatures from '../components/HomepageFeatures';
import Heading from '@theme/Heading';

import styles from './index.module.css';
import { FontAwesomeIcon } from '@fortawesome/react-fontawesome';
import { faArrowUpRightFromSquare } from '@fortawesome/free-solid-svg-icons';

function HomepageHeader() {
    const { siteConfig } = useDocusaurusContext();
    return (
        <header className={clsx('hero hero--primary', styles.heroBanner)}>
            <div className="container">
                <Heading as="h1" className="hero__title">
                    {siteConfig.title}
                </Heading>
                <p className="hero__subtitle">{siteConfig.tagline}</p>
                <div className={styles.buttons}>
                    <Link
                        className="button button--secondary button--lg"
                        to="/student/intro">
                        Student Guide
                        <FontAwesomeIcon icon={faArrowUpRightFromSquare} />
                    </Link>

                </div>
                <div className={styles.buttons}>
                    <Link
                        className="button button--secondary button--lg"
                        to="/instructor/intro">
                        Instructor Guide
                        <FontAwesomeIcon icon={faArrowUpRightFromSquare} />
                    </Link>
                </div>
                <div className={styles.buttons}>
                    <Link
                        className="button button--secondary button--lg"
                        to="/staff/intro">
                        Staff Documentation
                        <FontAwesomeIcon icon={faArrowUpRightFromSquare} />
                    </Link>
                </div>
                <div className={styles.buttons}>
                    <Link
                        className="button button--secondary button--lg"
                        to="/admin/intro">
                        Admin Documentation
                        <FontAwesomeIcon icon={faArrowUpRightFromSquare} />
                    </Link>
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
            <main>
                {/*<HomepageFeatures />*/}
            </main>
        </Layout>
    );
}
