(function() {
    'use strict';

    angular
        .module('artemisApp')
        .factory('ParticipationSearch', ParticipationSearch);

    ParticipationSearch.$inject = ['$resource'];

    function ParticipationSearch($resource) {
        var resourceUrl =  'api/_search/participations/:id';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true}
        });
    }
})();
