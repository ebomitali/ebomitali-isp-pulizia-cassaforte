@groovy.transform.BaseScript com.ibm.dbb.groovy.TaskScript baseScript

// Notify step execution
log.info("Running ${STEP}")
try {
// step code goes here
} catch (Exception e) {
    log.error("Error calculating GJCL condition variables", e)
    throw e
}

// should specify the return code
return 0