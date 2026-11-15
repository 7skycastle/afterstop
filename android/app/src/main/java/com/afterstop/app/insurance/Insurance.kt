package com.afterstop.app.insurance

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.net.Uri
import androidx.core.content.ContextCompat

/**
 * 사용자가 미리 입력한 보험 정보. 앱에는 보험사 번호를 넣어 두지 않는다(사용자가 증권·보험사 앱에서 확인해 입력).
 * 기기의 앱 개인 저장소에만 보관한다.
 */
data class InsuranceProfile(
    val company: String = "",
    val phone: String = "",
    val policyNo: String = "",
    val carNo: String = "",
    /** 사고 감지 후 "보험사에 연결할까요?" 를 묻는다 */
    val askAfterIncident: Boolean = true,
    /** 말로 "연결해 줘" 라고 하면 전화 앱을 거치지 않고 바로 건다 (전화 권한 필요) */
    val voiceDirectCall: Boolean = false,
) {
    val ready: Boolean get() = company.isNotBlank() && PhoneNumbers.isValid(phone)
}

object PhoneNumbers {
    fun digits(s: String) = s.filter { it.isDigit() }

    /** 보험사 대표·긴급출동 번호 형식(7~12자리)만 받는다. 112·119 같은 긴급 번호는 보험사 번호로 받지 않는다. */
    fun isValid(s: String): Boolean {
        if (s.isBlank() || !s.all { it.isDigit() || it == '-' || it == ' ' }) return false
        return digits(s).length in 7..12
    }

    fun display(s: String): String {
        val d = digits(s)
        return when {
            d.length == 8 && d.startsWith("1") -> "${d.take(4)}-${d.drop(4)}"
            d.length == 9 && d.startsWith("02") -> "02-${d.substring(2, 5)}-${d.drop(5)}"
            d.length == 10 && d.startsWith("02") -> "02-${d.substring(2, 6)}-${d.drop(6)}"
            d.length == 10 -> "${d.take(3)}-${d.substring(3, 6)}-${d.drop(6)}"
            d.length == 11 -> "${d.take(3)}-${d.substring(3, 7)}-${d.drop(7)}"
            else -> d
        }
    }
}

class InsuranceStore(private val prefs: SharedPreferences) {
    fun load() = InsuranceProfile(
        prefs.getString("ins.company", "") ?: "",
        prefs.getString("ins.phone", "") ?: "",
        prefs.getString("ins.policy", "") ?: "",
        prefs.getString("ins.car", "") ?: "",
        prefs.getBoolean("ins.ask", true),
        prefs.getBoolean("ins.direct", false),
    )

    fun save(p: InsuranceProfile) {
        prefs.edit().putString("ins.company", p.company).putString("ins.phone", p.phone).putString("ins.policy", p.policyNo)
            .putString("ins.car", p.carNo).putBoolean("ins.ask", p.askAfterIncident).putBoolean("ins.direct", p.voiceDirectCall).apply()
    }
}

/** 전화 연결. 바로 걸기는 사용자가 켜 두고 전화 권한을 허용했을 때만, 그 밖에는 번호가 입력된 전화 화면만 연다. */
object PhoneCaller {
    enum class Result { CALL_STARTED, DIALER_OPENED, FAILED }

    fun canCallDirectly(ctx: Context) = ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED

    fun call(ctx: Context, number: String, direct: Boolean): Result {
        val uri = Uri.parse("tel:" + PhoneNumbers.digits(number))
        val useCall = direct && canCallDirectly(ctx)
        val intent = Intent(if (useCall) Intent.ACTION_CALL else Intent.ACTION_DIAL, uri).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        return runCatching { ctx.startActivity(intent) }.fold({ if (useCall) Result.CALL_STARTED else Result.DIALER_OPENED }, { Result.FAILED })
    }

    /** 119 는 항상 전화 화면만 열고 통화 버튼은 사용자가 누른다. */
    fun dialEmergency(ctx: Context): Result =
        runCatching { ctx.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:119")).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .fold({ Result.DIALER_OPENED }, { Result.FAILED })
}
