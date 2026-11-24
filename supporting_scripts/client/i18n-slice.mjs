/**
 * The entries of the translation slice that the landing and the sign-in page start from (`i18n/{language}-landing.json`).
 *
 * An entry is a namespace or a path into one (`error.http`): the namespace `artemisApp` alone is most of the catalog.
 * The slice holds `landing` (components), `global` (menu, ribbon), `home` (sign-in form) and `login` (sign-in errors), and the
 * keys of other namespaces that the sign-in page, the footer, the theme switch, its alerts and its passkey flow show.
 *
 * Keep the list narrow: every key here is part of the request that blocks the first paint. A key that is missing shows up as
 * `translation-not-found[...]` until the full catalog has loaded, so add the keys of every component that these pages show.
 * `landing-translation-slice.spec.ts` checks the templates of the shell against this list.
 *
 * `prebuild.mjs` writes the slice, and `SplitTranslateLoader` (app/core/config/split-translate-loader.ts) decides which routes
 * start from it.
 */
export const LANDING_NAMESPACES = [
    'landing',
    'global',
    'home',
    'login',
    'aboutUs',
    'feedback',
    'releases',
    'loading',
    'connectionStatus',
    'entity.action',
    'error.http',
    'artemisApp.alerts',
    'artemisApp.disconnectedAlert',
    'artemisApp.error',
    'artemisApp.git',
    'artemisApp.legal.imprint.title',
    'artemisApp.legal.privacy',
    'artemisApp.lti.ltiSuccessLoginRequired',
    'artemisApp.outdatedAction',
    'artemisApp.outdatedAlert',
    'artemisApp.theme',
    'artemisApp.userSettings.passkeySettingsPage.error',
];
