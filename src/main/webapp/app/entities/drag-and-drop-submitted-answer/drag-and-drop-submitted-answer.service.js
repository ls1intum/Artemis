(function() {
    'use strict';
    angular
        .module('exerciseApplicationApp')
        .factory('DragAndDropSubmittedAnswer', DragAndDropSubmittedAnswer);

    DragAndDropSubmittedAnswer.$inject = ['$resource'];

    function DragAndDropSubmittedAnswer ($resource) {
        var resourceUrl =  'api/drag-and-drop-submitted-answers/:id';

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
