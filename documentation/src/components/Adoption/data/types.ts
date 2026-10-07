/**
 * How an institution relates to Artemis, as the institution itself described it.
 *
 * The project does not distinguish production from pilot deployments: that cannot be verified from the
 * outside, and a wrong label is worse than no label.
 */
export enum AdoptionStatus {
    /** Runs an Artemis instance, or is currently evaluating one. */
    UsingOrEvaluating = 'using-or-evaluating',
    /** Has expressed interest in Artemis but does not run an instance yet. */
    Interested = 'interested',
}

export interface AdoptionContact {
    name: string;
    /** Mail address, or a link to the person's institutional page. */
    href: string;
}

export interface Adopter {
    /** Stable identifier, used as a React key. */
    id: string;
    /** Institution name, or several names when one instance is shared. */
    name: string;
    /** Country, or the countries involved for a shared instance. */
    country: string;
    status: AdoptionStatus;
    /** Public URL of the instance, when there is one. */
    instanceUrl?: string;
    /** Shown next to the instance URL, for example when the instance is reachable only from a campus network. */
    instanceNote?: string;
    /** Additional link, for example the project an instance belongs to. */
    project?: { label: string; href: string };
    contact?: AdoptionContact;
    /**
     * Year in which the entry was last confirmed with the institution.
     *
     * Left unset until the first confirmation round. An entry that has not been confirmed for two years
     * should be re-confirmed or moved out of the list, so that the page stays worth trusting.
     */
    lastConfirmed?: number;
    /**
     * Link to a case study or experience report about this deployment.
     *
     * A logo says that an institution uses Artemis; a case study says what it changed for them, which is
     * what other institutions actually want to read. Anything durable works: a paper, a conference talk,
     * a blog post, or a page in this documentation.
     */
    caseStudy?: { label: string; href: string };
}
