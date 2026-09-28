package `as`.today.missyou.data.repository

import java.util.UUID

/**
 * Id generation, injected so tests can produce deterministic, readable ids.
 */
fun interface IdGenerator {
    fun newId(): String

    companion object {
        val Random = IdGenerator { UUID.randomUUID().toString() }
    }
}
