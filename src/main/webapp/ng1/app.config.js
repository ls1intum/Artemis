'use strict';

angular.
  module('artemisApp').
  config(['$locationProvider' ,'$routeProvider',
    function config($locationProvider, $routeProvider) {
        $locationProvider.hashPrefix = '';
        $routeProvider.
        when('/editor/:participationId', {
            template: '<editor participation="participation" file="file" repository="repository"></editor>'
        }).
        otherwise({
            template : ''
        });
    }
  ]);
