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

    FileUpload.$inject = ['$http', 'MAX_FILE_SIZE', '$q'];

    var uploadUrl = "api/fileUpload";

    function FileUpload($http, MAX_FILE_SIZE, $q) {
        return function (file) {

            var rejectedPromise = $q(function(resolve, reject) {reject()});

            var fileExtension = file.name.split('.').pop().toLocaleLowerCase();
            if (fileExtension !== "png" && fileExtension !== "jpg" && fileExtension !== "jpeg" && fileExtension !== "svg") {
                alert('Unsupported file-type! Only files of type ".png", ".jpg" or ".svg" allowed.');
                return rejectedPromise;
            }
            if (file.size > MAX_FILE_SIZE) {
                alert('File is too big! Maximum allowed file size: ' + MAX_FILE_SIZE / (1024 * 1024) + ' MB.');
                return rejectedPromise;
            }

            if (!file) {
                alert("Please select a file to upload first.");
                return rejectedPromise;
            }

            var fd = new FormData();
            fd.append('file', file);
            return $http.post(uploadUrl, fd, {
                transformRequest: angular.identity,
                headers: {'Content-Type': undefined}
            });
        }
    }
})();
