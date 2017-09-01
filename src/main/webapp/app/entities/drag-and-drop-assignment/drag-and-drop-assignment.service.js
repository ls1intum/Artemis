(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DragAndDropAssignment', DragAndDropAssignment);

    DragAndDropAssignment.$inject = ['$resource'];

    function DragAndDropAssignment ($resource) {
        var resourceUrl =  'api/drag-and-drop-assignments/:id';

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
