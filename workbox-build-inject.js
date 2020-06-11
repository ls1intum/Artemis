// Script that modifies the service-worker.js configuration using workbox-build
// Reference: https://developers.google.com/web/tools/workbox/modules/workbox-build

const { injectManifest } = require("workbox-build");

// Workbox configuration
const workboxConfig = require("./workbox-config");

console.log(`Workbox configuration: `, workboxConfig);

// We use injectManifest to inject everything we need into service-worker.js
// Reference: https://developers.google.com/web/tools/workbox/modules/workbox-build
injectManifest(workboxConfig).then(({ count, size, warnings }) => {
    warnings.forEach(console.warn);
    console.log(`Generated ${workboxConfig.swDest}, which will precache ${count} files (${size} bytes)`);
});
