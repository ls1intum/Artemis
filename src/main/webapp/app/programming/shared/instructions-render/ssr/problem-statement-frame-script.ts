/**
 * The only script that runs inside the sandboxed problem-statement frame.
 *
 * It is a plain string rather than a serialized function on purpose. Serializing a function with
 * `Function.prototype.toString()` survives neither minification that renames a captured constant, nor a
 * refactor that quietly introduces a free variable, nor a bundler that injects a runtime helper; each of those
 * fails at runtime, inside a sandbox, where nothing can report the error back. A string is opaque to the
 * bundler, so what is written here is exactly what executes.
 *
 * The frame carries a CSP of its own with a per-render nonce, and this script is the only element that gets
 * that nonce. Anything an attacker manages to smuggle past the server safelist and DOMPurify therefore cannot
 * execute, even though the sandbox permits scripts at all (see `problem-statement-frame.util.ts`).
 *
 * Everything expensive (KaTeX, highlight.js) already happened in the parent, so this file only carries the
 * three things that genuinely cannot: measuring the rendered height, resolving a click to a task, and turning
 * an anchor into a request the parent can act on.
 */

/** Replaced at assembly time with the per-render generation token. Hex only, so it needs no escaping. */
export const GENERATION_PLACEHOLDER = '__ARTEMIS_FRAME_GENERATION__';

/** Bumped when the message shape changes, so a stale frame from a previous deployment is ignored. */
export const FRAME_PROTOCOL_VERSION = 1;

/**
 * Marks a task the parent has declared interactive. The server stylesheet keys the pointer cursor off
 * `.artemis-task:not([data-feedback])`, and this client strips `data-feedback` before the markup enters the
 * frame (it carries the student's feedback), so without this class every task would show the default cursor.
 */
export const INTERACTIVE_TASK_CLASS = 'artemis-task--interactive';

export const FRAME_SCRIPT = `(function () {
    'use strict';

    var GEN = '${GENERATION_PLACEHOLDER}';
    var V = ${FRAME_PROTOCOL_VERSION};
    var TASK_SELECTOR = '.artemis-task';
    var INTERACTIVE_CLASS = '${INTERACTIVE_TASK_CLASS}';

    function post(message) {
        message.v = V;
        message.gen = GEN;
        // The frame has an opaque origin, so there is no origin string the parent could be addressed by.
        // The parent authenticates us by comparing event.source against its own iframe, not by origin.
        parent.postMessage(message, '*');
    }

    function tasks() {
        return Array.prototype.slice.call(document.querySelectorAll(TASK_SELECTOR));
    }

    var lastHeight = -1;

    function reportHeight() {
        var body = document.body;
        var height = Math.max(document.documentElement.scrollHeight, body ? body.scrollHeight : 0);
        // De-duplicated here as well as in the parent: a ResizeObserver fires for every layout pass, and an
        // unchanged height is not news worth a cross-document message.
        if (height === lastHeight) {
            return;
        }
        lastHeight = height;
        post({ type: 'height', px: height });
    }

    function closestTask(node) {
        while (node && node !== document.body) {
            if (node.nodeType === 1 && node.classList && node.classList.contains('artemis-task')) {
                return node;
            }
            node = node.parentNode;
        }
        return null;
    }

    function activateFrom(event, node) {
        var task = closestTask(node);
        if (!task) {
            return false;
        }
        var index = tasks().indexOf(task);
        if (index === -1) {
            return false;
        }
        event.preventDefault();
        post({ type: 'task', index: index });
        return true;
    }

    document.addEventListener('click', function (event) {
        if (activateFrom(event, event.target)) {
            return;
        }
        // Without allow-top-navigation and allow-popups a link would otherwise navigate the frame itself,
        // replacing the statement with the target page and leaving the reader no way back.
        var node = event.target;
        while (node && node !== document.body) {
            if (node.nodeType === 1 && node.tagName === 'A' && node.getAttribute('href')) {
                event.preventDefault();
                post({ type: 'link', href: node.getAttribute('href') });
                return;
            }
            node = node.parentNode;
        }
    });

    // Reported so the parent can hand it back after a re-render. Replacing the document reloads the frame and
    // takes the focus with it, which for a keyboard reader means being thrown back to the top of the statement
    // every time a result arrives.
    document.addEventListener('focusin', function (event) {
        var task = closestTask(event.target);
        if (task) {
            post({ type: 'focus', index: tasks().indexOf(task) });
        }
    });

    document.addEventListener('keydown', function (event) {
        if (event.key !== 'Enter' && event.key !== ' ' && event.key !== 'Spacebar') {
            return;
        }
        activateFrom(event, document.activeElement);
    });

    window.addEventListener('message', function (event) {
        // The parent is the only sender we accept anything from.
        if (event.source !== parent) {
            return;
        }
        var data = event.data;
        if (!data || data.type !== 'interactive' || !Array.isArray(data.tasks)) {
            return;
        }
        var labels = {};
        for (var i = 0; i < data.tasks.length; i++) {
            var entry = data.tasks[i];
            if (entry && typeof entry.index === 'number' && typeof entry.label === 'string') {
                labels[entry.index] = entry.label;
            }
        }
        var elements = tasks();
        for (var index = 0; index < elements.length; index++) {
            var element = elements[index];
            var label = labels[index];
            if (typeof label === 'string') {
                element.setAttribute('role', 'button');
                element.setAttribute('tabindex', '0');
                // ARIA prohibits aria-label on role=generic, so it is set only on the interactive branch.
                element.setAttribute('aria-label', label);
                element.classList.add(INTERACTIVE_CLASS);
            } else {
                element.removeAttribute('role');
                element.removeAttribute('tabindex');
                element.removeAttribute('aria-label');
                element.classList.remove(INTERACTIVE_CLASS);
            }
        }
        if (typeof data.focusIndex === 'number' && data.focusIndex >= 0) {
            var focusTarget = elements[data.focusIndex];
            if (focusTarget && typeof focusTarget.focus === 'function') {
                focusTarget.focus();
            }
        }
        reportHeight();
    });

    if (typeof ResizeObserver === 'function') {
        new ResizeObserver(reportHeight).observe(document.documentElement);
    }
    // Images and fonts settle after the first layout pass, and a late-loading image changes the height.
    window.addEventListener('load', reportHeight);
    reportHeight();
    post({ type: 'ready' });
})();`;
