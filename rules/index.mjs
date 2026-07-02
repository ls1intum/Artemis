import requireSignalReferenceNgbModalInput from './require-signal-reference-ngb-modal-input.mjs';
import enforceSignalApis from './enforce-signal-apis.mjs';
import enforceCleanupOnDestroy from './enforce-cleanup-on-destroy.mjs';
import preferSignalReactivityOverNgOnChanges from './prefer-signal-reactivity-over-ngonchanges.mjs';
import preferSignalTemplateState from './prefer-signal-template-state.mjs';
import noNavigationInEffect from './no-navigation-in-effect.mjs';
import noAsUnknownCast from './no-as-unknown-cast.mjs';

export default {
    rules: {
        'require-signal-reference-ngb-modal-input': requireSignalReferenceNgbModalInput,
        'enforce-signal-apis': enforceSignalApis,
        'enforce-cleanup-on-destroy': enforceCleanupOnDestroy,
        'prefer-signal-reactivity-over-ngonchanges': preferSignalReactivityOverNgOnChanges,
        'prefer-signal-template-state': preferSignalTemplateState,
        'no-navigation-in-effect': noNavigationInEffect,
        'no-as-unknown-cast': noAsUnknownCast,
    },
};
