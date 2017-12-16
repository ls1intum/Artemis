angular
    .module('artemisApp')
    .controller('ModalController', ModalController);

ModalController.$inject = ['$uibModalInstance', 'Modal'];

function ModalController($uibModalInstance, Modal){

    var vm = this;

    vm.modalOptions = Modal.modalOptions();

    vm.close = function () {
        $uibModalInstance.close({result: 'false'});
    };

    vm.ok = function () {
        $uibModalInstance.close({result: 'ok'});
    };

}
