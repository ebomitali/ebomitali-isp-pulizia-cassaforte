import org.slf4j.LoggerFactory
// Control output
println "Script started..."

// Verifica il backend effettivo di SLF4J, returns org.slf4j.simple.SimpleLogger
def log = LoggerFactory.getLogger(this.class)
println "Logger implementation: ${log.class.name}"

// Test log levels
log.info "This is an INFO message"
log.debug "This is a DEBUG message"
log.error "This is an ERROR message"