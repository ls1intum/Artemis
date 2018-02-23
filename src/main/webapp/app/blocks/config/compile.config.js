(function() {
    'use strict';

    angular
        .module('artemisApp')
        .config(compileServiceConfig);

    compileServiceConfig.$inject = ['$compileProvider','DEBUG_INFO_ENABLED'];

    function compileServiceConfig($compileProvider,DEBUG_INFO_ENABLED) {
        // disable debug data on prod profile to improve performance
        $compileProvider.debugInfoEnabled(DEBUG_INFO_ENABLED);

        // allow sourcetree links to pass sanitation
        $compileProvider.aHrefSanitizationWhitelist(/^((ftp|https?|sourcetree):\/\/|mailto:|tel:|#)/i);

        /*
        If you wish to debug an application with this information
        then you should open up a debug console in the browser
        then call this method directly in this console:

		angular.reloadWithDebugInfo();
		*/
    }
})();
