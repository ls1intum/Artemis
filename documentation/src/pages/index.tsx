import type { ReactNode } from 'react';
import clsx from 'clsx';
import useDocusaurusContext from '@docusaurus/useDocusaurusContext';
import Layout from '@theme/Layout';
import Heading from '@theme/Heading';
import LinkButton from '../components/LinkButton/LinkButton';

import styles from './index.module.css';

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
                    <LinkButton to="/student/intro">
                        Student Guide
                    </LinkButton>
                </div>
                <div className={styles.buttons}>
                    <LinkButton to="/instructor/intro">
                        Instructor Guide
                    </LinkButton>
                </div>
                <div className={styles.buttons}>
                    <LinkButton to="/staff/intro">
                        Staff Documentation
                    </LinkButton>
                </div>
                <div className={styles.buttons}>
                    <LinkButton to="/admin/intro">
                        Admin Documentation
                    </LinkButton>
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
