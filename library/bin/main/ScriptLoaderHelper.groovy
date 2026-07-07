// This code is meaningful only for groovy scripts loaded via ScriptLoader.loadScript()
// from another script run in USS by groovyz
// It will be appended to the end of FullPuliziaCassaforte.groovy

def createPuliziaCassaforteImpl() {
    return PuliziaCassaforteImpl.newInstance()
}