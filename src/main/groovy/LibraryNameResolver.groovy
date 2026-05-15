class LibraryNameResolver {
    String resolve(String template, String stage, String system) {
        template
            .replace('${C1STAGE}', stage  ?: '')
            .replace('${C1SYSTEM}', system ?: '')
    }

    // Derives TOCOLB library from resolved cassaforte library.
    // 4th qualifier '@@@@' → 'TO@@', 5th qualifier '@@@@@@@@' → 'COLB@@@@'.
    String toTocolbLibrary(String resolvedLibrary) {
        def parts = resolvedLibrary.split('\\.', -1)
        if (parts.size() >= 5) {
            parts[3] = parts[3].replace('@@@@', 'TO@@')
            parts[4] = parts[4].replace('@@@@@@@@', 'COLB@@@@')
        }
        parts.join('.')
    }
}
