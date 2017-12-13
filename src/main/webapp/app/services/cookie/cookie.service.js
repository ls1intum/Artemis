angular
    .module('artemisApp')
    .factory('Cookie', Cookie);

Cookie.$inject = [];

function Cookie() {

    var service = {
        setInCookie: setInCookie,
        getFromCookie: getFromCookie
    };

    return service;

    /*
    save the following in the cookie
    @param cookieName: The name of the value to be storde
    @param cookieValue: the value to be stored
    @param experationDays: how many days ist that value to be stored
     */
    function setInCookie(cookieName, cookieValue, experationDays) {
        var date = new Date();
        date.setTime(date.getTime() + (experationDays * 24 * 60 * 60 * 1000))
        var expires = "expires=" + date.toUTCString();
        document.cookie = cookieName + "=" + cookieValue + ";" + expires + ";path=/"
    }

    /*
    get the value to a cookieName
    @param cookieName: the name of the value which is supposed to be retreived

    @return the value as String, no value yields empty string
     */
    function getFromCookie(cookieName) {
        var name = cookieName + "=";
        var decodedCookie = decodeURIComponent(document.cookie);
        var ca = decodedCookie.split(';');
        for (var i = 0; i < ca.length; i++) {
            var c = ca[i];
            while (c.charAt(0) == ' ') {
                c = c.substring(1);
            }
            if (c.indexOf(cookieName) == 0) {
                return c.substring(name.length, c.length);
            }
        }
        return "";

    }


}

