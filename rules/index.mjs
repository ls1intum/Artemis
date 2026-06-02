import requireSignalReferenceNgbModalInput from './require-signal-reference-ngb-modal-input.mjs';
import enforceSignalApis from './enforce-signal-apis.mjs';
import enforceCleanupOnDestroy from './enforce-cleanup-on-destroy.mjs';

export default {
    rules: {
        'require-signal-reference-ngb-modal-input': requireSignalReferenceNgbModalInput,
        'enforce-signal-apis': enforceSignalApis,
        'enforce-cleanup-on-destroy': enforceCleanupOnDestroy,
    },
};
