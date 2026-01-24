#ifndef HELPERS_HPP
#define HELPERS_HPP

#pragma once

#include <string>
#include <sstream>
#include <algorithm>
#include <functional>
#include <iostream>
#include <cctype>

/**
 * @file Helpers.hpp
 * @brief Helper functions for testing / autograder
 *
 * Includes:
 * - Capturing output and input streams
 * - String normalization
 * - Flexible substring checks
 */
namespace Helpers {

    namespace Normalize {

        /**
         * @brief Removes unnecessary whitespace and line endings.
         *
         * - Converts Windows CRLF (\r\n) to LF (\n)
         * - Trims trailing spaces/tabs per line
         *
         * @param s Input string
         * @return Normalized string
         */
        inline std::string normalizeWhitespace(std::string s) {
            s.erase(std::remove(s.begin(), s.end(), '\r'), s.end());

            std::stringstream in(s);
            std::string line, result;

            while (std::getline(in, line)) {
                line.erase(line.find_last_not_of(" \t") + 1);
                result += line + '\n';
            }
            return result;
        }

        /**
         * @brief Removes all non-alphanumeric characters from a string.
         *
         * Useful to ignore punctuation.
         *
         * @param s Input string
         * @return String without punctuation
         */
        inline std::string removePunctuation(const std::string& s) {
            std::string result;
            for (const char c : s) {
                if (std::isalnum(static_cast<unsigned char>(c))) {
                    result += c;
                }
            }
            return result;
        }

        /**
         * @brief Converts all characters in a string to lowercase.
         *
         * @param s Input string
         * @return Lowercase string
         */
        inline std::string toLower(const std::string& s) {
            std::string result = s;
            std::transform(result.begin(), result.end(), result.begin(),
                           [](unsigned char c){ return std::tolower(c); });
            return result;
        }

        /**
         * @brief Normalizes a string for "forgiving" comparisons.
         *
         * Performs the following steps:
         * - Removes punctuation
         * - Converts all letters to lowercase
         * - Normalizes whitespace (CRLF -> LF, trims trailing spaces)
         *
         * @param s Input string
         * @return Normalized string
         */
        inline std::string normalizeString(const std::string& s) {
            return normalizeWhitespace(toLower(removePunctuation(s)));
        }
    } // namespace Normalize

    /**
     * @brief Executes a function and captures its std::cout output.
     *
     * - Accepts any number of function arguments
     * - Returns the captured output as a normalized string
     *
     * Example:
     *   auto out = Helpers::GetOutput(printSum, 3, 4);
     *
     * @tparam Fn Callable type (function pointer, lambda, etc.)
     * @tparam Args Types of arguments for fn
     * @param fn Function whose output will be captured
     * @param args Arguments for fn
     * @return Normalized output string
     */
    template<typename Fn, typename... Args>
    std::string GetOutput(Fn&& fn, Args&&... args) {
        std::streambuf* originalCout = std::cout.rdbuf();

        std::ostringstream out;
        std::cout.rdbuf(out.rdbuf());

        std::forward<Fn>(fn)(std::forward<Args>(args)...);

        std::cout.rdbuf(originalCout);
        return out.str();
    }

    /**
     * @brief Builds a string from multiple inputs to simulate std::cin.
     *
     * Each argument is separated by a space.
     *
     * @tparam Args Types of input values
     * @param args Input values
     * @return Concatenated string suitable for std::istringstream
     */
    template<typename... Args>
    std::string buildInput(Args&&... args) {
        std::ostringstream oss;
        ((oss << std::forward<Args>(args) << ' '), ...);
        return oss.str();
    }

    /**
     * @brief Executes a function, feeds std::cin with input, and captures std::cout output.
     *
     * - Input is automatically generated from arbitrary arguments
     * - Output is returned as a normalized string
     *
     * Example:
     *   auto out = Helpers::GetIO(studentFunction, 42, "Hello");
     *
     * @tparam Fn Callable type
     * @tparam Args Types of input arguments
     * @param fn Function that uses std::cin/std::cout
     * @param inputArgs Values to be fed into std::cin
     * @return Normalized output string
     */
    template<typename Fn, typename... Args>
    std::string GetIO(Fn&& fn, Args&&... inputArgs) {
        std::streambuf* originalCout = std::cout.rdbuf();
        std::streambuf* originalCin  = std::cin.rdbuf();

        std::istringstream in(buildInput(std::forward<Args>(inputArgs)...));
        std::ostringstream out;

        std::cout.rdbuf(out.rdbuf());
        std::cin.rdbuf(in.rdbuf());

        std::forward<Fn>(fn)();

        std::cout.rdbuf(originalCout);
        std::cin.rdbuf(originalCin);

        return out.str();
    }

    /**
     * @brief Checks if a string contains another string in a "forgiving" way.
     *
     * - Ignores case
     * - Ignores punctuation
     * - Ignores whitespace
     *
     * Example:
     *   ContainsSubstring("Sum: 42", "sum42"); // true
     *
     * @param output Full output string
     * @param stringToContain Substring to check
     * @return true if the substring is found
     */
    inline bool ContainsSubstring(const std::string& output, const std::string& stringToContain) {
        const auto normalizedOutput = Normalize::normalizeString(output);
        const auto normalizedTarget = Normalize::normalizeString(stringToContain);

        return normalizedOutput.find(normalizedTarget) != std::string::npos;
    }
} // namespace Helpers

#endif //HELPERS_HPP
