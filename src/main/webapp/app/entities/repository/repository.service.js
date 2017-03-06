(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('Repository', Repository)
        .factory('RepositoryFile', RepositoryFile);


    Repository.$inject = ['$resource'];

    function Repository ($resource) {
        var resourceUrl = 'api/repository/:participationId';

        return $resource(resourceUrl, {}, {
            'commit': {
                url: 'api/repository/:participationId/commit',
                method: 'POST'
            },
            'pull': {
                url: 'api/repository/:participationId/pull',
                method: 'POST'
            },
            'buildlogs': {
                url: 'api/repository/:participationId/buildlogs',
                method: 'GET', isArray: true
            }
        });
    }


    RepositoryFile.$inject = ['$resource'];

    function RepositoryFile ($resource) {
        var resourceUrl = 'api/repository/:participationId/files';

        return $resource(resourceUrl, {}, {
            'query': {
                method: 'GET', isArray: true
            },
            'get': {
                url: 'api/repository/:participationId/file',
                method: 'GET',
                transformResponse: function(data, headersGetter, status) {
                    return {fileContent:  data};
                }
            },
            'update': {
                url: 'api/repository/:participationId/file',
                method:'PUT'
            },
            'create': {
                url: 'api/repository/:participationId/file',
                method:'POST'
            },
            'delete': {
                url: 'api/repository/:participationId/file',
                method:'DELETE'
            }
        });
    }
})();
