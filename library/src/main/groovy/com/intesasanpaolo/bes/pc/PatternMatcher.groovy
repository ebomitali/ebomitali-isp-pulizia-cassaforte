package com.intesasanpaolo.bes.pc
import groovy.util.logging.Slf4j
import java.util.regex.Pattern

/**
 * Converts z/OS-style glob patterns to regular expressions and tests whether a value matches.
 *
 * <p>Supported wildcards (aligned with the ISP deletion-rules convention):
 * <ul>
 *   <li>{@code %} — matches exactly one character (equivalent to regex {@code .}).</li>
 *   <li>{@code *} — matches zero or more characters (equivalent to regex {@code .*}).</li>
 * </ul>
 *
 * All other characters are treated as literals.
 *
 * <p>Example: pattern {@code SJCL*} matches {@code SJCL}, {@code SJCLLOAD}, {@code SJCL123},
 * while {@code S%CL} matches only four-character strings starting with S and ending with CL.
 *
 * @see DeletionRule#typePattern
 */
@Slf4j
class PatternMatcher {
    boolean matches(String pattern, String value) {
        def regex = pattern.trim().collect { c ->
            switch (c) {
                case '%': return '.'
                case '*': return '.*'
                default:  return Pattern.quote(String.valueOf(c))
            }
        }.join('')
        def result = value.trim() ==~ regex
        log.trace("matches('{}', '{}') = {}", pattern, value, result)
        result
    }
}
