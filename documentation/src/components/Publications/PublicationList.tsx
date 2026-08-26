import type { ReactNode } from 'react';

import { type Publication, researchTopicLabels } from './data/types';
import styles from './Publications.module.css';

interface PublicationListProps {
    publications: Publication[];
    /** Heading level used for the publication titles, so the page keeps a correct heading order. */
    headingLevel: 'h3' | 'h4';
}

function PublicationEntry({ publication, headingLevel }: { publication: Publication; headingLevel: 'h3' | 'h4' }): ReactNode {
    const Heading = headingLevel;
    const link = publication.doi ? { href: `https://doi.org/${publication.doi}`, label: `DOI: ${publication.doi}` } : undefined;
    const fallbackLink = !link && publication.url ? { href: publication.url, label: publication.urlLabel ?? publication.url } : undefined;
    const reference = link ?? fallbackLink;

    return (
        <li className={styles.publication}>
            <Heading className={styles.publicationTitle}>{publication.title}</Heading>
            <p className={styles.publicationAuthors}>{publication.authors}</p>
            <p className={styles.publicationVenue}>
                {publication.venue}
                {publication.details ? `, ${publication.details}` : ''}
            </p>
            <div className={styles.publicationMeta}>
                {publication.forthcoming && <span className={styles.forthcomingTag}>To appear</span>}
                {reference && (
                    <a href={reference.href} target="_blank" rel="noopener noreferrer">
                        {reference.label}
                    </a>
                )}
                {publication.topics.map((topic) => (
                    <span key={topic} className={styles.topicTag}>
                        {researchTopicLabels[topic]}
                    </span>
                ))}
            </div>
        </li>
    );
}

export default function PublicationList({ publications, headingLevel }: PublicationListProps): ReactNode {
    return (
        <ul className={styles.publicationList}>
            {publications.map((publication) => (
                <PublicationEntry key={publication.id} publication={publication} headingLevel={headingLevel} />
            ))}
        </ul>
    );
}
