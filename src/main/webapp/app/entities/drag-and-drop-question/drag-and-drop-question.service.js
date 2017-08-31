(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('DragAndDropQuestion', DragAndDropQuestion);

    DragAndDropQuestion.$inject = ['$resource'];

    function DragAndDropQuestion ($resource) {
        var resourceUrl =  'api/drag-and-drop-questions/:id';

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
