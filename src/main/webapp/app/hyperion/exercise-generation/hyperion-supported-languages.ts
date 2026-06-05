import { ProgrammingLanguage } from 'app/programming/shared/entities/programming-exercise.model';

/**
 * The programming languages for which Artemis Intelligence (Hyperion) can generate or adapt a whole exercise.
 *
 * This list MIRRORS the non-default switch arms of the server's
 * {@code de.tum.cit.aet.artemis.hyperion.service.exercisegeneration.LanguageGenerationProfile} that return
 * meaningful (non-empty) generation guidance. It MUST be kept in sync with that file: a language that the server
 * has no profile for would generate a low-quality scaffold, and a language missing here would hide a supported
 * action from instructors.
 *
 * Only the languages with a well-supported, self-verifiable profile are included. The server also has best-effort
 * arms for niche, simulator/license-bound targets (C, OCaml, Bash, Assembler, MATLAB, VHDL) that the differential
 * verifier cannot reliably self-check; those are intentionally excluded from the create action.
 *
 * The longer-term fix is to expose this set from the server (e.g. via the profile/feature API) so the client never
 * has to mirror it by hand. Until then, any change to {@code LanguageGenerationProfile} must be reflected here.
 */
export const HYPERION_GENERATION_SUPPORTED_LANGUAGES: ProgrammingLanguage[] = [
    ProgrammingLanguage.JAVA,
    ProgrammingLanguage.KOTLIN,
    ProgrammingLanguage.PYTHON,
    ProgrammingLanguage.JAVASCRIPT,
    ProgrammingLanguage.TYPESCRIPT,
    ProgrammingLanguage.GO,
    ProgrammingLanguage.RUST,
    ProgrammingLanguage.C_PLUS_PLUS,
    ProgrammingLanguage.C_SHARP,
    ProgrammingLanguage.DART,
    ProgrammingLanguage.RUBY,
    ProgrammingLanguage.R,
    ProgrammingLanguage.HASKELL,
    ProgrammingLanguage.SWIFT,
];

/**
 * @param language the exercise's programming language (may be {@code undefined} before a language is chosen)
 * @returns whether Artemis Intelligence whole-exercise generation supports the given language
 */
export function isHyperionGenerationSupportedLanguage(language: ProgrammingLanguage | undefined): boolean {
    return language !== undefined && HYPERION_GENERATION_SUPPORTED_LANGUAGES.includes(language);
}
