// This code is meaningful only for groovy scripts loaded via ScriptLoader.loadScript()
// from another script run in USS by groovyz

def createPuliziaCassaforteImpl() {
    return PuliziaCassaforteImpl.newInstance()
}