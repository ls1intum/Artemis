import requireSignalReferenceNgbModalInput from "./require-signal-reference-ngb-modal-input.mjs";
import enforceSignalApisInMigratedModules from "./enforce-signal-apis-in-migrated-modules.mjs";

export default {
    rules: {
        "require-signal-reference-ngb-modal-input": requireSignalReferenceNgbModalInput,
        "enforce-signal-apis-in-migrated-modules": enforceSignalApisInMigratedModules,
    },
};
