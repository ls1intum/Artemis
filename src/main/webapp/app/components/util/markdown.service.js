(function () {
    'use strict';

    angular
        .module('artemisApp')
        .factory('ArtemisMarkdown', ArtemisMarkdown);

    function ArtemisMarkdown() {

        var service = {
            parseTextHintExplanation: parseTextHintExplanation,
            generateTextHintExplanation: generateTextHintExplanation,
            addHintAtCursor: addHintAtCursor,
            addExplanationAtCursor: addExplanationAtCursor
        };

        return service;

        /**
         * Parse the markdown text and apply the result to the target object's data
         *
         * The markdown text is split at [-h] and [-e] tags.
         *  => First part is text. Everything after [-h] is Hint, anything after [-e] is explanation
         *
         * @param markdownText {string} the markdown text to parse
         * @param targetObject {object} the object that the result will be saved in. Fields modified are "text", "hint" and "explanation".
         */
        function parseTextHintExplanation(markdownText, targetObject) {
            // split markdownText into main text, hint and explanation
            var markdownTextParts = markdownText.split(/\[-e]|\[-h]/g);
            targetObject.text = markdownTextParts[0].trim();
            if (markdownText.indexOf("[-h]") !== -1 && markdownText.indexOf("[-e]") !== -1) {
                if (markdownText.indexOf("[-h]") < markdownText.indexOf("[-e]")) {
                    targetObject.hint = markdownTextParts[1].trim();
                    targetObject.explanation = markdownTextParts[2].trim();
                } else {
                    targetObject.hint = markdownTextParts[2].trim();
                    targetObject.explanation = markdownTextParts[1].trim();
                }
            } else if (markdownText.indexOf("[-h]") !== -1) {
                targetObject.hint = markdownTextParts[1].trim();
                targetObject.explanation = null;
            } else if (markdownText.indexOf("[-e]") !== -1) {
                targetObject.hint = null;
                targetObject.explanation = markdownTextParts[1].trim();
            } else {
                targetObject.hint = null;
                targetObject.explanation = null;
            }
        }

        /**
         * generate the markdown text for the given source object
         *
         * The markdown is generated according to these rules:
         *
         * 1. First the value of sourceObject.text is inserted
         * 2. If hint and/or explanation exist, they are added after the text with a linebreak and tab in front of them
         * 3. Hint starts with [-h], explanation starts with [-e]
         *
         * @param sourceObject
         * @return {string}
         */
        function generateTextHintExplanation(sourceObject) {
            return (
                sourceObject.text +
                (sourceObject.hint ? "\n\t[-h] " + sourceObject.hint : "") +
                (sourceObject.explanation ? "\n\t[-e] " + sourceObject.explanation : "")
            );
        }

        /**
         * add the markdown for a hint at the current cursor location in the given editor
         *
         * @param editor {object} the editor into which the hint markdown will be inserted
         */
        function addHintAtCursor(editor) {
            var addedText = "\n\t[-h] Add a hint here (visible during the quiz via \"?\"-Button)";
            editor.focus();
            editor.insert(addedText);
            var range = editor.selection.getRange();
            range.setStart(range.start.row, range.start.column - addedText.length + 7);
            editor.selection.setRange(range);
        }

        /**
         * add the markdown for an explanation at the current cursor location in the given editor
         *
         * @param editor {object} the editor into which the explanation markdown will be inserted
         */
        function addExplanationAtCursor(editor) {
            var addedText = "\n\t[-e] Add an explanation here (only visible in feedback after quiz has ended)";
            editor.focus();
            editor.insert(addedText);
            var range = editor.selection.getRange();
            range.setStart(range.start.row, range.start.column - addedText.length + 7);
            editor.selection.setRange(range);
        }

    }

})();
