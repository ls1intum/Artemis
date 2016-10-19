(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('RepositoryFile', RepositoryFile);


    RepositoryFile.$inject = ['$resource'];

    function RepositoryFile ($resource) {
        var resourceUrl = 'api/repository/:participationId/files';

        return $resource(resourceUrl, {}, {
            'query': { method: 'GET', isArray: true },
            'get': {
                url: 'api/repository/:participationId/file',
                method: 'GET'
            }
        });
    }
})();
