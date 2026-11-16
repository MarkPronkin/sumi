package dev.cinema.crunchyroll.data.account

import android.content.Context
import java.io.IOException
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

interface DeviceIdentity {suspend fun getOrCreate(): String}

class MemoryDeviceIdentity : DeviceIdentity {
    private val id=UUID.randomUUID().toString()
    override suspend fun getOrCreate()=id
}

/** Installation identity survives login retries and logout; it contains no account credential. */
class InstallationDeviceIdentity(context: Context) : DeviceIdentity {
    private val prefs=context.applicationContext.getSharedPreferences("cinema_installation",Context.MODE_PRIVATE)
    private val mutex=Mutex()
    override suspend fun getOrCreate(): String = mutex.withLock {
        withContext(Dispatchers.IO) {
            prefs.getString("device_id",null)?.takeIf {it.isNotBlank()} ?: UUID.randomUUID().toString().also {id ->
                if(!prefs.edit().putString("device_id",id).commit()) throw IOException("Couldn’t save this installation’s identity.")
            }
        }
    }
}
