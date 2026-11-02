package app.feltbook.core.transfer

import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.json.Json

/**
 * Служебные сообщения получателя → отправителю (Nearby BYTES, десятки байт):
 * `ACK` — «Принять» нажато и импорт записан, `token` = [PackageFrame.Encoded.token] принятого пакета;
 * `DECLINE` — отказ. Неизвестный `type` или чужой `protocol` — сообщение игнорируется.
 */
@Serializable
internal data class ControlMessage(val protocol: Int, val type: String, val token: String? = null) {
    companion object {
        const val ACK: String = "ACK"
        const val DECLINE: String = "DECLINE"

        private val json = Json { ignoreUnknownKeys = true }

        fun ack(token: String): ControlMessage = ControlMessage(PackageFrame.PROTOCOL_VERSION, ACK, token)

        fun decline(): ControlMessage = ControlMessage(PackageFrame.PROTOCOL_VERSION, DECLINE)

        fun decode(bytes: ByteArray): ControlMessage? = try {
            json.decodeFromString(serializer(), bytes.decodeToString())
                .takeIf { it.protocol == PackageFrame.PROTOCOL_VERSION && it.type in setOf(ACK, DECLINE) }
        } catch (_: SerializationException) {
            null
        } catch (_: IllegalArgumentException) {
            null
        }
    }

    fun encode(): ByteArray = json.encodeToString(serializer(), this).encodeToByteArray()
}
