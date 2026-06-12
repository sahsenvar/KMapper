# MappingListener and the Degradation Sink

Two channels tell you everything mappings do in production:

- **`Result` failures** — hard errors, at the call site. ([Error
  handling](../error-handling/mapping-exception.md).)
- **The degradation sink** — every *absorbed* leniency: the broken date that became `null`,
  the dropped list element, the converged duplicate key. This page is that channel.

## Registering a listener

```kotlin
import com.sahsenvar.kmapper.KMapper
import com.sahsenvar.kmapper.MappingListener
import com.sahsenvar.kmapper.MappingDegradation

// once, at app startup (Application.onCreate / iOS app delegate):
KMapper.addListener(object : MappingListener {
    override fun onDegradation(event: MappingDegradation) {
        telemetry.count("mapping.degradation", event::class.simpleName)
    }

    override fun onError(source: Any, error: MappingException) {
        log.warn("mapping failed: ${error.message}")
    }
})
```

`MappingListener` also offers `onMapStart`/`onMapComplete` for tracing. All methods have
default no-op bodies — override what you need. A ready-made `LoggingMappingListener(log)` is
included.

Listeners are pure observers by contract: an exception thrown inside a listener is suppressed
and can never affect the mapping or other listeners. Generated code checks
`KMapper.hasListeners` first, so the whole channel costs ~nothing when unused.

## The degradation events

`MappingDegradation` is sealed; every event carries the field path:

| Event | Emitted when |
|-------|--------------|
| `AbsorbedConversionError` | a broken scalar took a default/null escape ([ladder](../basic-usage/null-safety.md) rung 2/3) — carries the cause |
| `DroppedBrokenElement` | a broken collection element was dropped |
| `DroppedNullElement` | a null element didn't fit a non-null element type and was dropped |
| `DuplicateKey` | two map keys converged after conversion; last entry won |
| `ConvergedDuplicateElement` | two set elements converged after conversion |

The split to remember (Rule 2 of the [mental model](../getting-started/mental-model.md)):
**declared absence is silent** — a nullable field receiving null is *not* an event;
**absorbed brokenness always reports** — data was present and lost, telemetry should know.

## The debug/release pattern

```kotlin
if (BuildConfig.DEBUG) {
    KMapper.addListener(object : MappingListener {
        override fun onDegradation(event: MappingDegradation) =
            error("degradation in debug build: $event") // crash loudly while developing…
    })
} else {
    KMapper.addListener(MetricsListener) // …observe quietly in production
}
```

One warning: don't run mappings *inside* `onDegradation` — a degrading seam inside the tap
would recurse the dispatch.

> Next: **[Multi-Module Projects →](../advanced/multi-module.md)**
