package `as`.today.missyou

import android.app.Application
import android.content.Context
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import `as`.today.missyou.core.AppDispatchers
import `as`.today.missyou.core.DefaultAppDispatchers
import `as`.today.missyou.core.time.JournalClock
import `as`.today.missyou.core.time.SystemTimeProvider
import `as`.today.missyou.crypto.KeystoreJournalCipher
import `as`.today.missyou.crypto.KeystoreWrapKeyProvider
import `as`.today.missyou.crypto.VaultKeyStore
import `as`.today.missyou.data.database.JournalDatabase
import `as`.today.missyou.data.import.ImportArchiveStore
import `as`.today.missyou.data.import.JournalImporter
import `as`.today.missyou.data.local.EncryptedAttachmentStore
import `as`.today.missyou.data.repository.IdGenerator
import `as`.today.missyou.data.repository.JournalMapper
import `as`.today.missyou.data.repository.JournalRepositoryImpl
import `as`.today.missyou.data.settings.DataStoreSettingsRepository
import `as`.today.missyou.domain.model.AppLockMode
import `as`.today.missyou.domain.repository.AttachmentStore
import `as`.today.missyou.domain.repository.JournalRepository
import `as`.today.missyou.domain.repository.SettingsRepository
import `as`.today.missyou.security.AppLockManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.io.File

private val Context.preferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "today-settings",
)

/**
 * Manual dependency container.
 *
 * A DI framework would be the usual choice, but the specification explicitly
 * asks to avoid unnecessary dependencies for low-end development hardware, and a
 * hand-written graph of this size is both smaller and easier to audit. Everything
 * is created lazily, so nothing touches disk until a screen actually needs it.
 */
class AppContainer(private val context: Context) {

    val dispatchers: AppDispatchers = DefaultAppDispatchers

    /**
     * Used by the backup screen to read and write only the files the user picks
     * through the system picker.
     *
     * Narrower than exposing the `Context`, and deliberately not a storage
     * permission: the app never gets blanket access to shared storage.
     */
    val contentResolver: android.content.ContentResolver get() = context.contentResolver

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val appLock: AppLockManager by lazy { AppLockManager(context) }

    val settings: SettingsRepository by lazy {
        DataStoreSettingsRepository(context.preferencesDataStore)
    }

    /**
     * The rollover time, cached in a [StateFlow] so the repository can read it
     * synchronously on every write without collecting a flow each time.
     */
    private val rolloverState: StateFlow<Int> by lazy {
        settings.settings
            .map { it.rolloverMinutes }
            .stateIn(applicationScope, SharingStarted.Eagerly, 0)
    }

    private val lockModeState: StateFlow<AppLockMode> by lazy {
        settings.settings
            .map { it.appLockMode }
            .stateIn(applicationScope, SharingStarted.Eagerly, AppLockMode.OFF)
    }

    private val wrappedKeyStore: WrappedKeyPreferences by lazy {
        WrappedKeyPreferences(context)
    }

    private val vaultKeyStore: VaultKeyStore by lazy {
        VaultKeyStore(
            wrapKeyProvider = KeystoreWrapKeyProvider(
                authRequired = { lockModeState.value != AppLockMode.OFF },
            ),
            storage = wrappedKeyStore,
        )
    }

    private val journalCipher by lazy { KeystoreJournalCipher(vaultKeyStore) }

    private val database: JournalDatabase by lazy { JournalDatabase.getInstance(context) }

    val attachmentStore: AttachmentStore by lazy {
        EncryptedAttachmentStore(context, journalCipher, dispatchers)
    }

    val journal: JournalRepository by lazy {
        val mapper = JournalMapper(journalCipher)
        val importer = JournalImporter(
            dao = database.journalDao(),
            mapper = mapper,
            attachmentStore = attachmentStore,
            archiveStore = ImportArchiveStore(File(context.filesDir, "import-archive"), dispatchers),
            dispatchers = dispatchers,
        )
        JournalRepositoryImpl(
            dao = database.journalDao(),
            clock = JournalClock(SystemTimeProvider()),
            dispatchers = dispatchers,
            mapper = mapper,
            importer = importer,
            attachmentStore = attachmentStore,
            appVersion = BuildConfig.VERSION_NAME,
            idGenerator = IdGenerator.Random,
            rolloverProvider = { `as`.today.missyou.core.time.DayRollover(rolloverState.value) },
        )
    }

    val appVersion: String get() = BuildConfig.VERSION_NAME

    val attachmentCipher get() = journalCipher

    /**
     * Runs the start-up work that must happen before the UI can be trusted.
     *
     * Sealing expired days is the important part: the effective journal day is
     * written first, then every past entry is stamped. A crash in between is
     * harmless, because the next start repeats it.
     */
    suspend fun initialise() {
        journal.synchroniseClock()
    }

    /**
     * Re-wraps the vault key when the user enables or disables app lock.
     *
     * The data key never changes, so the journal does not have to be re-encrypted.
     */
    fun onLockModeChanged() {
        applicationScope.launch { vaultKeyStore.rewrapForCurrentAuthMode() }
    }

    suspend fun currentLockMode(): AppLockMode = settings.appLockMode.first()

    fun clearAllData() {
        wrappedKeyStore.clear()
        vaultKeyStore.destroy()
    }
}

/** Persists the Keystore-wrapped vault key. */
private class WrappedKeyPreferences(context: Context) : `as`.today.missyou.crypto.WrappedKeyStorage {

    private val preferences = context.getSharedPreferences("today-vault", Context.MODE_PRIVATE)

    override fun load(authRequired: Boolean): ByteArray? {
        val key = if (authRequired) KEY_AUTH else KEY_PLAIN
        val encoded = preferences.getString(key, null) ?: return null
        return runCatching { android.util.Base64.decode(encoded, android.util.Base64.NO_WRAP) }.getOrNull()
    }

    override fun save(wrapped: ByteArray, authRequired: Boolean) {
        val key = if (authRequired) KEY_AUTH else KEY_PLAIN
        val encoded = android.util.Base64.encodeToString(wrapped, android.util.Base64.NO_WRAP)
        preferences.edit {
            putString(key, encoded)
            // Only one mode can be active at a time; drop the stale wrap.
            if (authRequired) remove(KEY_PLAIN) else remove(KEY_AUTH)
        }
    }

    override fun clear() = preferences.edit { clear() }

    private companion object {
        const val KEY_PLAIN = "wrapped_plain"
        const val KEY_AUTH = "wrapped_auth"
    }
}

class TodayApplication : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // Deliberately not blocking startup: the container builds lazily and the
        // clock synchronisation runs on a background dispatcher.
    }
}
