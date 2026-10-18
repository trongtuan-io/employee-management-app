package com.example.employee_management_app;

import java.util.regex.Pattern;

/** Rules for edited profile fields; unchanged legacy contact data is not rewritten. */
public final class ProfileValidation {
    private ProfileValidation() { }

    private static final Pattern EMAIL = Pattern.compile(
            "[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[A-Za-z0-9!#$%&'*+/=?^_`{|}~-]+)*"
            + "@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
            + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+");

    public static String text(String value) { return value == null ? "" : value.trim(); }

    public static String display(String value) {
        return text(value).isEmpty() ? "Chưa cập nhật" : text(value);
    }

    public static boolean changed(String value, String original) {
        return !text(value).equals(text(original));
    }

    public static String nameError(String value) {
        String name = text(value);
        if (name.isEmpty()) return "Họ tên không được để trống";
        if (name.codePointCount(0, name.length()) > 100) return "Họ tên tối đa 100 ký tự";
        if (name.codePoints().anyMatch(Character::isISOControl)) return "Họ tên chứa ký tự không hợp lệ";
        return null;
    }

    public static String emailError(String value) {
        String email = text(value);
        if (email.isEmpty()) return "Email không được để trống";
        if (email.length() > 100) return "Email tối đa 100 ký tự";
        if (!EMAIL.matcher(email).matches() || email.indexOf('@') > 64) return "Email không hợp lệ";
        return null;
    }

    public static String hireDateError(String value) {
        String date = text(value);
        if (date.isEmpty()) return null;
        if (!date.matches("\\d{4}-\\d{2}-\\d{2}")) return "Ngày vào làm phải có dạng YYYY-MM-DD";
        try {
            java.text.SimpleDateFormat fmt = new java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.ROOT);
            fmt.setLenient(false);
            java.util.Date parsed = fmt.parse(date);
            if (!fmt.format(parsed).equals(date) || parsed.after(new java.util.Date())) return "Ngày không hợp lệ hoặc ở tương lai";
        } catch (java.text.ParseException ex) { return "Ngày không hợp lệ"; }
        return null;
    }

    public static String phoneError(String value) {
        String phone = text(value);
        // Nullable in the database. The leading '+' counts toward VARCHAR(15).
        if (phone.isEmpty()) return null;
        if (phone.length() > 15 || !phone.matches("\\+?[0-9]{7,15}")) {
            return "SĐT gồm 7–15 chữ số, có thể bắt đầu bằng +; tối đa 15 ký tự";
        }
        return null;
    }
}
