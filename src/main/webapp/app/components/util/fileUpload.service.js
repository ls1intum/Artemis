/*
 * copied from
 * https://babuwant2do.wordpress.com/2017/09/06/angularjs-jhipster-spa-spring-rest-api-upload-file-step-by-step-implementation/
 *
 */

(function () {
    'use strict';
    angular
        .module('artemisApp')
        .factory('FileUpload', FileUpload);

    FileUpload.$inject = ['$http'];

    var uploadUrl = "api/fileUpload";

    function FileUpload($http) {
        return function (file) {
            var fd = new FormData();
            fd.append('file', file);
            return $http.post(uploadUrl, fd, {
                transformRequest: angular.identity,
                headers: {'Content-Type': undefined}
            });
        }
    }
})();
