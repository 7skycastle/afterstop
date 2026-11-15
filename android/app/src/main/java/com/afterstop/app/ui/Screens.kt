package com.afterstop.app.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AccountTree
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Dns
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.PlayCircle
import androidx.compose.material.icons.rounded.RecordVoiceOver
import androidx.compose.material.icons.rounded.Sensors
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.material.icons.rounded.Videocam
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.afterstop.app.BuildConfig
import com.afterstop.app.core.AnalysisSource
import com.afterstop.app.core.DemoScenario
import com.afterstop.app.core.FrameProvenance
import com.afterstop.app.core.Stage
import com.afterstop.app.data.IncidentRecord
import com.afterstop.app.insurance.InsuranceProfile
import com.afterstop.app.insurance.PhoneNumbers
import com.afterstop.app.oem.AdapterRegistry

/** 아래쪽(또는 왼쪽) 메뉴에 보이는 화면. 앱 구조 화면은 설정 안에서만 연다. */
private val NAV = listOf(Screen.HOME, Screen.DEMO, Screen.LIVE, Screen.RECORDS, Screen.SETTINGS)

private fun iconOf(sc: Screen): ImageVector = when (sc) {
    Screen.HOME -> Icons.Rounded.Home
    Screen.DEMO -> Icons.Rounded.PlayCircle
    Screen.LIVE -> Icons.Rounded.Link
    Screen.COMPONENTS -> Icons.Rounded.AccountTree
    Screen.RECORDS -> Icons.Rounded.Description
    Screen.SETTINGS -> Icons.Rounded.Settings
}

/** K_study BottomNav 형식: 아이콘 + 라벨, 활성 탭은 연한 파란 알약 배경 */
@Composable
private fun NavItem(sc: Screen, active: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    Column(
        modifier.clip(RoundedCornerShape(18.dp)).clickable(onClick = onClick).semantics { contentDescription = sc.label + if (active) " 선택됨" else "" }
            .padding(vertical = 6.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Box(Modifier.size(width = 52.dp, height = 32.dp).clip(RoundedCornerShape(16.dp)).background(if (active) p.blueSoft else androidx.compose.ui.graphics.Color.Transparent), contentAlignment = Alignment.Center) {
            Icon(iconOf(sc), null, tint = if (active) p.blue else p.sub, modifier = Modifier.size(22.dp))
        }
        T(sc.label, size = 12.sp, weight = if (active) FontWeight.ExtraBold else FontWeight.SemiBold, color = if (active) p.blue else p.sub, maxLines = 1)
    }
}

@Composable
fun AppRoot(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    val bg = Brush.verticalGradient(listOf(p.pageTop, p.panel, p.pageBottom))
    BoxWithConstraints(Modifier.fillMaxSize().background(bg).safeDrawingPadding()) {
        val wide = maxWidth >= 760.dp
        val activeNav = if (s.screen == Screen.COMPONENTS) Screen.SETTINGS else s.screen
        val content: @Composable (Modifier) -> Unit = { m ->
            Column(m) {
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(38.dp).clip(RoundedCornerShape(12.dp)).background(Brush.linearGradient(listOf(p.blue, p.accent))), contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Shield, null, tint = androidx.compose.ui.graphics.Color.White, modifier = Modifier.size(22.dp))
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        T("멈춘 다음", size = 20.sp, weight = FontWeight.Black)
                        T("사고 직후, 할 일을 하나씩", size = 12.sp, weight = FontWeight.SemiBold, color = p.sub)
                    }
                    Row(Modifier.clip(RoundedCornerShape(50)).background(p.soft).border(1.dp, p.line, RoundedCornerShape(50)).clickable(onClick = vm::toggleNight).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically) {
                        Icon(if (s.night) Icons.Rounded.LightMode else Icons.Rounded.DarkMode, null, tint = p.blue, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp)); T(if (s.night) "밝은 화면" else "어두운 화면", size = 13.sp, weight = FontWeight.Bold)
                    }
                }
                // 체험과 실제 연결은 늘 같은 자리에서 구분해 보여 준다.
                when (s.screen) {
                    Screen.DEMO -> Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Badge("체험 중 · 실제 사고가 아니에요 · 화면과 결과는 예시예요", Tone.WARN) }
                    Screen.LIVE -> Box(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) { Badge("실제 연결 · 연결한 카메라·영상·서버만 써요", Tone.OK) }
                    else -> {}
                }
                Spacer(Modifier.height(10.dp))
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    when (s.screen) {
                        Screen.HOME -> HomeScreen(vm, s)
                        Screen.DEMO -> DemoScreen(vm, s)
                        Screen.LIVE -> LiveScreen(vm, s)
                        Screen.COMPONENTS -> ComponentsScreen(vm, s)
                        Screen.RECORDS -> RecordsScreen(vm, s)
                        Screen.SETTINGS -> SettingsScreen(vm, s)
                    }
                }
            }
        }
        if (wide) {
            Row(Modifier.fillMaxSize()) {
                Column(Modifier.width(92.dp).fillMaxHeight().padding(vertical = 12.dp, horizontal = 8.dp).clip(RoundedCornerShape(24.dp)).background(p.panel).border(1.dp, p.line, RoundedCornerShape(24.dp)).padding(vertical = 10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    NAV.forEach { sc -> NavItem(sc, activeNav == sc, { vm.go(sc) }, Modifier.fillMaxWidth()) }
                }
                content(Modifier.weight(1f).fillMaxHeight())
            }
        } else {
            Column(Modifier.fillMaxSize()) {
                content(Modifier.weight(1f).fillMaxWidth())
                Row(Modifier.fillMaxWidth().background(p.panel).border(1.dp, p.line).padding(horizontal = 6.dp, vertical = 4.dp)) {
                    NAV.forEach { sc -> NavItem(sc, activeNav == sc, { vm.go(sc) }, Modifier.weight(1f)) }
                }
            }
        }
    }
}

/** 넓은 화면(태블릿 가로·차량 화면)은 2열, 좁은 화면(휴대폰 세로)은 1열. */
@Composable
fun TwoPane(left: @Composable () -> Unit, right: @Composable () -> Unit) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        if (maxWidth >= 760.dp) {
            Row(Modifier.fillMaxSize().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                Column(Modifier.weight(1.1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { left(); Spacer(Modifier.height(16.dp)) }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) { right(); Spacer(Modifier.height(16.dp)) }
            }
        } else {
            Column(Modifier.fillMaxSize().padding(horizontal = 12.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                right(); left(); Spacer(Modifier.height(16.dp))
            }
        }
    }
}

/** 홈 일러스트: 정차한 차량과 안내 말풍선 (벡터로 직접 그림) */
@Composable
private fun HeroIllustration(modifier: Modifier) {
    val p = LocalPalette.current
    Canvas(modifier) {
        val w = size.width; val h = size.height
        drawCircle(p.panel.copy(alpha = .75f), radius = w * .46f, center = Offset(w * .5f, h * .52f))
        drawRoundRect(p.blue.copy(alpha = .12f), topLeft = Offset(w * .08f, h * .72f), size = Size(w * .84f, h * .08f), cornerRadius = CornerRadius(h * .04f))
        val car = Path().apply {
            moveTo(w * .2f, h * .7f); lineTo(w * .22f, h * .56f); quadraticTo(w * .26f, h * .44f, w * .38f, h * .43f)
            lineTo(w * .6f, h * .43f); quadraticTo(w * .7f, h * .44f, w * .76f, h * .56f); lineTo(w * .8f, h * .7f); close()
        }
        drawPath(car, Brush.linearGradient(listOf(p.blue, p.accent), Offset(w * .2f, h * .43f), Offset(w * .8f, h * .7f)))
        drawRoundRect(androidx.compose.ui.graphics.Color.White.copy(alpha = .85f), Offset(w * .33f, h * .47f), Size(w * .34f, h * .08f), CornerRadius(8f))
        drawCircle(p.ink, w * .06f, Offset(w * .32f, h * .72f)); drawCircle(p.ink, w * .06f, Offset(w * .68f, h * .72f))
        drawCircle(p.panel, w * .025f, Offset(w * .32f, h * .72f)); drawCircle(p.panel, w * .025f, Offset(w * .68f, h * .72f))
        val tri = Path().apply { moveTo(w * .78f, h * .1f); lineTo(w * .94f, h * .38f); lineTo(w * .62f, h * .38f); close() }
        drawPath(tri, androidx.compose.ui.graphics.Color(0xFFF59E0B))
        drawRect(androidx.compose.ui.graphics.Color.White, Offset(w * .772f, h * .19f), Size(w * .016f, h * .1f))
        drawRect(androidx.compose.ui.graphics.Color.White, Offset(w * .772f, h * .31f), Size(w * .016f, h * .025f))
        for (i in 0 until 5) {
            val bh = h * (.05f + (i % 3) * .035f)
            drawRoundRect(p.blue, Offset(w * (.1f + i * .045f), h * .24f - bh / 2), Size(w * .022f, bh), CornerRadius(6f))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun HomeScreen(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val wide = maxWidth >= 760.dp
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp))
                    .background(Brush.linearGradient(listOf(p.blueSoft, p.accentSoft))).border(1.dp, p.line, RoundedCornerShape(28.dp)).padding(22.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge("당황해도 괜찮아요", Tone.OK)
                    T(if (wide) "사고 직후 무엇부터 할지\n하윤이 차분히\n알려 드려요." else "사고 직후\n무엇부터 할지\n하윤이 알려 드려요.", size = if (wide) 34.sp else 22.sp, weight = FontWeight.Black, lineHeight = if (wide) 44.sp else 30.sp)
                    T("차가 멈추면 사고 장면을 저장하고, 지금 할 일을 한 가지씩 말해 드려요. 안전을 확인한 뒤에는 보험사에도 바로 연결해 드려요.", size = 14.sp, color = p.body)
                }
                HeroIllustration(Modifier.size(if (wide) 220.dp else 120.dp))
            }
            val demo: @Composable (Modifier) -> Unit = { m ->
                Panel(m) {
                    CardHeader(Icons.Rounded.PlayCircle, "먼저 체험해 보기", "1분")
                    T("실제 사고 없이, 사고가 났을 때 어떻게 안내하는지 미리 들어 봐요.", size = 14.sp, color = p.body)
                    Btn("체험 시작", vm::startDemo, Modifier.fillMaxWidth(), BtnKind.PRIMARY, large = true)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        DemoScenario.entries.forEach { sc -> Btn(sc.label.substringBefore(" ·"), { vm.selectDemoScenario(sc); vm.startDemo() }) }
                    }
                }
            }
            val insurance: @Composable (Modifier) -> Unit = { m ->
                Panel(m) {
                    CardHeader(Icons.Rounded.SupportAgent, "보험사 연결 준비", if (s.insurance.ready) "준비됨" else "입력 전")
                    if (s.insurance.ready) {
                        T("${s.insurance.company} · ${PhoneNumbers.display(s.insurance.phone)}", size = 17.sp, weight = FontWeight.Bold)
                        T("사고 뒤 \"보험사에 연결할까요?\"라고 한 번 물어요. \"응\"이라고 대답하면 연결해 드려요.", size = 14.sp, color = p.body)
                    } else {
                        T("보험사 이름과 긴급출동 번호를 넣어 두면, 사고 뒤 말 한마디로 연결해 드려요.", size = 14.sp, color = p.body)
                    }
                    Btn(if (s.insurance.ready) "보험 정보 고치기" else "보험 정보 넣기", { vm.go(Screen.SETTINGS) }, Modifier.fillMaxWidth(), large = true)
                }
            }
            val live: @Composable (Modifier) -> Unit = { m ->
                Panel(m) {
                    CardHeader(Icons.Rounded.Videocam, "카메라·블랙박스 연결", if (s.health?.reachable == true) "AI 연결됨" else null)
                    T("휴대폰 카메라나 블랙박스 영상으로 실제 장면을 저장하고 AI로 상황을 확인해요.", size = 14.sp, color = p.body)
                    Btn("연결하러 가기", { vm.go(Screen.LIVE) }, Modifier.fillMaxWidth(), large = true)
                }
            }
            if (wide) Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) { demo(Modifier.weight(1f)); insurance(Modifier.weight(1f)); live(Modifier.weight(1f)) }
            else { demo(Modifier.fillMaxWidth()); insurance(Modifier.fillMaxWidth()); live(Modifier.fillMaxWidth()) }
            Panel(Modifier.fillMaxWidth()) {
                CardHeader(Icons.Rounded.Info, "멈춘 다음이 하지 않는 일")
                T("· 다친 정도나 누구 잘못인지는 판단하지 않아요.\n· 확실하지 않으면 \"모른다\"고 말하고, 안 보인다고 안전하다고 하지 않아요.\n· 119 신고를 대신하지 않아요. 119 버튼은 전화 화면만 열어 드려요.\n· 보험사는 \"연결해 줘\"라고 대답했을 때만 연결해요.\n· 현대차 빌트인 캠·차량 충돌 신호·차량 SOS 연결은 아직 준비 중이에요.", size = 14.sp, color = p.body, lineHeight = 22.sp)
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DemoScreen(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    TwoPane(
        left = {
            Panel {
                CardHeader(Icons.Rounded.PlayCircle, "체험하기", s.demoScenario.label)
                T(s.demoHeadline, size = 22.sp, weight = FontWeight.Black)
                T(s.demoDescription, color = p.sub)
                Box {
                    s.demoScene?.let { Image(it, "체험용 그림 장면 · 실제 촬영 영상 아님", Modifier.fillMaxWidth().aspectRatio(960f / 400f).clip(RoundedCornerShape(12.dp)), contentScale = ContentScale.Fit) }
                    Box(Modifier.align(Alignment.TopEnd).padding(8.dp)) { Badge(s.demoClock) }
                }
                if (s.demoTelemetry.isNotEmpty()) T(s.demoTelemetry, size = 14.sp, weight = FontWeight.SemiBold)
                PipelineBar(s.demoStage)
                s.demoLog.forEach { T("· $it", size = 13.sp, color = p.sub) }
                T("어떤 상황을 볼까요?", size = 13.sp, color = p.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DemoScenario.entries.forEach { sc -> Btn(sc.label, { vm.selectDemoScenario(sc) }, kind = if (s.demoScenario == sc) BtnKind.PRIMARY else BtnKind.SECONDARY, enabled = !s.demoRunning) }
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("체험 시작", vm::startDemo, kind = BtnKind.PRIMARY, enabled = !s.demoRunning, large = true)
                    Btn("멈추기", { vm.stopDemo() }, enabled = s.demoRunning, large = true)
                    Btn("기록 남기기", vm::saveCurrentRecord, enabled = s.pendingRecord != null && !s.demoRunning, large = true)
                }
            }
            AnalysisPanel(s)
        },
        right = { GuidancePanel(s, vm, Modifier.fillMaxWidth(), demo = true) },
    )
}

@Composable
fun AnalysisPanel(s: UiState) {
    val p = LocalPalette.current
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(Icons.Rounded.Insights, null, tint = LocalPalette.current.blue)
            T("AI가 본 장면", Modifier.weight(1f), size = 17.sp, weight = FontWeight.ExtraBold)
            when (s.analysisSource) {
                AnalysisSource.FIXTURE -> Badge("체험용 예시 결과", Tone.WARN)
                AnalysisSource.LOCAL_VISION_MODEL -> Badge("AI 확인 결과", Tone.OK)
                null -> Badge(if (s.analysisBusy) "확인 중" else "아직 없음")
            }
        }
        T(s.analysisStatus, size = 14.sp, color = p.sub)
        FrameStrip(s.frames, s.observation?.evidenceFrames.orEmpty())
        s.observation?.let { o ->
            KeyValue("장소 모습", mapOf("tunnel" to "터널 안으로 보여요", "road" to "도로로 보여요", "unknown" to "알 수 없어요")[o.scene] ?: "알 수 없어요")
            KeyValue("화면 상태", mapOf("usable" to "잘 보여요", "limited" to "일부만 보여요", "unusable" to "잘 안 보여서 판단하기 어려워요")[o.visibility] ?: "")
            KeyValue("연기·불꽃", observedSummary(s))
            KeyValue("판단 근거", if (o.evidenceFrames.isEmpty()) "근거로 쓸 장면이 없어 판단을 미뤘어요" else "장면 " + o.evidenceFrames.joinToString(", ") { (it + 1).toString() } + "을 근거로 봤어요")
            o.observations.forEach { T("· $it", size = 14.sp) }
            if (s.analysisModel != null) T("AI 모델: ${s.analysisModel}", size = 12.sp, color = p.sub)
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LiveScreen(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    val camPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.onCameraPermission(it) }
    val locPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { vm.startSensor() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) vm.selectFile(uri, displayName(ctx, uri))
    }
    TwoPane(
        left = {
            Panel {
                CardHeader(Icons.Rounded.Videocam, "장면을 어디서 가져올까요?", "1")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("휴대폰 카메라", {
                        vm.selectCamera()
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) camPerm.launch(Manifest.permission.CAMERA)
                    }, kind = if (s.liveSource == LiveSource.CAMERA) BtnKind.PRIMARY else BtnKind.SECONDARY)
                    Btn("블랙박스 영상 파일", { picker.launch(arrayOf("video/*")) }, kind = if (s.liveSource == LiveSource.FILE) BtnKind.PRIMARY else BtnKind.SECONDARY)
                    Btn("차량 블랙박스 · 준비 중", vm::selectOem, kind = if (s.liveSource == LiveSource.OEM) BtnKind.PRIMARY else BtnKind.SECONDARY)
                }
                T(s.sourceMessage, size = 14.sp, color = p.sub)
                when (s.liveSource) {
                    LiveSource.CAMERA -> CameraBlock(vm, s)
                    LiveSource.FILE -> FileBlock(vm, s)
                    LiveSource.OEM -> Badge("아직 연결할 수 없어요", Tone.BAD)
                    LiveSource.NONE -> {}
                }
                Btn(if (s.captureBusy) "저장하는 중…" else "사고 장면 저장", { vm.captureFrames() }, enabled = !s.captureBusy && !s.analysisBusy && (s.liveSource == LiveSource.FILE && s.fileDurationMs > 0 || s.liveSource == LiveSource.CAMERA && s.cameraConnected))
            }
            Panel {
                CardHeader(Icons.Rounded.Insights, "AI로 상황 확인", "2")
                ServerStatusRows(s)
                s.urlMessage?.let { T(it, size = 13.sp, color = p.warn) }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(if (s.healthBusy) "확인 중…" else "AI 서버 연결 확인", vm::checkHealth, enabled = !s.healthBusy)
                    Btn("AI 서버 설정", { vm.go(Screen.SETTINGS) })
                }
                Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).toggleable(s.consent, role = Role.Checkbox, onValueChange = vm::setConsent).padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(s.consent, null)
                    T("저장한 장면(최대 6장)을 AI 서버로 보내 확인하는 데 동의해요.", size = 14.sp)
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn(if (s.analysisBusy) "확인하는 중…" else "AI로 상황 확인", vm::runAnalysis, kind = BtnKind.PRIMARY, enabled = !s.analysisBusy && !s.captureBusy && s.frames.isNotEmpty())
                    Btn("취소", vm::cancelAnalysis, enabled = s.analysisBusy || s.captureBusy)
                }
            }
            Panel {
                CardHeader(Icons.Rounded.Sensors, "충격은 어떻게 알아챌까요?", "3")
                T("충격을 알아채면 바로 안내를 시작하고, 장면 저장 → (동의했다면) AI 확인까지 이어서 해요.", size = 13.sp, color = p.sub)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("충격 테스트", { vm.onIncident(null, "충격 테스트 버튼 · 실제 충격 아님") }, enabled = !s.captureBusy && !s.analysisBusy)
                    if (s.eventsOn) Btn("서버 신호 받기 끄기", vm::disconnectEvents) else Btn("서버 신호 받기", vm::connectEvents)
                    if (s.sensorOn) Btn("휴대폰 센서 끄기", vm::stopSensor) else Btn("휴대폰 센서로 감지", {
                        if (ContextCompat.checkSelfPermission(ctx, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) locPerm.launch(Manifest.permission.ACCESS_FINE_LOCATION) else vm.startSensor()
                    })
                }
                T(s.eventsState, size = 13.sp, color = p.sub)
                T(s.sensorStatus, size = 13.sp, color = p.sub)
                T("휴대폰 센서는 차량용 충돌 센서가 아니에요. 실제로 급정거해서 시험하지 마세요.", size = 12.sp, color = p.warn)
                Btn("기록 남기기", vm::saveCurrentRecord, enabled = s.pendingRecord != null)
            }
            AnalysisPanel(s)
        },
        right = { GuidancePanel(s, vm, Modifier.fillMaxWidth(), demo = false) },
    )
}

@Composable
private fun ServerStatusRows(s: UiState) {
    val h = s.health
    Row(horizontalArrangement = Arrangement.spacedBy(6.dp), modifier = Modifier.horizontalScroll(rememberScrollState())) {
        Badge(when { h == null -> "AI 서버: 확인 전"; h.reachable -> "AI 서버 연결됨"; else -> "AI 서버 연결 안 됨" }, when { h == null -> Tone.NEUTRAL; h.reachable -> Tone.OK; else -> Tone.BAD })
        Badge(when {
            h == null || !h.reachable -> "AI 모델: 확인 전"
            !h.modelConfigured && s.server.model.isBlank() -> "AI 모델: 설정 안 됨"
            h.modelReady == "ready" -> "AI 모델 설치됨"
            h.modelReady == "missing" -> "AI 모델 없음"
            h.modelReady == "unreachable" -> "AI 모델 응답 없음"
            else -> "AI 모델: 확인 전"
        }, if (h?.modelReady == "ready") Tone.OK else if (h?.reachable == true) Tone.WARN else Tone.NEUTRAL)
    }
    h?.let {
        T(it.message + if (it.authRequired && !it.authorized) " · 접속 코드가 맞지 않아요" else "", size = 12.sp, color = LocalPalette.current.sub)
    }
}

@Composable
private fun CameraBlock(vm: AppViewModel, s: UiState) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val granted = remember(s.cameraNeedsBind) { ContextCompat.checkSelfPermission(ctx, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED }
    if (!granted) { T("카메라 권한을 기다리고 있어요.", size = 13.sp); return }
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    LaunchedEffect(s.cameraNeedsBind) { vm.camera.bind(owner, previewView) }
    DisposableEffect(Unit) { onDispose { vm.camera.release() } }
    AndroidView({ previewView }, Modifier.fillMaxWidth().heightIn(max = 260.dp).aspectRatio(16f / 9f).clip(RoundedCornerShape(16.dp)))
    Badge(if (s.cameraConnected) "카메라 연결됨 · 최근 장면 ${s.cameraFrames}개 보관 중" else "카메라 연결 안 됨", if (s.cameraConnected) Tone.OK else Tone.BAD)
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Btn("다시 연결", vm::reconnectCamera)
        Btn("연결 끊기", vm::disconnectCamera)
    }
}

@Composable
private fun FileBlock(vm: AppViewModel, s: UiState) {
    if (s.fileDurationMs <= 0) return
    s.filePreview?.let { Image(it, "고른 영상의 사고 순간 미리 보기", Modifier.fillMaxWidth().height(220.dp).clip(RoundedCornerShape(16.dp)).background(LocalPalette.current.ink), contentScale = ContentScale.Fit) }
    T("사고 순간: ${"%.1f".format(s.fileAnchorMs / 1000.0)}초 / 전체 ${"%.1f".format(s.fileDurationMs / 1000.0)}초 · 앞뒤 장면 6개를 저장해요", size = 14.sp, weight = FontWeight.SemiBold)
    var pos by remember(s.fileName) { mutableStateOf(s.fileAnchorMs.toFloat()) }
    Slider(pos, { pos = it }, valueRange = 0f..s.fileDurationMs.toFloat(), onValueChangeFinished = { vm.setAnchor(pos.toLong()) })
    Btn("영상 선택 지우기", vm::clearFile)
}

/** 심사·개발용: 실제 구조와 연결 상태. */
@Composable
fun ComponentsScreen(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    val h = s.health
    val rows = listOf(
        Triple(Stage.INPUT, "VideoSource · TelemetrySource", "휴대폰 카메라(CameraX), 사용자가 고른 영상 파일, 체험용 그림 장면, 예시 차량 신호, 휴대폰 센서(검증 안 됨). " +
            "현재: " + when (s.liveSource) { LiveSource.CAMERA -> if (s.cameraConnected) "카메라 연결됨" else "카메라 연결 안 됨"; LiveSource.FILE -> "영상 파일 ${s.fileName ?: ""}"; LiveSource.OEM -> "빌트인 캠 미연결"; LiveSource.NONE -> "실제 영상 입력 없음" }),
        Triple(Stage.DETECT, "IncidentDetector (RuleBasedIncidentDetector)", "충격 2.5g 이상 · 2초 이내 25km/h 이상 감속 · 5km/h 이하 정차 조합, 10초 중복 억제. 시제품 시험용 수치이며 실차 보정·인증 기준이 아닙니다."),
        Triple(Stage.BUFFER, "FrameRingBuffer", "약 1초에 1장, 최대 20장을 메모리에만 보관하고 사건 전후 최대 6장을 고릅니다. 현재 저장: ${s.frames.size}장"),
        Triple(Stage.ANALYZE, "VisionClient (Fixture / Server)", "앱 → Node.js 서버 → 로컬 Ollama 비전 모델. 응답은 스키마 검사 후 반영하며 실패 시 예시로 대체하지 않습니다. 서버: " +
            (if (h == null) "확인 전" else if (h.reachable) "도달 · 모델 ${h.modelReady}" else "연결 안 됨")),
        Triple(Stage.GUIDE, "GuidancePolicy · AudioPlayer · 보험사 연결", "관찰 단서와 사용자 알림(이동 어려움 우선)으로 안내를 고르고 하윤 녹음 11개를 재생합니다. 안전 안내 뒤 보험사 연결을 묻고, 사용자가 승낙했을 때만 전화를 겁니다(위험 신호가 있으면 묻지 않음)."),
        Triple(Stage.RECORD, "RecordStore", "출처·근거 장면 번호·사용자 알림·안내·보험사 연결 결과·모르는 항목을 사용자가 선택할 때만 저장합니다. 영상 원본은 포함하지 않습니다. 저장된 기록: ${s.records.size}건"),
    )
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Btn("설정으로", { vm.go(Screen.SETTINGS) })
            T("앱 구조 · 심사·개발용", size = 18.sp, weight = FontWeight.ExtraBold)
        }
        rows.forEach { (stage, name, body) ->
            Panel {
                CardHeader(Icons.Rounded.AccountTree, stage.label, "${stage.ordinal + 1}단계")
                T(name, size = 13.sp, color = p.blue, weight = FontWeight.SemiBold)
                T(body, size = 14.sp)
            }
        }
        Panel {
            CardHeader(Icons.Rounded.DirectionsCar, "현대차 전용 연결부", "교체용 어댑터")
            AdapterRegistry.oemAdapters().forEach { a ->
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Badge(if (a.connected) "연결됨" else "연결 안 됨", if (a.connected) Tone.OK else Tone.BAD)
                    Column { T("${a.role} ← ${a.name}", size = 14.sp, weight = FontWeight.SemiBold); T(a.note, size = 12.sp, color = p.sub) }
                }
            }
            T("Pleos Connect 차량 데이터·빌트인 캠·SOS 권한과 규격이 확보되면 위 클래스를 실제 구현으로 바꿉니다.", size = 13.sp, color = p.sub)
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun provenanceLabel(code: String) = FrameProvenance.entries.firstOrNull { it.code == code }?.label ?: code

private fun insuranceLabel(code: String) = when (code) {
    "demo_simulated" -> "체험 연결(실제 전화 안 함)"
    "declined" -> "나중에 하기로 함"
    "dialer_opened" -> "전화 화면 열림"
    "call_started" -> "전화 걸림"
    "failed" -> "전화 열기 실패"
    else -> "연결 안 함"
}

@Composable
fun RecordsScreen(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    var exporting by remember { mutableStateOf<IncidentRecord?>(null) }
    val creator = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        val r = exporting; if (uri != null && r != null) vm.exportRecord(r, uri); exporting = null
    }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        T("남긴 기록 ${s.records.size}건 · 영상 원본은 저장하지 않아요.", size = 14.sp, color = p.sub)
        if (s.records.isEmpty()) Panel { T("아직 남긴 기록이 없어요. 체험이나 실제 연결 뒤 '기록 남기기'를 누르세요.") }
        s.records.forEach { r ->
            val demo = r.mode == "synthetic_product_demo"
            Panel {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Badge(if (demo) "체험" else "실제", if (demo) Tone.WARN else Tone.OK)
                    T(localTime(r.createdAt), size = 13.sp, color = p.sub)
                }
                T(when {
                    r.incident != null -> "충격 감지 · 충격 ${"%.1f".format(r.incident.impactG)}g · 사고로 확정한 건 아니에요"
                    demo -> "사고가 아니라고 봐서 안내하지 않았어요 · ${r.scenario ?: "체험"}"
                    else -> "충격 감지 없이 직접 확인했어요"
                }, weight = FontWeight.SemiBold)
                val ai = when (r.analysisSource) { "local_vision_model" -> "AI 확인 결과"; "none" -> "AI 확인 안 함"; else -> "체험용 예시 결과" }
                T("장면: ${provenanceLabel(r.inputSource)} · $ai" + (r.analysisError?.let { " · 확인 실패: $it" } ?: ""), size = 13.sp)
                r.observation?.let { T("근거 장면: ${it.evidenceFrames.joinToString(", ") { i -> (i + 1).toString() }.ifEmpty { "없음" }}", size = 13.sp) }
                T("알려 주신 내용: " + listOfNotNull("연기·불꽃".takeIf { r.userReports.smoke }, "움직이기 어려움".takeIf { r.userReports.immobile }).joinToString(" · ").ifEmpty { "없음" }, size = 13.sp)
                if (r.guidanceText.isNotEmpty()) T("안내: ${r.guidanceText}", size = 13.sp)
                T("보험사 연결: ${insuranceLabel(r.insuranceCall)}", size = 13.sp)
                T("앱이 모르는 것: ${r.unknowns.joinToString(" · ")}", size = 13.sp, color = p.warn)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("파일로 저장", { exporting = r; creator.launch("멈춘다음_기록_${r.id}.json") })
                    Btn("지우기", { vm.deleteRecord(r.id) }, kind = BtnKind.DANGER)
                }
            }
        }
        Spacer(Modifier.height(16.dp))
    }
}

@Composable
fun SettingsScreen(vm: AppViewModel, s: UiState) {
    val p = LocalPalette.current
    val ctx = LocalContext.current
    var url by rememberSaveable { mutableStateOf(s.server.baseUrl) }
    var token by rememberSaveable { mutableStateOf(s.server.token) }
    var model by rememberSaveable { mutableStateOf(s.server.model) }
    var company by rememberSaveable { mutableStateOf(s.insurance.company) }
    var phone by rememberSaveable { mutableStateOf(s.insurance.phone) }
    var policy by rememberSaveable { mutableStateOf(s.insurance.policyNo) }
    var car by rememberSaveable { mutableStateOf(s.insurance.carNo) }
    var ask by rememberSaveable { mutableStateOf(s.insurance.askAfterIncident) }
    var direct by rememberSaveable { mutableStateOf(s.insurance.voiceDirectCall) }
    fun profile() = InsuranceProfile(company, phone, policy, car, ask, direct)
    // 바로 걸기를 켜면 마이크·전화 권한을 함께 요청한다. 거절하면 전화 화면만 여는 방식으로 남는다.
    val perms = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { r ->
        val callOk = r[Manifest.permission.CALL_PHONE] == true || ContextCompat.checkSelfPermission(ctx, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED
        direct = callOk; vm.updateInsurance(profile())
    }
    val micPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Panel {
            CardHeader(Icons.Rounded.SupportAgent, "내 보험 정보", if (s.insurance.ready) "준비됨" else "입력 전")
            T("보험증권이나 보험사 앱에 적힌 '긴급출동' 번호를 넣어 주세요. 이 정보는 이 기기에만 저장돼요.", size = 13.sp, color = p.sub)
            OutlinedTextField(company, { company = it }, Modifier.fillMaxWidth(), label = { Text("보험사 이름") }, singleLine = true)
            OutlinedTextField(phone, { phone = it }, Modifier.fillMaxWidth(), label = { Text("긴급출동 번호") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                isError = phone.isNotBlank() && !PhoneNumbers.isValid(phone), supportingText = { if (phone.isNotBlank() && !PhoneNumbers.isValid(phone)) Text("숫자 7~12자리로 넣어 주세요") })
            OutlinedTextField(policy, { policy = it }, Modifier.fillMaxWidth(), label = { Text("증권번호 (선택)") }, singleLine = true)
            OutlinedTextField(car, { car = it }, Modifier.fillMaxWidth(), label = { Text("차량번호 (선택)") }, singleLine = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(ask, { ask = it }); Spacer(Modifier.width(10.dp))
                T("사고 뒤 \"보험사에 연결할까요?\" 물어보기", Modifier.weight(1f), size = 15.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                Switch(direct, { on ->
                    if (on) perms.launch(arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.CALL_PHONE)) else direct = false
                }); Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    T("\"연결해 줘\"라고 하면 바로 전화 걸기", size = 15.sp)
                    T(if (direct) "대답을 들으면 바로 전화를 걸어요." else "꺼져 있으면 번호가 입력된 전화 화면만 열어요. 통화 버튼은 직접 눌러 주세요.", size = 12.sp, color = p.sub)
                }
            }
            if (!vm.voice.permitted) Btn("말로 대답하기 켜기 (마이크 허용)", { micPerm.launch(Manifest.permission.RECORD_AUDIO) })
            if (!vm.voice.available) T("이 기기에서는 음성 인식을 쓸 수 없어요. 버튼으로 대답하면 돼요.", size = 12.sp, color = p.warn)
            Btn("보험 정보 저장", { vm.updateInsurance(profile()) }, Modifier.fillMaxWidth(), BtnKind.PRIMARY, large = true)
            T("위험 신호(움직이기 어려움·연기)가 있으면 보험사보다 119·구조 요청을 먼저 안내하고, 보험사 연결은 묻지 않아요.", size = 12.sp, color = p.sub)
        }
        Panel {
            CardHeader(Icons.Rounded.RecordVoiceOver, "음성 안내")
            Row(verticalAlignment = Alignment.CenterVertically) { Switch(s.autoVoice, vm::setAutoVoice); Spacer(Modifier.width(10.dp)); T("하윤 목소리로 자동 안내") }
            T("사고 안내 ${vm.audio.clipCount}개 · 보험사 연결 대화 ${vm.audio.promptCount}개 준비됨", size = 13.sp, color = p.sub)
        }
        Panel {
            CardHeader(Icons.Rounded.Dns, "AI 서버 연결 · 고급")
            T("PC에서 AI 서버를 켠 뒤 주소를 넣어 주세요. 에뮬레이터는 http://10.0.2.2:8787, 실제 휴대폰은 http://<PC의 IP>:8787 (같은 Wi-Fi).", size = 13.sp, color = p.sub)
            OutlinedTextField(url, { url = it }, Modifier.fillMaxWidth(), label = { Text("서버 주소") }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri))
            OutlinedTextField(token, { token = it }, Modifier.fillMaxWidth(), label = { Text("접속 코드 (서버의 AFTERSTOP_TOKEN)") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
            OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text("AI 모델 이름 (비우면 서버 설정)") }, singleLine = true)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Btn("저장", { vm.updateServer(url, token, model) }, kind = BtnKind.PRIMARY)
                Btn("저장 후 연결 확인", { vm.updateServer(url, token, model); vm.checkHealth() })
            }
            s.urlMessage?.let { T(it, size = 13.sp, color = p.warn) }
            s.health?.let { T("서버 연결: ${if (it.reachable) "됨" else "안 됨"} · 모델: ${if (it.modelConfigured) it.modelName ?: "설정됨" else "설정 안 됨"} (${it.modelReady}) · ${it.message}", size = 13.sp) }
            T("접속 코드는 이 기기에만 저장하고 앱 파일에 넣지 않아요.", size = 12.sp, color = p.sub)
        }
        Panel {
            CardHeader(Icons.Rounded.Code, "개발자 정보")
            T("빌드 ${BuildConfig.VERSION_NAME} (${if (BuildConfig.DEBUG) "debug" else "release"}) · HTTP 허용 주소: 10.0.2.2, 127.0.0.1, localhost${if (BuildConfig.DEV_HOST.isNotBlank()) ", " + BuildConfig.DEV_HOST else ""}", size = 13.sp, color = p.sub)
            Btn("앱 구조 보기 · 심사·개발용", { vm.go(Screen.COMPONENTS) })
        }
        Spacer(Modifier.height(16.dp))
    }
}

private fun localTime(iso: String): String = runCatching {
    java.time.Instant.parse(iso).atZone(java.time.ZoneId.systemDefault()).format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))
}.getOrDefault(iso)

private fun displayName(ctx: android.content.Context, uri: Uri): String = runCatching {
    ctx.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c -> if (c.moveToFirst()) c.getString(0) else null }
}.getOrNull() ?: "고른 영상"
