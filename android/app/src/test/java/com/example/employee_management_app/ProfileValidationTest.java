package com.example.employee_management_app;

import org.junit.Test;
import static org.junit.Assert.*;

public class ProfileValidationTest {
    @Test public void requiredNameAndDatabaseLengthAreEnforced() {
        assertNotNull(ProfileValidation.nameError(null));
        assertNotNull(ProfileValidation.nameError("   "));
        assertNull(ProfileValidation.nameError(" Nguyễn Thị Hải Yến "));
        assertNull(ProfileValidation.nameError("a".repeat(100)));
        assertNotNull(ProfileValidation.nameError("a".repeat(101)));
        assertNotNull(ProfileValidation.nameError("Nguyễn\nAn"));
    }

    @Test public void emailRequiresLocalPartAndDomainAndFitsDatabase() {
        for (String email : new String[]{null, "", "a", "a@", "@example.com", "a@localhost",
                "a..b@example.com", "a b@example.com", "a@-example.com", "a@example..com",
                "a".repeat(65) + "@example.com", "a@" + "b".repeat(64) + ".com"}) {
            assertNotNull(String.valueOf(email), ProfileValidation.emailError(email));
        }
        assertNull(ProfileValidation.emailError(" an.nguyen+hr@example.com "));
        assertNull(ProfileValidation.emailError("o'neil@example.vn"));
    }

    @Test public void optionalPhoneSupportsLocalAndInternationalFormats() {
        for (String phone : new String[]{null, "", "0901234567", "02412345678", "+84901234567",
                "1234567", "123456789012345", "+12345678901234"}) {
            assertNull(String.valueOf(phone), ProfileValidation.phoneError(phone));
        }
        for (String phone : new String[]{"123456", "1234567890123456", "+123456789012345",
                "090abc4567", "090 123 4567", "++84901234567", "０９０１２３４５６７"}) {
            assertNotNull(phone, ProfileValidation.phoneError(phone));
        }
    }

    @Test public void optionalHireDateMustBeValidAndNotInFuture() {
        assertNull(ProfileValidation.hireDateError(null));
        assertNull(ProfileValidation.hireDateError(""));
        assertNull(ProfileValidation.hireDateError("2025-08-11"));
        assertNotNull(ProfileValidation.hireDateError("2025-02-30"));
        assertNotNull(ProfileValidation.hireDateError("11/08/2025"));
        assertNotNull(ProfileValidation.hireDateError("2099-01-01"));
    }

    @Test public void legacyNullOrUnchangedContactDataIsNotMarkedForUpdate() {
        assertFalse(ProfileValidation.changed("", null));
        assertFalse(ProfileValidation.changed(" old invalid phone ", "old invalid phone"));
        assertTrue(ProfileValidation.changed("", "0901234567"));
        assertEquals("Chưa cập nhật", ProfileValidation.display(null));
        assertEquals("Chưa cập nhật", ProfileValidation.display(" "));
    }
}
