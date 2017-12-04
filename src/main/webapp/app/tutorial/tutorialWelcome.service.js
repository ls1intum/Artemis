(function () {
    'use strict';

    angular.module('artemisApp')
        .factory('tutorialWelcomeService', tutorialWelcomeService);

    tutorialWelcomeService.$inject = ['$uibModal'];

    function tutorialWelcomeService($uibModal) {

        var service = {
            open: open
        };

        var modalInstance = null;
        var resetModal = function () {
            modalInstance = null;
        };

        var modalDefaults = {
            animation: true,
            templateUrl: 'app/tutorial/welcomepage.html',
            controller: 'tutorialWelcomeController',
            controllerAs: 'vm'
        };

        return service;

        function open() {
            if (modalInstance !== null || getSeenInCookie()) return;

            setSeenInCookie(true);
            modalInstance = $uibModal.open(modalDefaults);

            return modalInstance.result;
        }

        /* set in cookie if overlay has been closed */
        function setSeenInCookie(bool){
            var date = new Date();
            date.setTime(date.getTime() + 365*24*60*60*1000);
            var expires = "expires=" + date.toUTCString();
            document.cookie = "tutorialDone" +  "=" + bool + ";" + expires + ";path=/"
        };

        /* get the seen state frome the cookie */
        function getSeenInCookie() {
            var name = "tutorialDone=";
            var decodedCookie = decodeURIComponent(document.cookie);
            var cookieEntries = decodedCookie.split(';');

            for(var i=0; i<cookieEntries.length; i++){
                var entry = cookieEntries[i];

                while(entry.charAt(0) == ' '){
                    entry = entry.substring(1);
                }
                if(entry.indexOf(name) == 0){
                    return entry.substring(name.length, entry.length) == "true";
                }
            }
            return false;
        };


    }
})();
