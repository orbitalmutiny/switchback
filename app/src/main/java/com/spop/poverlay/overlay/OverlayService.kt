package com.spop.poverlay.overlay

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.WindowManager.LayoutParams
import android.widget.FrameLayout
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.lifecycleScope
import com.spop.poverlay.ConfigurationRepository
import com.spop.poverlay.MainActivity
import com.spop.poverlay.R
import com.spop.poverlay.sensor.DeadSensorDetector
import com.spop.poverlay.sensor.interfaces.DummySensorInterface
import com.spop.poverlay.sensor.interfaces.PelotonBikeSensorInterfaceV1New
import com.spop.poverlay.sensor.interfaces.PelotonBikePlusSensorInterface
import com.spop.poverlay.ride.RideSessionRuntime
import com.spop.poverlay.ride.RideSessionStore
import com.spop.poverlay.ride.RideState
import com.spop.poverlay.route.RouteRideRuntime
import com.spop.poverlay.route.RouteStore
import com.spop.poverlay.util.IsBikePlus
import com.spop.poverlay.util.IsRunningOnPeloton
import com.spop.poverlay.util.LifecycleEnabledService
import com.spop.poverlay.util.calculateSpeedFromPelotonV1Power
import com.spop.poverlay.util.disableAnimations
import com.spop.poverlay.overlay.composables.ResistanceControlOverlay
import com.spop.poverlay.overlay.composables.RouteMapOverlay
import com.spop.poverlay.sensor.hr.BluetoothHeartRateMonitor
import com.spop.poverlay.sensor.hr.WorkoutServicesHeartRateMonitor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import timber.log.Timber
import java.io.File
import java.util.*
import kotlin.math.roundToInt


class OverlayService : LifecycleEnabledService() {
    companion object {
        private const val RideRecordingLogTag = "GrupettoRideRecording"
        private const val RouteResistanceLogTag = "SwitchbackRouteResistance"

        private const val DefaultOverlayFlags = (LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)


        private const val OverlayServiceId = 2032

        val OverlayHeightDp = 128.dp

        //Increases the size of the touch target during the hidden state
        const val HiddenTouchTargetMarginPx = 40

        //The percentage up or down a vertical drag must go before the overlay is relocated
        //Defined relative to the height of the screen
        const val VerticalMoveDragThreshold = .25f

        const val EdgeDockDragThreshold = .25f

        const val SideDockInsetPx = 32

        // Replace with DeadSensorInterface to simulate a dead sensor
        val EmulatorSensorInterface by lazy { DummySensorInterface() }

        private val mutableIsRunning = MutableStateFlow(false)
        val isRunning: StateFlow<Boolean> = mutableIsRunning
    }


    override fun onCreate() {
        super.onCreate()
        Log.i(RouteResistanceLogTag, "OverlayService created")
        mutableIsRunning.value = true

        val notificationManager = NotificationManagerCompat.from(this)
        startForeground(OverlayServiceId, prepareNotification(notificationManager))

        buildDialog()
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onDestroy() {
        mutableIsRunning.value = false
        Log.i(RouteResistanceLogTag, "OverlayService destroyed")
        super.onDestroy()
    }


    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Timber.i("overlay service received intent")
        Log.i(RouteResistanceLogTag, "OverlayService received intent")
        return START_STICKY
    }

    private fun buildDialog() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val screenSize = Size(
            resources.displayMetrics.widthPixels.toFloat(),
            resources.displayMetrics.heightPixels.toFloat()
        )

        val configurationRepository = ConfigurationRepository(applicationContext, this)
        val rideSessionStore = RideSessionStore(applicationContext)
        val routeStore = RouteStore(File(applicationContext.filesDir, "routes"))
        var bikePlusSensorInterface: PelotonBikePlusSensorInterface? = null
        val sensorInterface = if (IsRunningOnPeloton) {
            if (IsBikePlus) {
                PelotonBikePlusSensorInterface(this).also {
                    bikePlusSensorInterface = it
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            it.stop()
                        }
                    })
                }
            } else {
                PelotonBikeSensorInterfaceV1New(this).also {
                    lifecycle.addObserver(object : DefaultLifecycleObserver {
                        override fun onDestroy(owner: LifecycleOwner) {
                            it.stop()
                        }
                    })
                }
            }

        } else {
            EmulatorSensorInterface
        }
        Log.i(
            RouteResistanceLogTag,
            "OverlayService sensor ready: isPeloton=$IsRunningOnPeloton isBikePlus=$IsBikePlus bikePlusControlAvailable=${bikePlusSensorInterface != null}"
        )

        val heartRateMonitor = BluetoothHeartRateMonitor(applicationContext).also {
            lifecycle.addObserver(object : DefaultLifecycleObserver {
                override fun onDestroy(owner: LifecycleOwner) {
                    it.close()
                }
            })
        }

        val nativeHeartRateMonitor = if (IsRunningOnPeloton) {
            WorkoutServicesHeartRateMonitor(applicationContext).also {
                lifecycle.addObserver(object : DefaultLifecycleObserver {
                    override fun onDestroy(owner: LifecycleOwner) {
                        it.close()
                    }
                })
            }
        } else {
            null
        }

        // Combined HR: native (Peloton WorkoutServices) preferred, BLE fallback.
        // Native emits null until callback registration is confirmed; BLE carries values until then.
        val combinedHeartRateBpm = MutableStateFlow<Int?>(null)

        val sensorViewModel = OverlaySensorViewModel(
            application,
            sensorInterface,
            DeadSensorDetector(sensorInterface, this.coroutineContext),
            configurationRepository,
            bikePlusSensorInterface?.let {
                { it.getBikePlusService(configurationRepository) }
            },
            routeStore,
            combinedHeartRateBpm
        )

        val timerViewModel = OverlayTimerViewModel(
            application,
            configurationRepository
        )
        val dialogViewModel = OverlayDialogViewModel(screenSize, sensorViewModel.isMinimized)
        val initialHudLocation = when (configurationRepository.hudDockLocation.value) {
            "top" -> OverlayLocation.Top
            else -> OverlayLocation.Bottom
        }
        dialogViewModel.dialogLocation.value = initialHudLocation
        dialogViewModel.dialogGravity.value = initialHudLocation.gravity

        val layoutFlag = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            LayoutParams.TYPE_SYSTEM_ALERT
        }


        val defaultFlags = (LayoutParams.FLAG_NOT_TOUCH_MODAL
                or LayoutParams.FLAG_NOT_FOCUSABLE
                or LayoutParams.FLAG_LAYOUT_NO_LIMITS)

        val overlayParams = LayoutParams(
            200,
            LayoutParams.WRAP_CONTENT,
            layoutFlag,
            defaultFlags,
            PixelFormat.TRANSLUCENT
        ).apply {
            disableAnimations()
        }

        val touchTargetParams = LayoutParams().apply {
            copyFrom(overlayParams)
            disableAnimations()
        }

        val resistanceControlParams = LayoutParams(
            dpToPx(300),
            dpToPx(132),
            layoutFlag,
            LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.BOTTOM or Gravity.END
            x = dpToPx(24)
            y = dpToPx(150)
            disableAnimations()
        }

        val routeMapParams = LayoutParams(
            dpToPx(276),
            dpToPx(190),
            layoutFlag,
            LayoutParams.FLAG_NOT_TOUCH_MODAL or LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 0
            y = 0
            disableAnimations()
        }

        val touchTargetView = FrameLayout(this).apply {
            lifecycleViaService()
            setOnClickListener {
                sensorViewModel.onOverlayPressed()
            }
            layoutParams = ViewGroup.LayoutParams(100, 100)
        }

        val overlayView = ComposeView(this).apply {
            lifecycleViaService()
            setViewCompositionStrategy(
                ViewCompositionStrategy
                    .DisposeOnLifecycleDestroyed(this@OverlayService)
            )
            setContent {
                Overlay(
                    sensorViewModel,
                    timerViewModel,
                    OverlayHeightDp,
                    dialogViewModel.dialogLocation.collectAsState(),
                    dialogViewModel::processHorizontalDrag,
                    dialogViewModel::processVerticalDrag,
                    dialogViewModel::processHideProgress,
                    dialogViewModel::onOverlayLayout,
                    dialogViewModel::onTimerOverlayLayout
                )
            }
            alpha = 0.9f
            isFocusable = false
            clipToPadding = false
            clipChildren = false
            clipToOutline = false
        }
        wm.addView(overlayView, overlayParams)

        val resistanceControlView = ComposeView(this).apply {
            lifecycleViaService()
            visibility = View.GONE
            setViewCompositionStrategy(
                ViewCompositionStrategy
                    .DisposeOnLifecycleDestroyed(this@OverlayService)
            )
            setContent {
                val resistance = sensorViewModel.resistanceValue.collectAsState(
                    initial = SensorValuePlaceholderText
                ).value
                ResistanceControlOverlay(
                    resistance = resistance,
                    onStep = { sensorViewModel.onResistanceStep(it) },
                    onClose = {
                        configurationRepository.setBikePlusResistanceControlOverlayVisible(false)
                    }
                )
            }
            alpha = 0.94f
            isFocusable = false
        }
        wm.addView(resistanceControlView, resistanceControlParams)

        val routeMapView = ComposeView(this).apply {
            lifecycleViaService()
            visibility = View.GONE
            setViewCompositionStrategy(
                ViewCompositionStrategy
                    .DisposeOnLifecycleDestroyed(this@OverlayService)
            )
            setContent {
                val speedLabel = sensorViewModel.speedLabel.collectAsState(initial = "mph").value
                sensorViewModel.routeHudState.collectAsState(initial = null).value?.let {
                    RouteMapOverlay(
                        routeHudState = it,
                        useMph = speedLabel == "mph",
                        onCollapsedChanged = { isCollapsed ->
                            routeMapParams.width = dpToPx(if (isCollapsed) 44 else 276)
                            routeMapParams.height = dpToPx(if (isCollapsed) 92 else 190)
                            routeMapParams.x = 0
                            wm.updateViewLayout(this, routeMapParams)
                        },
                        onMoveBy = { deltaY ->
                            val maxY = (resources.displayMetrics.heightPixels - routeMapParams.height).coerceAtLeast(0)
                            routeMapParams.y = (routeMapParams.y + deltaY.roundToInt()).coerceIn(0, maxY)
                            wm.updateViewLayout(this, routeMapParams)
                        }
                    )
                }
            }
            alpha = 0.96f
            isFocusable = false
        }
        wm.addView(routeMapView, routeMapParams)

        wm.addView(touchTargetView, touchTargetParams)
        touchTargetView.clipChildren = false
        touchTargetView.clipToPadding = false
        //Subscribe to Dialog view model and update views
        lifecycleScope.launchWhenResumed {
            dialogViewModel.dialogLocation.collect {
                configurationRepository.setHudDockLocation(
                    if (it == OverlayLocation.Top) {
                        "top"
                    } else {
                        "bottom"
                    }
                )
            }
        }

        lifecycleScope.launchWhenResumed {
            combine(
                dialogViewModel.dialogOrigin,
                dialogViewModel.dialogLocation,
                dialogViewModel.dialogGravity,
                dialogViewModel.partialOverlayFlags,
                dialogViewModel.touchTargetHeight,
                dialogViewModel.dialogSizeParams,
                dialogViewModel.minimizedDialogSizeParams
            ) { values ->
                val origin = values[0] as Offset
                val location = values[1] as OverlayLocation
                val gravity = values[2] as Int
                val overlayFlags = values[3] as Int
                val touchTargetHeight = values[4] as Float
                val (width, height)  = values[5] as Pair<Int,Int>
                val (mWidth, mHeight)  = values[6] as Pair<Int,Int>
                overlayParams.x = if (location.isHorizontal) {
                    origin.x.roundToInt()
                } else {
                    SideDockInsetPx
                }
                overlayParams.y = origin.y.roundToInt()
                overlayParams.flags = DefaultOverlayFlags or overlayFlags
                overlayParams.gravity = gravity
                overlayParams.width = width
                overlayParams.height = if(sensorViewModel.isMinimized.value){
                    mHeight
                }else{
                    height
                }
                touchTargetParams.x = overlayParams.x
                touchTargetParams.y = origin.y.roundToInt()
                touchTargetParams.gravity = gravity
                touchTargetParams.width = mWidth
                touchTargetParams.height = touchTargetHeight.roundToInt()
                touchTargetView.visibility = if(touchTargetHeight > 0f){
                    View.VISIBLE
                }else{
                    View.GONE
                }
                disableClipOnParents(overlayView)
                wm.updateViewLayout(overlayView, overlayParams)
                wm.updateViewLayout(touchTargetView, touchTargetParams)
            }.collect {}
        }

        lifecycleScope.launchWhenResumed {
            combine(
                sensorViewModel.resistanceControlEnabled,
                configurationRepository.bikePlusResistanceControlOverlayVisible
            ) { controlsEnabled, overlayVisible ->
                controlsEnabled && overlayVisible
            }.collect {
                resistanceControlView.visibility = if (it) {
                    View.VISIBLE
                } else {
                    View.GONE
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            sensorViewModel.routeHudState.collect {
                routeMapView.visibility = if (it == null) {
                    View.GONE
                } else {
                    View.VISIBLE
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            configurationRepository.heartRateMonitorEnabled.collectLatest { isEnabled ->
                Log.i("SwitchbackHeartRate", "Heart rate setting changed: enabled=$isEnabled")
                if (isEnabled) {
                    nativeHeartRateMonitor?.start()
                    heartRateMonitor.start()
                } else {
                    nativeHeartRateMonitor?.stop()
                    heartRateMonitor.stop()
                }
            }
        }

        lifecycleScope.launchWhenResumed {
            if (nativeHeartRateMonitor != null) {
                combine(
                    nativeHeartRateMonitor.heartRateBpm,
                    heartRateMonitor.heartRateBpm
                ) { native, ble -> native ?: ble }
                    .collect { combinedHeartRateBpm.value = it }
            } else {
                heartRateMonitor.heartRateBpm.collect { combinedHeartRateBpm.value = it }
            }
        }

        lifecycleScope.launchWhenResumed {
            configurationRepository.rideSessionRecordingEnabled.collectLatest { isEnabled ->
                if (!isEnabled) {
                    Log.i(RideRecordingLogTag, "Ride session recording disabled")
                    return@collectLatest
                }

                val activeBikePlusSensorInterface = bikePlusSensorInterface
                if (activeBikePlusSensorInterface == null) {
                    Log.i(RideRecordingLogTag, "Ride session recording unavailable: Bike+ service not active")
                    return@collectLatest
                }

                val bikePlusService = activeBikePlusSensorInterface.getBikePlusService(configurationRepository)
                val rideSessionRuntime = RideSessionRuntime()
                val routeRideRuntime = RouteRideRuntime()
                val activeRoute = configurationRepository.activeRouteId.value?.let { routeStore.loadRoute(it) }
                val activeRouteStartPositionMeters = configurationRepository.activeRoutePositionMeters.value
                if (activeRoute != null) {
                    routeRideRuntime.start(activeRoute, activeRouteStartPositionMeters)
                    Log.i(RideRecordingLogTag, "Route session active: ${activeRoute.id}")
                }
                var lastState: RideState = rideSessionRuntime.state.value
                var lastLoggedSampleCount = 0

                Log.i(RideRecordingLogTag, "Ride session recording enabled")
                while (currentCoroutineContext().isActive) {
                    try {
                        val snapshot = withContext(Dispatchers.IO) {
                            bikePlusService.readTelemetrySnapshot()
                        }
                        val speedMph = calculateSpeedFromPelotonV1Power(snapshot.powerWatts)
                        rideSessionRuntime.onTelemetrySnapshot(
                            snapshot = snapshot,
                            speedMph = speedMph,
                            heartRateBpm = combinedHeartRateBpm.value,
                            routePositionMetersForDistance = activeRoute?.let {
                                { distanceMiles ->
                                    routeRideRuntime
                                        .updateDistanceMeters(
                                            activeRouteStartPositionMeters + (distanceMiles.toDouble() * 1609.344)
                                        )
                                        ?.positionMeters
                                        ?.toFloat()
                                }
                            }
                        )

                        val currentState = rideSessionRuntime.state.value
                        if (lastState !is RideState.Active && currentState is RideState.Active) {
                            Log.i(RideRecordingLogTag, "Ride auto-started")
                        }
                        lastState = currentState

                        val sampleCount = rideSessionRuntime.samples.size
                        if (sampleCount > 0 && sampleCount - lastLoggedSampleCount >= 30) {
                            lastLoggedSampleCount = sampleCount
                            Log.i(RideRecordingLogTag, "Ride samples recorded: $sampleCount")
                            rideSessionRuntime.currentRecord(name = activeRoute?.name)?.let { record ->
                                val file = withContext(Dispatchers.IO) {
                                    rideSessionStore.save(record)
                                }
                                Log.i(RideRecordingLogTag, "Ride session saved: ${file.name}")
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(RideRecordingLogTag, "Ride session recording failed", e)
                    }

                    delay(1000L)
                }
            }
        }
    }

    private fun dpToPx(dp: Int): Int =
        (dp * resources.displayMetrics.density).roundToInt()

    fun disableClipOnParents(v: View) {
        if (v.parent == null) {
            return
        }
        if (v is ViewGroup) {
            v.clipChildren = false
        }
        if (v.parent is View) {
            disableClipOnParents(v.parent as View)
        }
    }

    private fun prepareNotification(notificationManager: NotificationManagerCompat): Notification {
        val channelId = UUID.randomUUID().toString()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            notificationManager.getNotificationChannel(channelId) == null
        ) {
            val name: CharSequence = getString(R.string.overlay_notification)
            val importance = NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            channel.enableVibration(false)
            notificationManager.createNotificationChannel(channel)
        }
        val notificationIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        }

        val intentFlags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            notificationIntent,
            intentFlags
        )

        val notificationBuilder: NotificationCompat.Builder =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                NotificationCompat.Builder(this, channelId)
            } else {
                @Suppress("DEPRECATION")
                NotificationCompat.Builder(this)
            }

        notificationBuilder
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
        notificationBuilder.setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
        return notificationBuilder.build()
    }
}

