(function() {
    'use strict';

    angular
        .module('artemisApp')
        .constant('MAX_FILE_SIZE', 5 * 1024 * 1024);
        // Maximum File Size: 5 MB (Spring-Boot interprets MB as 1024^2 bytes)
        // -> this should match the value in
        //    resources/config/application.yml
        //    spring.http.multipart.max-file-size

})();
