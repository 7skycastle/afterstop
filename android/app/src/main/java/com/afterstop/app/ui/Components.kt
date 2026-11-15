package com.afterstop.app.ui

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.material.icons.rounded.SupportAgent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.afterstop.app.core.AnalysisSource
import com.afterstop.app.core.Guides
import com.afterstop.app.core.Stage

@Composable
fun Panel(modifier: Modifier = Modifier, padding: Int = 18, content: @Composable ColumnScope.() -> Unit) {
    val p = LocalPalette.current
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier.shadow(14.dp, shape, ambientColor = p.shadow, spotColor = p.shadow).clip(shape).background(p.panel)
            .border(1.dp, p.line, shape).padding(padding.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
    )
}

/** K_study 카드 머리: 아이콘 원 + 굵은 제목 + 오른쪽 보조 문구 */
@Composable
fun CardHeader(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, trailing: String? = null) {
    val p = LocalPalette.current
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(36.dp).clip(CircleShape).background(p.blueSoft), contentAlignment = Alignment.Center) {
            androidx.compose.material3.Icon(icon, null, tint = p.blue, modifier = Modifier.size(20.dp))
        }
        T(title, Modifier.weight(1f), size = 17.sp, weight = FontWeight.ExtraBold)
        if (trailing != null) T(trailing, size = 12.sp, weight = FontWeight.Bold, color = p.sub)
    }
}

enum class BtnKind { PRIMARY, SECONDARY, DANGER }

@Composable
fun Btn(label: String, onClick: () -> Unit, modifier: Modifier = Modifier, kind: BtnKind = BtnKind.SECONDARY, enabled: Boolean = true, large: Boolean = false) {
    val p = LocalPalette.current
    val h = if (large) 64.dp else 52.dp
    val fs = if (large) 20.sp else 16.sp
    val shape = RoundedCornerShape(16.dp)
    when (kind) {
        BtnKind.PRIMARY -> Button(onClick, modifier.heightIn(min = h), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = p.blue, contentColor = p.onBlue, disabledContainerColor = p.line)) { T(label, size = fs, weight = FontWeight.ExtraBold, color = if (enabled) p.onBlue else p.sub) }
        BtnKind.DANGER -> Button(onClick, modifier.heightIn(min = h), enabled = enabled, shape = shape,
            colors = ButtonDefaults.buttonColors(containerColor = p.red, contentColor = Color.White, disabledContainerColor = p.line)) { T(label, size = fs, weight = FontWeight.ExtraBold, color = if (enabled) Color.White else p.sub) }
        BtnKind.SECONDARY -> OutlinedButton(onClick, modifier.heightIn(min = h), enabled = enabled, shape = shape,
            border = BorderStroke(1.dp, p.line), colors = ButtonDefaults.outlinedButtonColors(containerColor = p.soft)) { T(label, size = fs, weight = FontWeight.Bold, color = if (enabled) p.ink else p.sub) }
    }
}

/** 눌림 상태가 있는 큰 토글 버튼 (사용자 알림용). */
@Composable
fun ToggleBtn(label: String, on: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val p = LocalPalette.current
    OutlinedButton(
        onClick, modifier.heightIn(min = 56.dp).semantics { role = Role.Switch; stateDescription = if (on) "켜짐" else "꺼짐" },
        enabled = enabled, shape = RoundedCornerShape(14.dp),
        border = BorderStroke(if (on) 2.dp else 1.dp, if (on) p.warn else p.border),
        colors = ButtonDefaults.outlinedButtonColors(containerColor = if (on) p.warnBg else Color.Transparent),
    ) { T((if (on) "✓ " else "") + label, size = 16.sp, weight = FontWeight.SemiBold, color = if (on) p.warn else p.ink) }
}

@Composable
fun Badge(text: String, tone: Tone = Tone.NEUTRAL, modifier: Modifier = Modifier) {
    val p = LocalPalette.current
    val (bg, fg) = when (tone) {
        Tone.OK -> p.blueSoft to p.blue
        Tone.WARN -> p.warnBg to p.warn
        Tone.BAD -> p.redBg to p.red
        Tone.NEUTRAL -> p.soft to p.sub
    }
    Row(modifier.clip(RoundedCornerShape(50)).background(bg).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(fg)); Spacer(Modifier.width(6.dp))
        T(text, size = 13.sp, weight = FontWeight.SemiBold, color = fg)
    }
}

enum class Tone { OK, WARN, BAD, NEUTRAL }

@Composable
fun KeyValue(k: String, v: String, note: String? = null) {
    val p = LocalPalette.current
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(16.dp)).background(p.soft).border(1.dp, p.line, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp, vertical = 12.dp)) {
        T(k, size = 12.sp, weight = FontWeight.Bold, color = p.sub)
        T(v, size = 16.sp, weight = FontWeight.Bold)
        if (note != null) T(note, size = 12.sp, color = p.sub)
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PipelineBar(active: Stage?, done: Set<Stage> = emptySet()) {
    val p = LocalPalette.current
    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Stage.entries.forEach { s ->
            val isActive = s == active
            val isDone = active != null && s.ordinal < active.ordinal || s in done
            Box(
                Modifier.clip(RoundedCornerShape(10.dp)).background(if (isActive) p.blue else if (isDone) p.soft else p.panel)
                    .border(1.dp, if (isActive) p.blue else p.line, RoundedCornerShape(10.dp)).padding(horizontal = 10.dp, vertical = 8.dp),
            ) { T("${s.ordinal + 1} ${s.label}", size = 13.sp, weight = FontWeight.SemiBold, color = if (isActive) p.onBlue else if (isDone) p.blue else p.sub) }
        }
    }
}

@Composable
fun FrameStrip(frames: List<ShownFrame>, evidence: List<Int>) {
    val p = LocalPalette.current
    if (frames.isEmpty()) return
    Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        frames.forEach { f ->
            Column(Modifier.width(148.dp)) {
                Image(f.image, "사고 장면 ${f.index + 1}", Modifier.fillMaxWidth().height(84.dp).clip(RoundedCornerShape(8.dp))
                    .border(if (f.index in evidence) 3.dp else 1.dp, if (f.index in evidence) p.warn else p.line, RoundedCornerShape(8.dp)), contentScale = ContentScale.Crop)
                T("장면 ${f.index + 1} · ${whenLabel(f.offsetSeconds)}" + if (f.index in evidence) " · 근거" else "", size = 12.sp, weight = FontWeight.SemiBold)
                T(f.provenance.label, size = 11.sp, color = p.sub, maxLines = 2)
            }
        }
    }
}

/** 사건 기준 초 표기. 반올림으로 생기는 -0.0 을 0.0 으로 보인다. */
fun secs(v: Double): String = if (kotlin.math.abs(v) < 0.05) "0.0초" else "%+.1f초".format(v)

private val HINT = mapOf("observed" to "보여요", "possible" to "있는 것 같아요", "not_observed" to "안 보여요", "unknown" to "모르겠어요")

/** 장면 시각을 쉬운 말로: "사고 순간", "3초 전", "2초 후" */
fun whenLabel(v: Double): String {
    if (kotlin.math.abs(v) < 0.05) return "사고 순간"
    val a = kotlin.math.abs(v)
    val n = if (kotlin.math.abs(a - Math.round(a)) < 0.05) "%.0f".format(a) else "%.1f".format(a)
    return if (v < 0) "${n}초 전" else "${n}초 후"
}

/** 운전자 안내 화면: 한 번에 한 가지 할 일을 크게, 큰 버튼 몇 개만. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun GuidancePanel(s: UiState, vm: AppViewModel, modifier: Modifier = Modifier, demo: Boolean) {
    val p = LocalPalette.current
    Panel(modifier) {
        if (s.inCall) {
            T("119 도움 요청 · 체험", size = 26.sp, weight = FontWeight.Bold)
            T("체험 중이라 실제로 전화하지 않아요. 실제 사용에서는 119가 입력된 전화 화면이 열려요.", color = p.sub)
            T(Guides["help"].text, size = 18.sp, weight = FontWeight.SemiBold)
            Btn("체험 화면 닫기", vm::toggleCall, Modifier.fillMaxWidth(), BtnKind.DANGER, large = true)
            return@Panel
        }
        if (s.showFacts) {
            CallSheet(s)
            Btn("안내로 돌아가기", { vm.showFacts(false) }, Modifier.fillMaxWidth(), BtnKind.PRIMARY, large = true)
            return@Panel
        }
        val help = if (demo) "119 도움 요청 · 체험" else "119 도움 요청"
        if (!s.started) {
            Badge("안내 전")
            T(if (demo) "체험을 시작하면 충격을 감지한 순간 바로 안내가 시작돼요." else "충격을 감지하면 바로 안내를 시작해요. 지금 들으려면 아래 버튼을 누르세요.", size = 18.sp)
            Btn("안내 바로 듣기", vm::startCommonGuidance, Modifier.fillMaxWidth(), BtnKind.PRIMARY, large = true)
            Btn(help, vm::callEmergency, Modifier.fillMaxWidth(), BtnKind.DANGER)
            return@Panel
        }
        val key = vm.currentKey(s)
        val g = Guides[key]
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            T("지금 할 일 ${s.step + 1}/3", size = 14.sp, weight = FontWeight.Bold, color = p.blue)
            Badge(g.label, if (key in listOf("observed", "smoke", "immobile", "combined", "combinedObserved")) Tone.WARN else Tone.OK)
            if (s.paused) Badge("잠시 멈춤", Tone.NEUTRAL)
        }
        T(g.title, size = 30.sp, weight = FontWeight.Bold, lineHeight = 38.sp)
        T(g.text, size = 21.sp, weight = FontWeight.SemiBold, lineHeight = 30.sp)
        T(contextNote(s, key), size = 14.sp, color = p.sub)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(10.dp).clip(CircleShape).background(if (s.audioPlaying) p.blue else p.line)); Spacer(Modifier.width(8.dp))
            T(if (s.autoVoice) s.audioMessage else "음성 안내 꺼짐 · 화면 글로 안내해요", size = 13.sp, color = p.sub)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn("다시 듣기", vm::replay, enabled = !s.paused && vm.audio.hasClip(key))
            Btn(if (s.paused) "이어서 듣기" else "잠시 멈춤", vm::togglePause)
            Btn(if (s.step == 2) "처음부터" else "다음 안내", vm::nextGuide, kind = BtnKind.PRIMARY, enabled = !s.paused)
        }
        T("해당하면 눌러 주세요 · 안내가 바뀌어요", size = 13.sp, color = p.sub)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            ToggleBtn("움직이기 어려워요", s.guidance.immobile, vm::toggleImmobile)
            ToggleBtn("연기·불꽃이 보여요", s.guidance.smoke, vm::toggleSmoke)
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Btn(help, vm::callEmergency, kind = BtnKind.DANGER)
            Btn("통화할 때 읽을 내용", { vm.showFacts(true) })
        }
        InsuranceCard(s, vm, demo)
        SituationFacts(s)
    }
}

/** 보험사 연결: 묻고 → 말이나 버튼으로 대답 → 승낙했을 때만 연결. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun InsuranceCard(s: UiState, vm: AppViewModel, demo: Boolean) {
    val p = LocalPalette.current
    val mic = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok -> if (ok) vm.startListening() }
    val name = s.insurance.company.ifBlank { if (demo) "예시 보험사" else "보험사" }
    val shape = RoundedCornerShape(20.dp)
    Column(
        Modifier.fillMaxWidth().clip(shape).background(p.blueSoft).border(1.dp, p.line, shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            androidx.compose.material3.Icon(androidx.compose.material.icons.Icons.Rounded.SupportAgent, null, tint = p.blue, modifier = Modifier.size(22.dp))
            T("보험사 연결", Modifier.weight(1f), size = 17.sp, weight = FontWeight.ExtraBold)
            if (demo) Badge("체험", Tone.WARN)
        }
        when (s.insStage) {
            InsStage.ASKING, InsStage.LISTENING -> {
                T("${name}에 연결할까요?", size = 24.sp, weight = FontWeight.Black, lineHeight = 32.sp)
                if (s.insMessage.isNotBlank()) T(s.insMessage, size = 14.sp, color = p.body)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Btn("연결해 줘", vm::connectInsurance, kind = BtnKind.PRIMARY, large = true)
                    Btn("나중에", vm::declineInsurance, large = true)
                    Btn(if (s.insStage == InsStage.LISTENING) "듣는 중…" else "말로 대답하기", {
                        if (vm.voice.permitted) vm.startListening() else mic.launch(android.Manifest.permission.RECORD_AUDIO)
                    }, enabled = s.insStage != InsStage.LISTENING)
                }
            }
            InsStage.CONNECTED -> {
                T(s.insMessage, size = 16.sp, weight = FontWeight.SemiBold)
                s.insHeard?.let { T("들은 말: \"$it\"", size = 13.sp, color = p.sub) }
                Btn("통화할 때 읽을 내용", { vm.showFacts(true) }, Modifier.fillMaxWidth(), BtnKind.PRIMARY)
            }
            InsStage.DECLINED -> {
                T(s.insMessage, size = 15.sp, color = p.body)
                Btn("지금 ${name}에 연결", vm::offerInsurance, Modifier.fillMaxWidth())
            }
            InsStage.IDLE -> {
                T(s.insMessage.ifBlank {
                    if (demo || s.insurance.ready) "안전을 확인한 뒤, 한 번 묻고 ${name}에 연결해 드려요." else "보험 정보를 넣어 두면 사고 뒤 말 한마디로 연결해 드려요."
                }, size = 14.sp, color = p.body)
                if (!demo && !s.insurance.ready) Btn("보험 정보 넣기", { vm.go(Screen.SETTINGS) }, Modifier.fillMaxWidth())
                else Btn("${name}에 연결", vm::offerInsurance, Modifier.fillMaxWidth())
            }
        }
    }
}

/** 119·보험사와 통화할 때 그대로 읽을 수 있게 정리한 내용. */
@Composable
fun CallSheet(s: UiState) {
    val unknown = s.guidance.lost || s.guidance.scenario == "unknown"
    T("통화할 때 읽어 주세요", size = 24.sp, weight = FontWeight.Bold)
    val time = s.incidentAt?.let { java.time.format.DateTimeFormatter.ofPattern("a h시 m분").withLocale(java.util.Locale.KOREAN).format(it.atZone(java.time.ZoneId.systemDefault())) }
    KeyValue("언제", time?.let { "$it 쯤" } ?: "시간 모름")
    KeyValue("어디서", if (unknown) "위치를 몰라요 · 주변 표지판이나 도로명을 알려 주세요" else s.demoScenario.place)
    val what = listOfNotNull(
        if (s.lastIncident != null) "충격 뒤 차가 멈췄어요" else null,
        "움직이기 어려워요".takeIf { s.guidance.immobile },
        "연기·불꽃이 보여요".takeIf { s.guidance.smoke },
        s.observation?.takeIf { it.inferredRisk }?.let { "영상에서 연기처럼 보이는 게 있어요(AI 추정)" },
    ).joinToString(" · ").ifEmpty { "눈앞의 상황을 그대로 말해 주세요" }
    KeyValue("무슨 일", what)
    val ins = s.insurance
    val mine = listOfNotNull(ins.carNo.takeIf { it.isNotBlank() }?.let { "차량번호 $it" }, ins.policyNo.takeIf { it.isNotBlank() }?.let { "증권번호 $it" }).joinToString(" · ")
    KeyValue("내 정보", mine.ifEmpty { "설정에서 차량번호·증권번호를 넣으면 여기에 보여요" })
    if (ins.ready) KeyValue("보험사", "${ins.company} · ${com.afterstop.app.insurance.PhoneNumbers.display(ins.phone)}")
    KeyValue("앱이 모르는 것", "다친 정도 · 과실 · 움직여도 안전한지")
}

@Composable
private fun SituationFacts(s: UiState) {
    val unknown = s.guidance.lost || s.guidance.scenario == "unknown"
    val place = when {
        s.analysisSource == AnalysisSource.LOCAL_VISION_MODEL && s.observation?.scene == "tunnel" -> "터널 안으로 보여요 · 정확한 위치는 몰라요"
        unknown -> "위치를 몰라요"
        else -> s.demoScenario.place
    }
    KeyValue("지금 상황", place, if (s.lastIncident != null) "충격 감지 · ${s.incidentOrigin ?: ""} · 사고로 확정한 건 아니에요" else null)
    KeyValue("영상에서 본 것", observedSummary(s), sourceNote(s))
    KeyValue("움직일 수 있나요", if (s.guidance.immobile) "움직이기 어렵다고 알려 주셨어요" else "앱은 알 수 없어요 · 직접 살펴 주세요")
}

fun observedSummary(s: UiState): String {
    val o = s.observation ?: return if (s.analysisBusy) "AI가 보는 중이에요 · 그동안 기본 안내를 이어가요" else if (s.analysisError != null) "AI가 확인하지 못했어요 · 판단을 미뤘어요" else "아직 확인하지 않았어요"
    return when {
        o.visibility == "unusable" || o.evidenceFrames.isEmpty() -> "화면이 잘 안 보여 판단을 미뤘어요"
        else -> "연기 ${HINT[o.smoke]} · 불꽃 ${HINT[o.fire]}" + if (!o.inferredRisk) " · 안 보인다고 안전한 건 아니에요" else ""
    }
}

private fun sourceNote(s: UiState): String? = when (s.analysisSource) {
    AnalysisSource.FIXTURE -> "체험용 예시 결과예요"
    AnalysisSource.LOCAL_VISION_MODEL -> "AI 추정이에요 · 확정이 아니에요"
    null -> if (s.analysisError != null) "결과를 지어내지 않아요" else null
}

private fun contextNote(s: UiState, key: String): String {
    val userKeys = setOf("smoke", "immobile", "combined")
    if (key in userKeys || key == "help" || key.startsWith("facts")) return Guides[key].note
    return when (s.analysisSource) {
        AnalysisSource.FIXTURE -> "체험용 예시 결과예요. 다친 정도나 움직여도 안전한지는 알 수 없어요."
        AnalysisSource.LOCAL_VISION_MODEL -> if (s.observation?.inferredRisk == true) "AI가 영상에서 위험해 보이는 것을 찾았어요. 다친 정도나 대피 방향은 판단하지 않아요."
        else "영상에 안 보이는 위험도 있을 수 있어요."
        null -> Guides[key].note
    }
}

@Composable
fun RowScope.Weighted(weight: Float, content: @Composable ColumnScope.() -> Unit) {
    Column(Modifier.weight(weight), verticalArrangement = Arrangement.spacedBy(12.dp), content = content)
}
