(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DragAndDropMapping', DragAndDropMapping);

    DragAndDropMapping.$inject = ['$resource'];

    function DragAndDropMapping ($resource) {
        var resourceUrl =  'api/drag-and-drop-mappings/:id';

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
