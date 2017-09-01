(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DragItem', DragItem);

    DragItem.$inject = ['$resource'];

    function DragItem ($resource) {
        var resourceUrl =  'api/drag-items/:id';

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
