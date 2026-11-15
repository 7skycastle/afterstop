package com.afterstop.app

import com.afterstop.app.core.AudioManifest
import com.afterstop.app.core.Guides
import com.afterstop.app.core.Prompts
import com.afterstop.app.core.VoiceAnswer
import com.afterstop.app.core.VoiceAnswerParser
import com.afterstop.app.insurance.InsuranceProfile
import com.afterstop.app.insurance.PhoneNumbers
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.security.MessageDigest

class InsuranceTest {
    private val assets = File("src/main/assets")

    @Test fun yesAnswersConnect() {
        listOf("응", "네", "예 연결해 줘", "연결해줘", "응 걸어", "그래", "보험사 연결해 주세요", "네네 부탁해요", "오케이").forEach {
            assertEquals(it, VoiceAnswer.YES, VoiceAnswerParser.parse(listOf(it)))
        }
    }

    @Test fun noAnswersNeverConnect() {
        listOf("아니", "아니요", "나중에", "괜찮아요", "연결하지 마", "아니 연결 안 해", "취소", "됐어", "필요 없어").forEach {
            assertEquals(it, VoiceAnswer.NO, VoiceAnswerParser.parse(listOf(it)))
        }
    }

    @Test fun anyNoCandidateWinsOverYes() {
        // 인식 후보에 거절이 하나라도 있으면 걸지 않는다(잘못 거는 것보다 안전).
        assertEquals(VoiceAnswer.NO, VoiceAnswerParser.parse(listOf("응 연결해", "아니 연결해")))
    }

    @Test fun unclearAnswersAskAgain() {
        listOf("", "음", "뭐라고", "날씨 어때").forEach { assertEquals(it, VoiceAnswer.UNCLEAR, VoiceAnswerParser.parse(listOf(it))) }
        assertEquals(VoiceAnswer.UNCLEAR, VoiceAnswerParser.parse(emptyList()))
        // "응" 이 다른 낱말 속에 들어 있어도 승낙으로 보지 않는다.
        assertEquals(VoiceAnswer.UNCLEAR, VoiceAnswerParser.parse(listOf("대응 방법")))
    }

    @Test fun phoneNumberRules() {
        assertTrue(PhoneNumbers.isValid("1588-0000"))
        assertTrue(PhoneNumbers.isValid("02-123-4567"))
        assertTrue(PhoneNumbers.isValid("010 1234 5678"))
        assertFalse("긴급 번호는 보험사 번호로 받지 않음", PhoneNumbers.isValid("119"))
        assertFalse(PhoneNumbers.isValid("112"))
        assertFalse(PhoneNumbers.isValid("1588-00ab"))
        assertFalse(PhoneNumbers.isValid(""))
        assertEquals("1588-0000", PhoneNumbers.display("15880000"))
        assertEquals("02-123-4567", PhoneNumbers.display("021234567"))
        assertEquals("010-1234-5678", PhoneNumbers.display("01012345678"))
    }

    @Test fun profileReadyNeedsNameAndValidNumber() {
        assertFalse(InsuranceProfile().ready)
        assertFalse(InsuranceProfile(company = "보험사", phone = "119").ready)
        assertFalse(InsuranceProfile(company = "", phone = "1588-0000").ready)
        assertTrue(InsuranceProfile(company = "보험사", phone = "1588-0000").ready)
    }

    @Test fun promptManifestMatchesTextsAndFiles() {
        val clips = AudioManifest.parse(File(assets, "audio/prompts/manifest.json").readText())
        // 녹음은 선택 사항: 없으면 화면 글로만 묻는다. 있는 녹음은 모두 문구·파일·체크섬이 맞아야 한다.
        assertTrue("알 수 없는 키", clips.all { it.key in Prompts.ALL })
        for (c in clips) {
            assertEquals("문구 불일치: ${c.key}", Prompts[c.key].text, c.text)
            val f = File(assets, "audio/prompts/${c.file}")
            assertTrue("파일 없음: ${c.file}", f.isFile)
            val sha = MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }
            assertEquals("체크섬 불일치: ${c.file}", c.sha256, sha)
            assertEquals("하윤 Hayoon", c.voice)
        }
        assertEquals(clips.size, AudioManifest.playablePrompts(clips).size)
        // 보험 대화 녹음은 사고 안내 11개 매핑과 섞이지 않는다.
        assertTrue(AudioManifest.playable(clips).isEmpty())
        assertTrue(clips.none { it.key in Guides.ALL })
    }
}
