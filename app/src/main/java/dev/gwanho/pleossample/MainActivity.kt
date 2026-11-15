package dev.gwanho.pleossample

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import android.media.ToneGenerator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.pleos.playground.location.fused.FusedLocation
import ai.pleos.playground.location.fused.listener.FusedLocationListener
import ai.pleos.playground.location.fused.listener.ProximityAlertListener
import ai.pleos.playground.location.fused.model.LocationData
import ai.pleos.playground.location.fused.model.ProximityAlertEvent
import ai.pleos.playground.location.fused.model.ProximityAlertRequest
import ai.pleos.playground.location.fused.model.ProximityTransition
import ai.pleos.playground.tts.TextToSpeech
import ai.pleos.playground.tts.constant.ErrorType
import ai.pleos.playground.tts.constant.Mode
import ai.pleos.playground.tts.listener.OnInitializeListener
import ai.pleos.playground.vehicle.Vehicle
import ai.pleos.playground.vehicle.api.model.area.DoorArea
import ai.pleos.playground.vehicle.api.model.area.SeatArea
import ai.pleos.playground.vehicle.api.model.capability.CapabilityConfig
import ai.pleos.playground.vehicle.constant.area.DoorValue
import ai.pleos.playground.vehicle.constant.area.SeatValue
import ai.pleos.playground.vehicle.constant.control.LightSwitch
import ai.pleos.playground.vehicle.constant.control.MoveControl
import ai.pleos.playground.vehicle.constant.control.VehicleGear
import ai.pleos.playground.vehicle.listener.DoorPositionListener
import ai.pleos.playground.vehicle.listener.GearSelectionListener
import ai.pleos.playground.vehicle.listener.SeatOccupancyListener
import dev.gwanho.pleossample.ui.Router
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

/**
 * 모심. 차량 신호 → CareSeatEngine → 시트·독서등·TTS·소리 명령. 250ms마다 UiState 스냅샷을 Compose에 넘긴다.
 * 실행: adb shell am start --user 10 -n dev.gwanho.pleossample/.MainActivity   로그: adb logcat -s CARE
 */
class MainActivity : ComponentActivity() {
    private lateinit var vehicle: Vehicle
    private lateinit var engine: CareSeatEngine
    private var tts: TextToSpeech? = null
    private var fused: FusedLocation? = null
    private val alertIds = mutableMapOf<Int, String>()
    private val handler = Handler(Looper.getMainLooper())
    private val prefs by lazy { getSharedPreferences("care", Context.MODE_PRIVATE) }
    private val capable = mutableMapOf<Pair<Seat, Axis>, Boolean>()
    private var lightCap = true
    private val lightOn = mutableMapOf<Seat, Boolean>()
    private val logs = ArrayDeque<String>()
    private var gear: String? = null; private var driverSeated: Boolean? = null
    private var invert = true
    private var volume = 70
    private var screens = listOf<Screen>()
    private var ui by mutableStateOf<UiState?>(null)
    private var lastSig: Triple<Seat, SignalCtx, SignalKind>? = null; private var lastBeepAt = 0L; private var redOn = false
    private var ambientOrig: Int? = null
    private var ambientPending = false
    private val unregisters = mutableListOf<() -> Unit>()
    private var targetTemp: Float? = null; private var currentTemp: Float? = null
    private var lowBeam = false                                // 전조등(로우빔) 켜짐 = 어두움 → 독서등을 켤 때
    private val heatSetAt = mutableMapOf<Seat, Long>()
    private var beltWarn: Boolean? = null                      // 에뮬레이터 벨트 경고등 기준선
    private val beltSeen = mutableMapOf<Seat, Boolean>()       // 실차 자리별 첫 값
    private var beltReal = false                               // 자리별 벨트 신호가 실제로 바뀐 적 있음(실차)
    private fun parentSeat() = Seat.values().firstOrNull { engine.isSeated(it) } ?: engine.prediction()?.first ?: Seat.PASSENGER
    private var naviHelper: ai.pleos.playground.navi.helper.NaviHelper? = null
    // 뒤 Task가 채우는 상태
    private var failure: String? = null                          // Task 13
    private var navi: Navi? = null  // Task 16
    private val heat = mutableMapOf<Seat, Int>(); private var heatCap = setOf<Seat>(); private var ambientArea: SeatArea? = null; private var memory = mapOf<Seat, IntRange>()   // Task 15, 16

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        invert = prefs.getBoolean("invert", true); volume = prefs.getInt("volume", 70)
        if (intent.getBooleanExtra("keepAudio", false)) keepAudioOn()   // 데모 녹화용: am start --ez keepAudio true
        intent.getStringExtra("seedRides")?.let { prefs.edit().putString("rides", it.replace('|', '\n')).apply() }   // 시연용 예시 기록: am start --es seedRides "at,SEAT,sec,helped,place,KIND|..."
        engine = CareSeatEngine(
            thresholdMs = prefs.getLong("thresholdMs", 20_000), requiredCount = prefs.getInt("requiredCount", 2), requiredBandCount = prefs.getInt("requiredBandCount", 3), helpWindowMs = prefs.getLong("helpWindowMs", 60_000),
            moveLimitMs = intent.getLongExtra("moveLimitMs", 3_000),   // 데모용: am start --el moveLimitMs 1000 (가상 콘솔 50% 이동)
            announceDelayMs = intent.getLongExtra("announceDelayMs", 3_000), doneMs = intent.getLongExtra("doneMs", 3_000),   // 데모용: 예고 문장·완료 문장이 끝날 때까지 화면을 둘 때 늘림
            confirmed = prefs.getBoolean("confirmed", false), guided = prefs.getBoolean("guided", false),
            counts = Seat.values().associateWith { prefs.getInt("count_$it", 0) },
            rides = prefs.getString("rides", "")!!.lines().mapNotNull { l -> runCatching { l.split(",").let { f -> Ride(f[0].toLong(), Seat.valueOf(f[1]), f[2].toLong(), f[3].toBoolean(), f[4].toIntOrNull(), RideKind.valueOf(f[5])) } }.getOrNull() },   // 깨진 줄은 버림
            places = prefs.getString("places", "")!!.lines().mapNotNull { l -> runCatching { l.split("\t").let { f -> Place(f[0].toInt(), f[1].toDouble(), f[2].toDouble(), f[3], f[4].toInt(), f[5].toInt(), f[6].toLong()) } }.getOrNull() },
            cold = { (currentTemp?.takeIf { it in 5f..45f }?.let { it < 20f }) ?: (targetTemp?.let { it > 20f } ?: false) },
            dark = { lowBeam },
            capable = { s, a -> capable[s to a] ?: true },
            postureMs = { s, p, a -> prefs.getLong("posture_${s}_${p}_$a", Long.MIN_VALUE).takeIf { it != Long.MIN_VALUE } },
        )
        engine.easyAccess = prefs.getBoolean("easyAccess", false); engine.heatEnabled = prefs.getBoolean("heatEnabled", true)
        if (prefs.getLong("pausedUntil", 0) > System.currentTimeMillis()) engine.setPaused(true, now())
        setContent {
            BackHandler(enabled = (ui?.screens?.isNotEmpty() == true)) { onEvent(UiEvent.Pop) }
            ui?.let { Router(it) { e -> onEvent(e) } }
        }
        vehicle = Vehicle(this); vehicle.initialize()
        registerAll()
        initTts()
        initNavi(); handler.post(naviPoll)
        if (checkSelfPermission(PERM_LOCATION) == PackageManager.PERMISSION_GRANTED) startFused() else requestPermissions(arrayOf(PERM_LOCATION), 1)
        handler.post(ticker)
        render()
    }

    private fun now() = System.currentTimeMillis()

    private fun registerAll() {
        safe("Gear.registerGearSelection") {
            val l = object : GearSelectionListener {
                override fun onSelected(g: VehicleGear?) { handler.post { gear = g?.name; apply(engine.onGear(g == VehicleGear.GEAR_PARK, now())) } }
                override fun onFailed(e: Exception) = log("Gear onFailed: ${e.message}")
            }
            vehicle.getGear().registerGearSelection(l); unregisters += { vehicle.getGear().unregisterGearSelection(l) }
        }
        fun seatOcc(v: SeatValue, onValue: (Boolean, Long) -> List<Command>) = safe("Seat.registerSeatOccupancy($v)") {
            val area = SeatArea(v)
            val l = object : SeatOccupancyListener {
                override fun onSeatOccupancyUpdated(b: Boolean?) { handler.post { apply(onValue((b ?: false) != invert, now())) } }
                override fun onFailed(e: Exception) = log("SeatOccupancy($v) onFailed: ${e.message}")
            }
            vehicle.getSeat().registerSeatOccupancy(area, l); unregisters += { vehicle.getSeat().unregisterSeatOccupancy(area, l) }
        }
        fun door(v: DoorValue, onValue: (Boolean, Long) -> List<Command>) = safe("Door.registerDoorPosition($v)") {
            val area = DoorArea(v)
            val l = object : DoorPositionListener {
                override fun onPositionUpdated(p: Int?) { handler.post { apply(onValue((p ?: 0) != 0, now())) } }
                override fun onFailed(e: Exception) = log("Door($v) onFailed: ${e.message}")
            }
            vehicle.getDoor().registerDoorPosition(area, l); unregisters += { vehicle.getDoor().unregisterDoorPosition(area, l) }
        }
        seatOcc(SeatValue.ROW_1_LEFT) { b, t -> driverSeated = b; engine.onDriverSeated(b, t) }
        door(DoorValue.ROW_1_LEFT) { b, t -> engine.onDriverDoor(b, t) }
        for (s in Seat.values()) { seatOcc(s.sdk()) { b, t -> engine.onSeated(s, b, t) }; door(s.door()) { b, t -> engine.onDoor(s, b, t) } }
        fun caps(axis: Axis, areas: List<SeatArea>) {
            handler.post {   // SDK 바인더 스레드 → main에서 쓴다(snapshot과 같은 스레드)
                val sv = areas.flatMap { it.values.toList() }.toSet()
                for (s in Seat.values()) capable[s to axis] = s.sdk() in sv
                log("${axis.label} capability: ${Seat.values().filter { capable[it to axis] == true }.joinToString { it.label }}")
                render()
            }
        }
        fun capFail(axis: Axis): (Exception) -> Unit = { e -> handler.post { log("check ${axis.name} capability onFailed: ${e.message}"); Seat.values().forEach { s -> capable[s to axis] = false }; render() } }
        safe("checkMoveSeatForeAftCapabilities") { vehicle.getSeat().checkMoveSeatForeAftCapabilities({ _: CapabilityConfig, a: List<SeatArea> -> caps(Axis.SLIDE, a) }, capFail(Axis.SLIDE)) }
        safe("checkMoveSeatBackrestAngleCapabilities") { vehicle.getSeat().checkMoveSeatBackrestAngleCapabilities({ _: CapabilityConfig, a: List<SeatArea> -> caps(Axis.BACKREST, a) }, capFail(Axis.BACKREST)) }
        safe("checkMoveSeatHeightCapabilities") { vehicle.getSeat().checkMoveSeatHeightCapabilities({ _: CapabilityConfig, a: List<SeatArea> -> caps(Axis.HEIGHT, a) }, capFail(Axis.HEIGHT)) }
        safe("checkMoveSeatTiltCapabilities") { vehicle.getSeat().checkMoveSeatTiltCapabilities({ _: CapabilityConfig, a: List<SeatArea> -> caps(Axis.TILT, a) }, capFail(Axis.TILT)) }
        safe("checkSeatMemoryCapabilities") { vehicle.getSeat().checkSeatMemoryCapabilities({ _: CapabilityConfig, m: Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> ->
            handler.post {
                memory = Seat.values().mapNotNull { s -> m.entries.firstOrNull { s.sdk() in it.key.values }?.let { s to (it.value.minValue..it.value.maxValue) } }.toMap()
                log("seat memory: ${memory.map { "${it.key.label} ${it.value}" }}"); render() } }, { log("checkSeatMemoryCapabilities onFailed: ${it.message}") }) }
        safe("checkAmbientLightBrightnessColorCapabilities") {
            vehicle.getLight().checkAmbientLightBrightnessColorCapabilities({ _: CapabilityConfig, areas: List<SeatArea> ->
                handler.post {
                    ambientArea = areas.firstOrNull(); log("ambient color capability: ${areas.size} area")
                    ambientArea?.let { a -> vehicle.getLight().getAmbientLightBrightnessColor(a, { c -> handler.post { ambientOrig = c; log("ambient orig color=$c") } }, { handler.post { log("getAmbientLightBrightnessColor onFailed: ${it.message}") } }) }
                    render()
                }
            }, { handler.post { ambientArea = null; log("checkAmbientLightBrightnessColorCapabilities onFailed: ${it.message}") } })
        }
        safe("checkReadingLightSwitchCapabilities") { vehicle.getLight().checkReadingLightSwitchCapabilities({ _: CapabilityConfig, m: Map<SeatArea, List<LightSwitch>> -> handler.post { lightCap = m.isNotEmpty(); render() } }, { handler.post { lightCap = false; log("checkReadingLightSwitchCapabilities onFailed: ${it.message}"); render() } }) }
        safe("checkSeatTemperatureCapabilities") { vehicle.getSeat().checkSeatTemperatureCapabilities({ _: CapabilityConfig, m: Map<SeatArea, ai.pleos.playground.vehicle.api.model.capability.AreaBounds<Int>> ->
            handler.post { heatCap = Seat.values().filter { s -> m.keys.any { s.sdk() in it.values } }.toSet(); log("seat heat capability: ${heatCap.joinToString { it.label }}"); render() } }, { log("checkSeatTemperatureCapabilities onFailed: ${it.message}") }) }
        for (s in Seat.values()) safe("registerHvacSeatTemperature(${s.label})") {
            val area = SeatArea(s.sdk()); val l = object : ai.pleos.playground.vehicle.listener.HvacSeatTemperatureListener {
                override fun onHvacSeatTemperatureUpdated(v: Int?) { handler.post { if ((v ?: 0) != 0 || now() - (heatSetAt[s] ?: 0L) > 2_000) { heat[s] = v ?: 0; apply(engine.onHeat(s, v ?: 0)) } } }
                override fun onFailed(e: Exception) = log("HvacSeatTemperature(${s.label}) onFailed: ${e.message}") }
            vehicle.getSeat().registerHvacSeatTemperature(area, l); unregisters += { vehicle.getSeat().unregisterHvacSeatTemperature(area, l) } }
        safe("registerHvacTemperature") { val area = SeatArea(SeatValue.ROW_1_RIGHT)
            val set = object : ai.pleos.playground.vehicle.listener.HvacTemperatureListener { override fun onHvacTemperatureUpdated(v: Float?) { handler.post { targetTemp = v }; log("목표 온도 $v") }; override fun onFailed(e: Exception) = log("HvacTemperature onFailed: ${e.message}") }
            val cur = object : ai.pleos.playground.vehicle.listener.HvacTemperatureListener { override fun onHvacTemperatureUpdated(v: Float?) { handler.post { currentTemp = v }; log("현재 온도 $v") }; override fun onFailed(e: Exception) = log("HvacTemperatureCurrent onFailed: ${e.message}") }
            vehicle.getHVAC().registerHvacTemperature(area, set); vehicle.getHVAC().registerHvacTemperatureCurrent(area, cur)
            unregisters += { vehicle.getHVAC().unregisterHvacTemperature(area, set); vehicle.getHVAC().unregisterHvacTemperatureCurrent(area, cur) } }
        for (s in Seat.values()) safe("registerSeatBeltBuckledState(${s.label})") {
            val area = SeatArea(s.sdk()); val l = object : ai.pleos.playground.vehicle.listener.SeatBeltBuckledStateListener {
                override fun onSeatBeltBuckledStateUpdated(b: Boolean?) { handler.post { val v = b ?: return@post; val prev = beltSeen.put(s, v); if (prev != null && prev != v) { beltReal = true; apply(engine.onBelt(s, v, now())) } } }
                override fun onFailed(e: Exception) = log("SeatBeltBuckledState(${s.label}) onFailed: ${e.message}") }
            vehicle.getSeat().registerSeatBeltBuckledState(area, l); unregisters += { vehicle.getSeat().unregisterSeatBeltBuckledState(area, l) } }
        safe("registerLowBeamLightState") { val l = object : ai.pleos.playground.vehicle.listener.LightStateListener {
                override fun onLightStateUpdated(s: ai.pleos.playground.vehicle.constant.control.LightState?) { handler.post { lowBeam = s == ai.pleos.playground.vehicle.constant.control.LightState.ON; log("전조등(로우빔) $s") } }
                override fun onFailed(e: Exception) = log("LowBeamLightState onFailed: ${e.message}") }
            vehicle.getLight().registerLowBeamLightState(l); unregisters += { vehicle.getLight().unregisterLowBeamLightState(l) }
            vehicle.getLight().getCurrentLowBeamLightState({ l.onLightStateUpdated(it) }, { log("getCurrentLowBeamLightState onFailed: ${it.message}") }) }
        safe("registerTelltales") { val l = object : ai.pleos.playground.vehicle.listener.TelltaleInfoListener {
                override fun onTelltaleInfoUpdated(t: ai.pleos.playground.vehicle.api.model.telltale.Telltales?) { handler.post { t ?: return@post; if (beltReal) return@post   // 자리별 벨트 신호가 한 번이라도 바뀐 차(실차)에서는 대체 신호를 쓰지 않음
                    val on = t.telltales.any { it.type == ai.pleos.playground.vehicle.constant.telltale.TelltaleType.SEAT_BELT && it.state != ai.pleos.playground.vehicle.constant.telltale.TelltaleState.OFF }   // 꺼진 경고등은 목록에서 빠진다(에뮬레이터 확인)
                    val prev = beltWarn; beltWarn = on
                    if (prev != null && prev != on) { log("벨트 경고등 ${if (on) "켜짐 → 풂" else "꺼짐 → 맴"} (${parentSeat().label})"); apply(engine.onBelt(parentSeat(), !on, now())) } } }
                override fun onFailed(e: Exception) = log("Telltales onFailed: ${e.message}") }
            vehicle.getSafety().registerTelltales(l); unregisters += { vehicle.getSafety().unregisterTelltales(l) } }
    }

    override fun onRequestPermissionsResult(code: Int, perms: Array<String>, results: IntArray) {   // ComponentActivity는 Kotlin이라 Array<String>
        super.onRequestPermissionsResult(code, perms, results)
        if (code == 1 && results.firstOrNull() == PackageManager.PERMISSION_GRANTED) startFused() else log("FUSED_LOCATION 거부됨 → 장소 조건·도착 안내 없이 진행")
    }

    private fun startFused() = safe("FusedLocation") {
        val f = FusedLocation(this)
        if (!f.isSupported()) { log("FusedLocation 미지원 → 장소 조건·도착 안내 없이 진행"); return@safe }
        f.initialize()
        f.registerFusedLocationCallback(object : FusedLocationListener {
            override fun onChanged(d: LocationData?) { handler.post { d ?: return@post; engine.onLocation(d.latitude, d.longitude) } }
            override fun onError(e: Exception) = log("FusedLocation onError: ${e.message}")
        })
        fused = f; log("FusedLocation ready"); syncArrivalAlert()
    }

    /** 확정 + 장소마다 도착 지오펜스. 지운 장소는 내린다. 등록 전 자리표시("")는 지우지 않고, 등록이 끝났을 때 이미 빠진 장소면 바로 내린다. */
    private fun syncArrivalAlert() {
        val f = fused ?: return
        val want = if (engine.isConfirmed) engine.places.associateBy { it.id } else emptyMap()
        (alertIds.keys - want.keys).forEach { id -> alertIds.remove(id)?.takeIf { it.isNotEmpty() }?.let { safe("removeProximityAlert") { f.removeProximityAlert(it) } } }
        for (p in want.values) if (p.id !in alertIds) {
            alertIds[p.id] = ""
            runCatching {
                f.addProximityAlert(ProximityAlertRequest(p.lat, p.lon, engine.placeRadiusM.toFloat(), setOf(ProximityTransition.ENTER)), object : ProximityAlertListener {
                    override fun onRegistered(id: String) { handler.post { if (p.id in alertIds) { alertIds[p.id] = id; log("도착 지오펜스 「${p.name}」 r=${engine.placeRadiusM.toInt()}m") } else safe("removeProximityAlert") { f.removeProximityAlert(id) } } }
                    override fun onTransition(e: ProximityAlertEvent) { handler.post { if (e.transition == ProximityTransition.ENTER) apply(engine.onArrive(now())) } }
                    override fun onError(e: Exception) { handler.post { alertIds.remove(p.id); log("addProximityAlert onError: ${e.message}") } }
                })
            }.onFailure { alertIds.remove(p.id); log("addProximityAlert threw ${it.javaClass.simpleName}: ${it.message}") }
        }
    }

    private fun initTts() = safe("TextToSpeech.initialize") {
        val t = TextToSpeech(this, Mode.ON_DEVICE)
        t.initialize(object : OnInitializeListener {
            override fun onSuccess() {
                t.setLocale(Locale.KOREA)
                t.addEventListener(object : ai.pleos.playground.tts.listener.EventListener {   // 문장이 끝나면 다음 문장 (initialize 전에 달면 SDK 안의 lateinit 이 터진다)
                    override fun onStart() {}
                    override fun onReady() {}
                    override fun onDone() { handler.post { sayEnded("done") } }
                    override fun onStop() { handler.post { sayEnded("stop") } }
                    override fun onError(e: ErrorType) { handler.post { sayEnded("error $e") } }
                    override fun onUpdatedRms(rms: Double) {}
                })
                tts = t; log("TTS ready (ON_DEVICE)"); sayNext.run()
            }
            override fun onFailure(e: ErrorType) = log("TTS init failed: $e → 음성 없이 진행")
        })
    }

    private fun initNavi() = safe("NaviHelper") {
        val n = ai.pleos.playground.navi.helper.NaviHelper(this)
        n.initialize({ log("NaviHelper ready"); handler.post { naviHelper = n }
            n.addListener(object : ai.pleos.playground.navi.helper.listener.NaviHelperEventListener {
                override fun onDestinationInfo(d: ai.pleos.playground.navi.data.DestinationInfo) { handler.post {
                    navi = if (d.remainTime > 0 && d.name.isNotEmpty()) Navi(d.remainTime, d.remainDistance, d.name, SimpleDateFormat("a h:mm", Locale.KOREA).format(Date(now() + d.remainTime * 1000L))) else null
                    if (d.remainTime in 1..60) apply(engine.onArrive(now())) } }
                override fun onDestinationArrived(a: ai.pleos.playground.navi.data.DestinationArrivedInfo) { handler.post { log("내비 도착: ${a.name}"); apply(engine.onArrive(now())) } }
                override fun onRouteEnded() { handler.post { navi = null } }
                override fun onRouteCancelled() { handler.post { navi = null } }
            }) }, { log("NaviHelper init failed: ${it.message}") })
    }
    private val naviPoll = object : Runnable { override fun run() { if (gear != null && gear != VehicleGear.GEAR_PARK.name) safe("getDestinationInfo") { naviHelper?.getDestinationInfo() }; handler.postDelayed(this, 5_000) } }

    private fun safe(what: String, block: () -> Unit) = runCatching(block).onFailure { log("$what threw ${it.javaClass.simpleName}: ${it.message}") }

    private val ticker = object : Runnable {   // 엔진 tick 50ms, 화면 250ms, 저장 1s
        var n = 0
        override fun run() { apply(engine.tick(now())); if (n % 5 == 0) render(); if (n % 20 == 0) persist(); n++; handler.postDelayed(this, 50) }
    }

    // 소리: 띵(짧은 비프), 딩동(두 음). 설정 「신호 소리 크기」가 볼륨
    private var tone: ToneGenerator? = null
    private var toneVolume = -1
    // 데모 녹화용: 들리지 않는 1LSB 소리를 계속 재생해 출력 스레드가 3초 뒤 대기(standby)로 안 들어가게 한다. 문장마다 스트림을 다시 열며 나는 시작 지지직(에뮬레이터 녹화) 실험
    private var keepAudio: AudioTrack? = null
    private fun keepAudioOn() = safe("keepAudio") {
        val sr = 48_000; val pcm = ShortArray(sr * 2) { if ((it / 2) % 2 == 0) 1 else -1 }   // 1초 스테레오, ±1 (-90dBFS)
        keepAudio = AudioTrack.Builder()
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setAudioFormat(AudioFormat.Builder().setSampleRate(sr).setEncoding(AudioFormat.ENCODING_PCM_16BIT).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
            .setTransferMode(AudioTrack.MODE_STATIC).setBufferSizeInBytes(pcm.size * 2).build()
            .also { it.write(pcm, 0, pcm.size); it.setLoopPoints(0, sr, -1); it.play() }
        log("keepAudio on")
    }
    private fun tone(): ToneGenerator? { if (toneVolume != volume) { runCatching { tone?.release() }; tone = runCatching { ToneGenerator(AudioManager.STREAM_MUSIC, volume) }.getOrNull(); toneVolume = volume }; return tone }
    private fun ding() { runCatching { tone()?.startTone(ToneGenerator.TONE_PROP_BEEP, 180) } }
    private fun dingdong() { runCatching { tone()?.startTone(ToneGenerator.TONE_PROP_BEEP2, 400) } }
    // TTS는 한 문장씩. 앞 문장이 끝났다는 이벤트(onDone/onStop/onError)가 온 뒤 다음 문장을 말해 문장이 중간에 끊기지 않게 한다. 이벤트가 안 오면 글자 수로 어림한 시간 뒤에 넘어간다
    private val sayQueue = ArrayDeque<String>(); private var saying: String? = null
    private val sayNext = object : Runnable { override fun run() {
        saying = null
        val t = tts ?: return   // 아직 준비 전이면 준비되는 대로(onSuccess) 이어서
        val text = sayQueue.removeFirstOrNull() ?: run { log("TTS idle"); return }
        saying = text; log("TTS: $text"); safe("TTS.speak") { t.speak(text, true) }
        handler.postDelayed(this, 1_500 + text.length * 250L)
    } }
    private fun say(text: String) { sayQueue += text; if (saying == null) sayNext.run() }
    private fun sayEnded(why: String) { if (saying == null) return; handler.removeCallbacks(sayNext); log("TTS $why: $saying"); sayNext.run() }
    private fun playDemoSounds() {   // 신호 카드 순서대로: 「등받이를 세울게요」 → 띵 · 띵 → 딩동
        say("등받이를 세울게요.")
        handler.postDelayed({ ding() }, 2_500); handler.postDelayed({ ding() }, 3_500); handler.postDelayed({ dingdong() }, 4_500)
    }

    private fun apply(cmds: List<Command>) {
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { apply(cmds) }; return }
        for (c in cmds) when (c) {
            is Command.Log -> log(c.text)
            is Command.Say -> say(c.text)
            is Command.MoveSeat -> {
                val sv = c.seat.sdk()
                val ctl = when (c.move) { Move.BACKWARD -> MoveControl.BACKWARD; Move.FORWARD -> MoveControl.FORWARD; Move.STOP -> MoveControl.STOP }
                val name = when (c.axis) { Axis.SLIDE -> "moveSeatForeAft"; Axis.BACKREST -> "moveSeatBackrestAngle"; Axis.HEIGHT -> "moveSeatHeight"; Axis.TILT -> "moveSeatTilt" }
                safe(name) {
                    val ok = { _: SeatArea, m: MoveControl? -> log("$name($sv, $m) ok") }
                    val ng = { e: Exception -> log("$name($sv, $ctl) onFailed: ${e.message}")
                        if ("50001" !in (e.message ?: "") && c.move != Move.STOP) handler.post { failure = "${c.seat.label}이 끝까지 움직이지 않았어요. 멈춘 자리에서\n타셔도 안전해서 부모님께는 「됐어요」를 알려 드렸어요."; apply(engine.onMoveFailed(c.seat, c.axis, now())) } }
                    when (c.axis) {
                        Axis.SLIDE -> vehicle.getSeat().moveSeatForeAft(SeatArea(sv), ctl, ok, ng)
                        Axis.BACKREST -> vehicle.getSeat().moveSeatBackrestAngle(SeatArea(sv), ctl, ok, ng)
                        Axis.HEIGHT -> vehicle.getSeat().moveSeatHeight(SeatArea(sv), ctl, ok, ng)
                        Axis.TILT -> vehicle.getSeat().moveSeatTilt(SeatArea(sv), ctl, ok, ng)
                    }
                }
            }
            is Command.ReadingLight -> {
                val sv = c.seat.sdk(); val sw = if (c.on) LightSwitch.ON else LightSwitch.OFF
                lightOn[c.seat] = c.on
                safe("setReadingLightSwitch") { vehicle.getLight().setReadingLightSwitch(SeatArea(sv), sw, { log("setReadingLightSwitch($sv, $it) ok") }, { log("setReadingLightSwitch($sv, $sw) onFailed: ${it.message}") }) }
            }
            is Command.SeatHeat -> { val sv = c.seat.sdk(); heat[c.seat] = c.level; if (c.level > 0) heatSetAt[c.seat] = now()
                safe("setHvacSeatTemperature") { vehicle.getSeat().setHvacSeatTemperature(SeatArea(sv), c.level, { _, v -> log("setHvacSeatTemperature($sv, $v) ok") }, { log("setHvacSeatTemperature($sv, ${c.level}) onFailed: ${it.message}") }) } }
        }
        if (cmds.isNotEmpty()) { persist(); syncArrivalAlert(); render() }   // 명령 없는 Ride 기록은 ticker가 1초마다 저장
    }

    private fun persist() = prefs.edit().apply {
        putBoolean("confirmed", engine.isConfirmed); putBoolean("guided", engine.guided)
        engine.countsSnapshot.forEach { (s, n) -> putInt("count_$s", n) }
        putString("rides", engine.rides.joinToString("\n") { "${it.at},${it.seat},${it.seconds},${it.helped},${it.placeId ?: ""},${it.kind}" })
        putString("places", engine.places.joinToString("\n") { "${it.id}\t${it.lat}\t${it.lon}\t${it.name}\t${it.count}\t${it.helpedCount}\t${it.lastAt}" })
    }.apply()

    private fun nextMorning(): Long = Calendar.getInstance().apply { add(Calendar.DAY_OF_MONTH, 1); set(Calendar.HOUR_OF_DAY, 6); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0) }.timeInMillis

    private val pressed = mutableMapOf<Axis, Long>(); private var pressStart: Triple<Seat, Axis, Long>? = null
    private var pressDir = Move.FORWARD
    private fun manual(e: UiEvent.Manual) {
        if (e.move == Move.STOP) { pressStart?.let { (st, a, t0) -> if (st == e.seat && a == e.axis) { val dt = (now() - t0).coerceAtMost(engine.moveLimitMs); pressed[a] = (pressed[a] ?: 0L) + if (pressDir == Move.FORWARD) dt else -dt; pressStart = null } } }
        val out = engine.manual(e.seat, e.axis, e.move, now())
        if (e.move != Move.STOP && out.any { it is Command.MoveSeat && it.move != Move.STOP }) { pressStart = Triple(e.seat, e.axis, now()); pressDir = e.move }
        apply(out)
    }

    private fun onEvent(e: UiEvent) {
        when (e) {
            UiEvent.Start -> prefs.edit().putBoolean("started", true).apply()
            UiEvent.Confirm -> apply(engine.confirm())
            UiEvent.Reject -> apply(engine.reject())
            UiEvent.Unconfirm -> apply(engine.unconfirm())
            is UiEvent.Pause -> { apply(engine.setPaused(e.on, now())); prefs.edit().putLong("pausedUntil", if (e.on) nextMorning() else 0).apply() }
            is UiEvent.EasyAccess -> { engine.easyAccess = e.on; prefs.edit().putBoolean("easyAccess", e.on).apply() }
            is UiEvent.Push -> { if (e.s is Screen.PostureEdit) pressed.clear(); screens = if (e.s is Screen.Places && screens.lastOrNull() is Screen.Places) screens.dropLast(1) + e.s else screens + e.s }
            is UiEvent.Manual -> manual(e)
            is UiEvent.SavePosture -> { prefs.edit().apply { pressed.filter { it.value != 0L }.forEach { (a, ms) -> putLong("posture_${e.seat}_${e.posture}_$a", ms) } }.apply(); log("자세 기억 ${e.seat.label} ${e.posture.label}: $pressed"); pressed.clear() }
            is UiEvent.Memory -> safe("selectSeatMemory") { vehicle.getSeat().selectSeatMemory(SeatArea(e.seat.sdk()), e.slot) { log("selectSeatMemory(${e.seat.label}, ${e.slot}) onFailed: ${it.message}") }; log("selectSeatMemory(${e.seat.label}, ${e.slot})") }
            UiEvent.MobilityDismiss -> { prefs.edit().putInt("mobilitySeen", Calendar.getInstance().let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) + 1 }).apply(); screens = screens.filter { it != Screen.Mobility } }
            UiEvent.Pop -> { pressStart?.let { (st, ax, _) -> manual(UiEvent.Manual(st, ax, Move.STOP)) }; screens = screens.dropLast(1) }
            UiEvent.PlaySounds -> playDemoSounds()
            is UiEvent.Answer -> apply(engine.answerBoarding(e.yes, now()))
            UiEvent.DismissFailure -> failure = null
            UiEvent.StopAll -> { apply(engine.setPaused(true, now())); apply(engine.setPaused(false, now())); log("보호자 멈춤") }
            is UiEvent.SetThreshold -> { engine.thresholdMs = e.ms; prefs.edit().putLong("thresholdMs", e.ms).apply() }
            is UiEvent.SetRequired -> { engine.requiredCount = e.n; engine.requiredBandCount = e.n + 1; prefs.edit().putInt("requiredCount", e.n).putInt("requiredBandCount", e.n + 1).apply() }
            is UiEvent.SetHelpWindow -> { engine.helpWindowMs = e.ms; prefs.edit().putLong("helpWindowMs", e.ms).apply() }
            is UiEvent.Heat -> { engine.heatEnabled = e.on; prefs.edit().putBoolean("heatEnabled", e.on).apply() }
            is UiEvent.Volume -> { volume = e.v; prefs.edit().putInt("volume", e.v).apply() }
            UiEvent.ClearAll -> { engine.clearAll(); prefs.edit().apply { remove("confirmed"); Seat.values().forEach { remove("count_$it") }; remove("rides"); remove("places"); prefs.all.keys.filter { it.startsWith("posture_") }.forEach { remove(it) }; remove("mobilitySeen"); remove("pausedUntil") }.apply(); screens = emptyList(); syncArrivalAlert(); log("기록 지움") }
            is UiEvent.Invert -> { invert = e.on; prefs.edit().putBoolean("invert", e.on).apply() }
            is UiEvent.RenamePlace -> { engine.renamePlace(e.id, e.name); persist() }
            is UiEvent.RemovePlace -> { engine.removePlace(e.id); persist(); syncArrivalAlert() }
            UiEvent.OpenNavi -> safe("requestNavigationScreen") { naviHelper?.requestNavigationScreen(ai.pleos.playground.navi.data.NavigationScreenRequest.Foreground) ?: log("NaviHelper 없음") }
        }
        render()
    }

    private fun snapshot(): UiState {
        val t = now()
        return UiState(
            now = t, park = gear == null || gear == VehicleGear.GEAR_PARK.name, started = prefs.getBoolean("started", false),
            confirmed = engine.isConfirmed, pendingConfirm = engine.pendingConfirm, driverSeated = driverSeated,
            question = engine.question, failure = failure, signal = engine.signal(t), pending = engine.pending(), arrivalUntil = (engine.arrivedAt ?: 0L) + 10_000, navi = navi,
            rides = engine.rides, places = engine.places, bands = engine.bands(), prediction = engine.prediction(), requiredCount = engine.requiredCount, totalCount = engine.totalCount,
            seats = Seat.values().associateWith { s -> SeatUi(engine.isSeated(s), engine.isDoorOpen(s), engine.isMoving(s) || engine.isAnnouncing(s), engine.isPrepared(s), engine.status(s), lightOn[s] == true, heat[s] ?: 0, Axis.values().associateWith { a -> engine.level(s, a, t) }) },
            caps = capable.toMap(), lightCap = lightCap, ambientCap = ambientArea != null, heatCap = heatCap, memory = memory,
            settings = Settings(engine.thresholdMs, engine.requiredCount, engine.requiredBandCount, engine.helpWindowMs, engine.easyAccess, engine.heatEnabled, volume, engine.paused),
            invert = invert, logs = logs.toList(), screens = screens, mobilityDismissed = prefs.getInt("mobilitySeen", 0) == Calendar.getInstance().let { it.get(Calendar.YEAR) * 100 + it.get(Calendar.MONTH) + 1 }, pressed = pressed.toMap(),
        )
    }
    private fun ambient(color: Int?) {
        val a = ambientArea ?: return; val c = color ?: ambientOrig ?: return
        safe("setAmbientLightBrightnessColor") { vehicle.getLight().setAmbientLightBrightnessColor(a, c, { _, _ -> }, { log("setAmbientLightBrightnessColor onFailed: ${it.message}") }) }
    }
    /** 250ms마다. 신호 전이에 소리·조명. WAIT는 1초마다 띵 + 빨강 깜빡임. */
    private fun onSignal(sig: Signal?, t: Long) {
        val key = sig?.let { Triple(it.seat, it.ctx, it.kind) }
        if (key != lastSig) {
            lastSig = key; lastBeepAt = 0; redOn = false
            when (sig?.kind) { SignalKind.SOON -> ambient(AMBER); SignalKind.DONE -> { dingdong(); ambient(GREEN) }; SignalKind.WAIT -> {}; null -> if (gear == VehicleGear.GEAR_PARK.name) ambient(null) else ambientPending = true }   // 주행 중 조명 명령은 거부됨 → P에서 복원
        }
        if (sig?.kind == SignalKind.WAIT && t - lastBeepAt >= 1_000) { lastBeepAt = t; ding(); redOn = !redOn; ambient(if (redOn) RED else null) }
    }
    private fun render() { val t = now(); if (ambientPending && gear == VehicleGear.GEAR_PARK.name) { ambientPending = false; ambient(null) }; onSignal(engine.signal(t), t); ui = snapshot() }

    private val fmt = SimpleDateFormat("HH:mm:ss.SSS", Locale.US)
    private fun log(msg: String) {
        Log.i("CARE", msg)
        val line = "${fmt.format(Date())}  $msg"
        if (Looper.myLooper() != Looper.getMainLooper()) { handler.post { logs += line; while (logs.size > 300) logs.removeFirst() }; return }
        logs += line; while (logs.size > 300) logs.removeFirst()
    }

    private companion object {
        const val PERM_LOCATION = "pleos.car.permission.FUSED_LOCATION"
        const val RED = 0xFFB42318.toInt(); const val AMBER = 0xFFFF8A00.toInt(); const val GREEN = 0xFF0B7D43.toInt()
    }
    private fun Seat.sdk() = when (this) { Seat.PASSENGER -> SeatValue.ROW_1_RIGHT; Seat.REAR_LEFT -> SeatValue.ROW_2_LEFT; Seat.REAR_RIGHT -> SeatValue.ROW_2_RIGHT }
    private fun Seat.door() = when (this) { Seat.PASSENGER -> DoorValue.ROW_1_RIGHT; Seat.REAR_LEFT -> DoorValue.ROW_2_LEFT; Seat.REAR_RIGHT -> DoorValue.ROW_2_RIGHT }

    override fun onDestroy() {
        handler.removeCallbacks(ticker); handler.removeCallbacks(naviPoll); runCatching { naviHelper?.release() }
        unregisters.forEach { runCatching(it) }
        runCatching { tts?.release() }; runCatching { fused?.release() }; runCatching { tone?.release() }
        apply(engine.setPaused(true, now()))   // 움직이던 자리에 STOP
        vehicle.release()
        super.onDestroy()
    }
}
