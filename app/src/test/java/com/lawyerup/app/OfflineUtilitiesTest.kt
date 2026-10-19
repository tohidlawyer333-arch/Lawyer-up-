package com.lawyerup.app

import com.lawyerup.app.data.AttachmentPolicy
import com.lawyerup.app.data.CmsPhraseInterpreter
import org.junit.Assert.*
import org.junit.Test

class OfflineUtilitiesTest {
    @Test fun attachmentPolicyRejectsOversize() = assertFalse(AttachmentPolicy.validate("scan.pdf", 2_000_001).accepted)
    @Test fun cmsRuleIsExplicitlyHeuristic() {
        val result = CmsPhraseInterpreter.explain("پرونده مقید به وقت نظارت است")
        assertNotNull(result); assertTrue(result!!.suggestedCheck.isNotBlank())
    }
}
