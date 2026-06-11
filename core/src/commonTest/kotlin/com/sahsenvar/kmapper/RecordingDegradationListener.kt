package com.sahsenvar.kmapper

/** Test fixture: records every degradation event dispatched through KMapper. */
class RecordingDegradationListener : MappingListener {
    val events = mutableListOf<MappingDegradation>()

    override fun onDegradation(event: MappingDegradation) {
        events.add(event)
    }
}
