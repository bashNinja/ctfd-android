package io.github.bashninja.ctfd.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {
    @Test fun recognisesTextTheSiteHides() {
        assertTrue(isHidden("""<span style="font-size:0.1px">"""))
        assertTrue(isHidden("""<span style="font-size: 0">"""))
        assertTrue(isHidden("""<div style="display:none">"""))
        assertTrue(isHidden("""<p style="color:red; visibility: hidden">"""))
        assertTrue(isHidden("""<div hidden>"""))
        assertFalse(isHidden("""<span style="font-size:14px;color:red">"""))
        assertFalse(isHidden("""<span style="font-weight:bold">"""))
    }

    @Test fun stripsHiddenElementsFromHtmlBlocks() {
        val html = """<div>Shown <span style="display:none">secret <b>bold</b></span>after</div>"""
        assertEquals("<div>Shown after</div>", stripHidden(html))
        assertEquals("<p>plain</p>", stripHidden("<p>plain</p>"))
    }
}
