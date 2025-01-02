const noHttpClientTestingModuleRule = require("./no-http-client-testing-module");
const plugin = {
    rules: { "enforce-no-http-client-testing-module": noHttpClientTestingModuleRule }
};
module.exports = plugin;
