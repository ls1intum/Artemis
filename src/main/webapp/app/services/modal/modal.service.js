angular
    .module('artemisApp')
    .factory('Modal', Modal);

Modal.$inject = ['$uibModal'];

function Modal($uibModal) {

    var service = {
        showModal: showModal,
        modalOptions: modalOptions
    };


    var modalInstance = null;
    var resetModal = function(){
        modalInstance = null;
    };

    var tempModalOptions = {};

    var modalDefaults = {
        animation: true,
        backdrop: true,
        keyboard: true,
        modalFade: true,
        templateUrl: 'app/services/modal/modal.html',
        controller: 'ModalController',
        controllerAs: 'vm'
    };

    var modalOptions = {
        closeButtonText: 'Close',
        actionsButtonText: 'Okay',
        headerText: 'Proceed?',
        bodyText: 'Perform this Action?'
    };

    return service;

    function showModal (customModalDefaults, customModalOptions){
        if (!customModalDefaults) customModalDefaults = {};
        customModalDefaults.backdrop = 'static';

        //Map modal.html $scope custom properties to defaults defined in service
        angular.extend(tempModalOptions, modalOptions, customModalOptions);
        return show(customModalDefaults, customModalOptions);
    };

    function show (customModalDefaults, customModalOptions) {
        //Create temp objects to work with since we're in a singleton service
        var tempModalDefaults = {};


        //Map angular-ui modal custom defaults to modal defaults defined in service
        angular.extend(tempModalDefaults, modalDefaults, customModalDefaults);

        if(modalInstance != null)
            return;

        modalInstance = $uibModal.open(tempModalDefaults);

        return modalInstance.result;
    };

    function modalOptions(){
        return tempModalOptions;
    };

}
