(function() {
    'use strict';
    angular
        .module('artemisApp')
        .factory('DragAndDropStatistic', DragAndDropStatistic);

    DragAndDropStatistic.$inject = ['$resource'];

    function DragAndDropStatistic ($resource) {
        var resourceUrl =  'api/drag-and-drop-statistics/:id';

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
