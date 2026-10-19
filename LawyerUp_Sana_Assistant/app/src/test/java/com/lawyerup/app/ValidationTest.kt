package com.lawyerup.app
import com.lawyerup.app.validation.Validation
import org.junit.Assert.*
import org.junit.Test
class ValidationTest { @Test fun required(){assertNotNull(Validation.required("","عنوان")); assertNull(Validation.required("x","عنوان"))} @Test fun date(){assertNull(Validation.date("2024-01-02")); assertNotNull(Validation.date("x"))} }