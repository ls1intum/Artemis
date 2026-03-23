(function () {
    'use strict';

    var POPUP_ID = 'artemis-feedback-popup';
    var INIT_ATTR = 'data-artemis-interactive';

    function init() {
        var tasks = document.querySelectorAll('.artemis-task[data-feedback]');
        for (var i = 0; i < tasks.length; i++) {
            if (tasks[i].hasAttribute(INIT_ATTR)) {
                continue;
            }
            var raw = tasks[i].getAttribute('data-feedback');
            if (!raw || raw === '[]') {
                continue;
            }
            var feedback;
            try {
                feedback = JSON.parse(raw);
            }
            catch (e) {
                continue;
            }
            if (!Array.isArray(feedback) || feedback.length === 0) {
                continue;
            }
            tasks[i].setAttribute(INIT_ATTR, 'true');
            tasks[i].setAttribute('tabindex', '0');
            tasks[i].setAttribute('role', 'button');
            tasks[i].setAttribute('aria-haspopup', 'true');
            tasks[i].addEventListener('click', showPopup);
            tasks[i].addEventListener('keydown', function (e) {
                if (e.key === 'Enter' || e.key === ' ') {
                    e.preventDefault();
                    showPopup.call(this, e);
                }
            });
        }
    }

    function showPopup(e) {
        closePopup();
        var raw = this.getAttribute('data-feedback');
        if (!raw) {
            return;
        }
        var feedback;
        try {
            feedback = JSON.parse(raw);
        }
        catch (err) {
            return;
        }
        if (!Array.isArray(feedback) || feedback.length === 0) {
            return;
        }

        var popup = document.createElement('div');
        popup.id = POPUP_ID;
        popup.setAttribute('role', 'dialog');
        popup.setAttribute('aria-label', 'Test feedback');

        var style = 'position:absolute;z-index:10000;background:#fff;border:1px solid #dee2e6;'
            + 'border-radius:8px;box-shadow:0 4px 12px rgba(0,0,0,.15);padding:12px 16px;'
            + 'max-width:400px;font-size:14px;line-height:1.5;color:#212529;';
        popup.setAttribute('style', style);

        for (var i = 0; i < feedback.length; i++) {
            var item = feedback[i];
            var row = document.createElement('div');
            row.setAttribute('style', 'margin-bottom:' + (i < feedback.length - 1 ? '8px' : '0'));

            var icon = document.createElement('span');
            icon.setAttribute('style', 'margin-right:6px');
            icon.textContent = item.passed ? '\u2705' : '\u274C';
            row.appendChild(icon);

            var name = document.createElement('strong');
            name.textContent = item.name;
            row.appendChild(name);

            if (item.message) {
                var msg = document.createElement('div');
                msg.setAttribute('style', 'margin-left:24px;color:#6c757d;font-size:13px');
                msg.textContent = item.message;
                row.appendChild(msg);
            }

            popup.appendChild(row);
        }

        document.body.appendChild(popup);

        var rect = this.getBoundingClientRect();
        var scrollX = window.pageXOffset || document.documentElement.scrollLeft;
        var scrollY = window.pageYOffset || document.documentElement.scrollTop;
        popup.style.left = (rect.left + scrollX) + 'px';
        popup.style.top = (rect.bottom + scrollY + 4) + 'px';

        setTimeout(function () {
            document.addEventListener('click', onOutsideClick);
            document.addEventListener('keydown', onEscape);
        }, 0);
    }

    function closePopup() {
        var existing = document.getElementById(POPUP_ID);
        if (existing) {
            existing.parentNode.removeChild(existing);
        }
        document.removeEventListener('click', onOutsideClick);
        document.removeEventListener('keydown', onEscape);
    }

    function onOutsideClick(e) {
        var popup = document.getElementById(POPUP_ID);
        if (popup && !popup.contains(e.target)) {
            closePopup();
        }
    }

    function onEscape(e) {
        if (e.key === 'Escape') {
            closePopup();
        }
    }

    if (document.readyState === 'loading') {
        document.addEventListener('DOMContentLoaded', init);
    }
    else {
        init();
    }
})();
