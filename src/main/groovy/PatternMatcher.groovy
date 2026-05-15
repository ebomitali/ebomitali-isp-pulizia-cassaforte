import java.util.regex.Pattern

class PatternMatcher {
    boolean matches(String pattern, String value) {
        def regex = pattern.collect { c ->
            switch (c) {
                case '%': return '.'
                case '*': return '.*'
                default:  return Pattern.quote(String.valueOf(c))
            }
        }.join('')
        value ==~ regex
    }
}
