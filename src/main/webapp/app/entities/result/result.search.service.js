(function() {
    'use strict';

    angular
        .module('exerciseApplicationApp')
        .factory('ResultSearch', ResultSearch);

    ResultSearch.$inject = ['$resource'];

    function ResultSearch($resource) {
        var resourceUrl =  'api/_search/results/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true}
        });
    }
})();
