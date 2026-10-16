package app

import android.app.Service
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.openauto.fakeunit.FakeFirmware
import com.openauto.fakeunit.Script

/**
 * The fake FYT toolkit service, bound by Dashwheel as com.syu.ms/app.ToolkitService
 * (the class name is the real firmware's, hence this package). Hands out the
 * fake modules' binders and starts the scripted car.
 */
class ToolkitService : Service() {
    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "fake FYT toolkit up")
        Script.play(this)
    }

    override fun onBind(intent: Intent?): IBinder {
        Log.i(TAG, "bound by ${intent?.action}")
        return FakeFirmware.toolkit
    }

    companion object {
        private const val TAG = "FakeToolkit"
    }
}
