import type { ReactNode } from 'react';

import { type Adopter } from './data/types';
import styles from './Adoption.module.css';

interface AdopterListProps {
    adopters: Adopter[];
}

function AdopterEntry({ adopter }: { adopter: Adopter }): ReactNode {
    return (
        <li className={styles.adopter}>
            <div className={styles.adopterHeader}>
                <strong className={styles.adopterName}>{adopter.name}</strong>
                <span className={styles.adopterCountry}>{adopter.country}</span>
                {adopter.lastConfirmed !== undefined && <span className={styles.adopterConfirmed}>confirmed {adopter.lastConfirmed}</span>}
            </div>
            <dl className={styles.adopterDetails}>
                {adopter.instanceUrl && (
                    <>
                        <dt>Instance</dt>
                        <dd>
                            <a href={adopter.instanceUrl} target="_blank" rel="noopener noreferrer">
                                {adopter.instanceUrl.replace(/^https:\/\//, '')}
                            </a>
                            {adopter.instanceNote && <span className={styles.adopterNote}> ({adopter.instanceNote})</span>}
                        </dd>
                    </>
                )}
                {adopter.project && (
                    <>
                        <dt>Project</dt>
                        <dd>
                            <a href={adopter.project.href} target="_blank" rel="noopener noreferrer">
                                {adopter.project.label}
                            </a>
                        </dd>
                    </>
                )}
                {adopter.caseStudy && (
                    <>
                        <dt>Case study</dt>
                        <dd>
                            <a href={adopter.caseStudy.href} target="_blank" rel="noopener noreferrer">
                                {adopter.caseStudy.label}
                            </a>
                        </dd>
                    </>
                )}
                {adopter.contact && (
                    <>
                        <dt>Contact</dt>
                        <dd>
                            <a href={adopter.contact.href} target="_blank" rel="noopener noreferrer">
                                {adopter.contact.name}
                            </a>
                        </dd>
                    </>
                )}
            </dl>
        </li>
    );
}

export default function AdopterList({ adopters }: AdopterListProps): ReactNode {
    return (
        <ul className={styles.adopterList}>
            {adopters.map((adopter) => (
                <AdopterEntry key={adopter.id} adopter={adopter} />
            ))}
        </ul>
    );
}
