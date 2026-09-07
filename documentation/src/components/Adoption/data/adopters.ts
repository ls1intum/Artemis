import { type Adopter, AdoptionStatus } from './types';

const USING = AdoptionStatus.UsingOrEvaluating;
const INTERESTED = AdoptionStatus.Interested;

/**
 * Institutions that use, evaluate, or have expressed interest in Artemis.
 *
 * This is the canonical list. The README and the Artemis website link here instead of keeping copies.
 * Entries are self-declared: an institution is listed because it told the project about its use or
 * interest, and each one operates its own instance.
 *
 * To add or correct an entry, open a pull request against this file or write to artemis@xcit.tum.de.
 */
export const adopters: Adopter[] = [
    {
        id: 'tum',
        name: 'Technical University of Munich',
        country: 'Germany',
        status: USING,
        instanceUrl: 'https://artemis.tum.de',
        contact: { name: 'Stephan Krusche', href: 'mailto:krusche@tum.de' },
    },
    {
        id: 'codeability',
        name: 'LFU Innsbruck, Uni Salzburg, JKU Linz, AAU Klagenfurt, TU Wien',
        country: 'Austria',
        status: USING,
        instanceUrl: 'https://artemis.codeability.uibk.ac.at',
        project: { label: 'codeAbility project', href: 'https://codeability.uibk.ac.at' },
        contact: { name: 'Michael Breu', href: 'mailto:Michael.Breu@uibk.ac.at' },
    },
    {
        id: 'uni-stuttgart',
        name: 'University of Stuttgart',
        country: 'Germany',
        status: USING,
        instanceUrl: 'https://artemis.sqa.ddnss.org',
        contact: { name: 'Steffen Becker', href: 'mailto:steffen.becker@informatik.uni-stuttgart.de' },
    },
    {
        id: 'uni-passau',
        name: 'Universität Passau',
        country: 'Germany',
        status: USING,
        instanceUrl: 'https://artemis.fim.uni-passau.de',
        instanceNote: 'only accessible via the university network or VPN',
        contact: { name: 'Benedikt Fein', href: 'mailto:fein@fim.uni-passau.de' },
    },
    {
        id: 'kit',
        name: 'Karlsruhe Institute of Technology',
        country: 'Germany',
        status: USING,
        instanceUrl: 'https://artemis.cs.kit.edu',
        contact: { name: 'Dominik Fuchß', href: 'mailto:dominik.fuchss@kit.edu' },
    },
    {
        id: 'hm-munich',
        name: 'Hochschule München',
        country: 'Germany',
        status: USING,
        instanceUrl: 'https://artemis.cs.hm.edu',
        contact: { name: 'Michael Eggers', href: 'mailto:michael.eggers@hm.edu' },
    },
    {
        id: 'tu-dresden',
        name: 'Technische Universität Dresden',
        country: 'Germany',
        status: USING,
        contact: { name: 'Andreas Domanowski', href: 'mailto:andreas.domanowski@tu-dresden.de' },
    },
    {
        id: 'hs-heilbronn',
        name: 'Hochschule Heilbronn',
        country: 'Germany',
        status: USING,
        contact: { name: 'Jörg Winckler', href: 'mailto:joerg.winckler@hs-heilbronn.de' },
    },
    {
        id: 'mtg-munich',
        name: 'Maria-Theresia-Gymnasium München',
        country: 'Germany',
        status: USING,
        contact: { name: 'Valentin Herrmann', href: 'mailto:valentin.herrmann@tum.de' },
    },
    {
        id: 'hu-berlin',
        name: 'HU Berlin',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Lars Grunske', href: 'https://www.informatik.hu-berlin.de/de/Members/lars-grunske' },
    },
    {
        id: 'wh-zwickau',
        name: 'Westsächsische Hochschule Zwickau',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Heiko Baum', href: 'https://www.fh-zwickau.de/pti/organisation/fachgruppe-informatik/personen/dr-ing-heiko-baum' },
    },
    {
        id: 'tu-chemnitz',
        name: 'Technische Universität Chemnitz',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Danny Kowerko', href: 'https://www.tu-chemnitz.de/informatik/mc/professor.php.en' },
    },
    {
        id: 'uni-koeln',
        name: 'Universität zu Köln',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Andreas Vogelsang', href: 'https://cs.uni-koeln.de/sse/team/prof-dr-andreas-vogelsang' },
    },
    {
        id: 'tu-dortmund',
        name: 'Technische Universität Dortmund',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Falk Howar', href: 'https://se.cs.tu-dortmund.de' },
    },
    {
        id: 'uni-bielefeld',
        name: 'Universität Bielefeld',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Daniel Merkle', href: 'https://ekvv.uni-bielefeld.de/pers_publ/publ/PersonDetail.jsp?personId=451188465' },
    },
    {
        id: 'uni-ulm',
        name: 'Universität Ulm',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Matthias Tichy', href: 'https://www.uni-ulm.de/in/sp/team/tichy' },
    },
    {
        id: 'thm',
        name: 'Technische Hochschule Mittelhessen',
        country: 'Germany',
        status: INTERESTED,
        contact: { name: 'Christian Prause', href: 'https://www.thm.de/iem/christian-prause' },
    },
    {
        id: 'imperial-college-london',
        name: 'Imperial College London',
        country: 'United Kingdom',
        status: INTERESTED,
        contact: { name: 'Robert Chatley', href: 'https://www.doc.ic.ac.uk/~rbc' },
    },
    {
        id: 'unisa',
        name: 'University of South Australia',
        country: 'Australia',
        status: INTERESTED,
        contact: { name: 'Srecko Joksimovic', href: 'https://people.unisa.edu.au/srecko.joksimovic' },
    },
];
