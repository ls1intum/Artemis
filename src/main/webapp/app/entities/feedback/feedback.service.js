(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('Feedback', Feedback);

    Feedback.$inject = ['$resource'];

    function Feedback ($resource) {
        var resourceUrl =  'api/feedbacks/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true},
            'get': {
                method: 'GET',
                transformResponse: function (data) {
                    if (data) {
                        data = angular.fromJson(data);
                    }
                    return data;
                }
            },
            'update': { method:'PUT' }
        });
    }
})();
