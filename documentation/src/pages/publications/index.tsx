import { type ReactNode, useMemo, useState } from 'react';
import Link from '@docusaurus/Link';
import Heading from '@theme/Heading';
import Layout from '@theme/Layout';

import PublicationList from '../../components/Publications/PublicationList';
import { publications } from '../../components/Publications/data/publications';
import { researchTopicLabels, researchTopicOrder } from '../../components/Publications/data/types';
import styles from '../../components/Publications/Publications.module.css';

type GroupingMode = 'year' | 'topic';

const researchLoop = [
    {
        title: 'Research',
        body: 'A question about teaching, feedback, or assessment at scale is identified, usually while running large university courses.',
    },
    {
        title: 'Prototype',
        body: 'The idea is implemented in Artemis, frequently as part of a thesis or a university course project.',
    },
    {
        title: 'Evaluation',
        body: 'The prototype is used in real courses and evaluated together with the students and instructors who work with it.',
    },
    {
        title: 'Open-source product',
        body: 'What holds up is hardened, reviewed, documented, and released as part of Artemis for every institution to use.',
    },
];

function ResearchLoop(): ReactNode {
    return (
        <ol className={styles.loop}>
            {researchLoop.map((step, index) => (
                <li key={step.title} className={styles.loopStep}>
                    <span className={styles.loopStepNumber}>Step {index + 1}</span>
                    <strong className={styles.loopStepTitle}>{step.title}</strong>
                    <span className={styles.loopStepBody}>{step.body}</span>
                </li>
            ))}
        </ol>
    );
}

function PublicationsByYear(): ReactNode {
    const years = useMemo(() => [...new Set(publications.map((publication) => publication.year))].sort((a, b) => b - a), []);

    return (
        <>
            {years.map((year) => (
                <section key={year}>
                    <Heading as="h3" className={styles.groupTitle}>
                        {year}
                    </Heading>
                    <PublicationList publications={publications.filter((publication) => publication.year === year)} headingLevel="h4" />
                </section>
            ))}
        </>
    );
}

function PublicationsByTopic(): ReactNode {
    return (
        <>
            {researchTopicOrder.map((topic) => {
                const matching = publications.filter((publication) => publication.topics.includes(topic));
                if (matching.length === 0) {
                    return undefined;
                }
                return (
                    <section key={topic}>
                        <Heading as="h3" className={styles.groupTitle}>
                            {researchTopicLabels[topic]}
                        </Heading>
                        <PublicationList publications={matching} headingLevel="h4" />
                    </section>
                );
            })}
        </>
    );
}

export default function PublicationsPage(): ReactNode {
    const [grouping, setGrouping] = useState<GroupingMode>('year');

    return (
        <Layout title="Research behind Artemis" description="Why research is part of Artemis, how research reaches the platform, and the peer-reviewed publications behind it.">
            <main className={`container ${styles.main}`}>
                <header className={styles.header}>
                    <Heading as="h1" className={styles.title}>
                        Research behind Artemis
                    </Heading>
                    <p className={styles.lead}>
                        Artemis is a product and a research platform at the same time. Features are not added because they look good on a feature list, but because there is a
                        teaching problem worth solving and evidence that the solution helps.
                    </p>
                </header>

                <section className={styles.section}>
                    <Heading as="h2">Why research is part of Artemis</Heading>
                    <div className={styles.sectionIntro}>
                        <p>
                            Artemis grew out of research on interactive learning at the Technical University of Munich and has been used as an education research platform ever
                            since. Large courses make the trade-off between meaningful feedback and the number of students who can receive it visible, and that trade-off is what
                            most of the work below investigates: how to give individual feedback at scale, how to assess authentically, and how to guide students through content
                            adaptively.
                        </p>
                        <p>
                            Because the platform and the research share the same codebase, results do not stop at a paper. New capabilities usually follow the same loop, and every
                            step of it happens in public.
                        </p>
                    </div>
                    <ResearchLoop />
                </section>

                <section className={styles.section}>
                    <Heading as="h2">Publications</Heading>
                    <div className={styles.sectionIntro}>
                        <p>
                            Peer-reviewed publications on Artemis, its AI subsystems, and the interactive learning, feedback, and assessment research the platform grew out of. A
                            publication can belong to more than one research theme, so the themed view shows some entries more than once. The full publication list of the group,
                            including work on other subjects, is available on the{' '}
                            <a href="https://aet.cit.tum.de/research/publications/" target="_blank" rel="noopener noreferrer">
                                AET website
                            </a>
                            .
                        </p>
                    </div>

                    <div className={styles.viewToggle} role="group" aria-labelledby="publications-grouping-label">
                        <span className={styles.viewToggleLabel} id="publications-grouping-label">
                            Group by
                        </span>
                        <button type="button" className={styles.viewButton} aria-pressed={grouping === 'year'} onClick={() => setGrouping('year')}>
                            Year
                        </button>
                        <button type="button" className={styles.viewButton} aria-pressed={grouping === 'topic'} onClick={() => setGrouping('topic')}>
                            Research theme
                        </button>
                    </div>

                    {grouping === 'year' ? <PublicationsByYear /> : <PublicationsByTopic />}
                </section>

                <aside className={styles.note} aria-label="Citing and contributing">
                    <p>
                        <strong>Citing Artemis.</strong> If you reference Artemis in your own work, please use the citation metadata in{' '}
                        <a href="https://github.com/ls1intum/Artemis/blob/develop/CITATION.cff" target="_blank" rel="noopener noreferrer">
                            CITATION.cff
                        </a>
                        .
                    </p>
                    <p>
                        <strong>Missing a publication?</strong> This list is maintained in the Artemis repository. Open a pull request against{' '}
                        <a
                            href="https://github.com/ls1intum/Artemis/blob/develop/documentation/src/components/Publications/data/publications.ts"
                            target="_blank"
                            rel="noopener noreferrer"
                        >
                            the publication data
                        </a>{' '}
                        to add or correct an entry.
                    </p>
                    <p>
                        <strong>Related pages.</strong> <Link to="/about">About Artemis</Link> explains how research shapes the project,{' '}
                        <Link to="/developer/open-source">Open-source development</Link> describes how a prototype becomes a released feature, and{' '}
                        <Link to="/compare">the platform comparison</Link> positions Artemis against other learning platforms.
                    </p>
                </aside>
            </main>
        </Layout>
    );
}
