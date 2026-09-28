package `as`.today.missyou.data.import

import `as`.today.missyou.core.JournalErrorReason
import `as`.today.missyou.core.JournalException
import `as`.today.missyou.crypto.PassphraseCrypto
import `as`.today.missyou.data.RichDocumentCodec
import `as`.today.missyou.domain.model.BackupEntry
import `as`.today.missyou.domain.model.BackupFile
import `as`.today.missyou.domain.model.RichDocument
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.time.LocalDate

/**
 * Reads `.qjournal` backups.
 *
 * Both plain and passphrase-encrypted backups are accepted; the payload announces
 * which it is. Nothing is guessed, and a file that is not a backup produces a
 * readable error rather than a stack trace.
 */
internal object BackupReader {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    fun read(rawBytes: ByteArray, passphrase: CharArray?): BackupFile {
        val bytes = if (PassphraseCrypto.isEncryptedBackup(rawBytes)) {
            if (passphrase == null) {
                throw JournalException(
                    JournalErrorReason.ImportUnreadable,
                    detail = "Backup is encrypted but no passphrase was supplied",
                )
            }
            runCatching { PassphraseCrypto.decrypt(rawBytes, passphrase) }.getOrElse {
                throw JournalException(
                    JournalErrorReason.ImportUnreadable,
                    detail = "Could not decrypt backup: wrong passphrase or damaged file",
                    cause = it,
                )
            }
        } else {
            rawBytes
        }

        val text = runCatching {
            String(bytes, Charsets.UTF_8)
        }.getOrElse {
            throw JournalException(JournalErrorReason.ImportUnreadable, detail = "Backup is not valid UTF-8")
        }

        val file = runCatching {
            json.decodeFromString(BackupFile.serializer(), text)
        }.getOrElse {
            throw JournalException(
                JournalErrorReason.ImportUnreadable,
                detail = "Not a recognised Today backup file",
                cause = it,
            )
        }

        if (file.format != BackupFile.FORMAT) {
            throw JournalException(
                JournalErrorReason.ImportUnreadable,
                detail = "Unsupported backup format: ${file.format}",
            )
        }
        if (file.schemaVersion > BackupFile.CURRENT_SCHEMA_VERSION) {
            throw JournalException(
                JournalErrorReason.ImportUnreadable,
                detail = "Backup was written by a newer version of Today (schema ${file.schemaVersion})",
            )
        }
        return file
    }

    fun documentOf(entry: BackupEntry): RichDocument = RichDocumentCodec.decode(entry.document)

    fun dateOf(entry: BackupEntry): LocalDate = runCatching { LocalDate.parse(entry.journalDate) }
        .getOrElse {
            throw JournalException(
                JournalErrorReason.ImportUnreadable,
                detail = "Entry has an unreadable date: ${entry.journalDate}",
            )
        }
}

/** Reads a backup without loading it entirely into a string twice. */
internal fun BackupFile.entriesAsStream(): Sequence<BackupEntry> = entries.asSequence()

internal fun ByteArray.asInputStream() = ByteArrayInputStream(this)
