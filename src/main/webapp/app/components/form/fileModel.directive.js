/*
 * copied from
 * https://babuwant2do.wordpress.com/2017/09/06/angularjs-jhipster-spa-spring-rest-api-upload-file-step-by-step-implementation/
 *
 */

(function () {
    'use strict';
    angular
        .module('artemisApp')
        .directive('fileModel', ['$parse', function ($parse) {
            return {
                restrict: 'A',
                link: function (scope, element, attrs) {
                    var model = $parse(attrs.fileModel);
                    var modelSetter = model.assign;

                    element.bind('change', function () {
                        scope.$apply(function () {
                            modelSetter(scope, element[0].files[0]);
                        });
                    });
                }
            };
        }]);
})();
