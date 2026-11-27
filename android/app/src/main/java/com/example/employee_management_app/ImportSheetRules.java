package com.example.employee_management_app;

import java.nio.charset.StandardCharsets;
import java.text.ParsePosition;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

/** Field-level validation shared by Android editor and local JVM tests.
 * PHP remains the final authority for existing database values and foreign keys.
 */
final class ImportSheetRules {
    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9.!#$%&'*+/=?^_`{|}~-]+@[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?"
                    + "(?:\\.[A-Za-z0-9](?:[A-Za-z0-9-]{0,61}[A-Za-z0-9])?)+$");
    private ImportSheetRules() {}

    static String validateValue(String header, String input, boolean xlsx) {
        String value = input == null ? "" : input.trim();
        if (value.length() > 500) return "Giá trị ô vượt quá 500 ký tự";
        switch (header) {
            case "username":
                if (!value.matches("[A-Za-z0-9_.-]{3,50}"))
                    return "Mã nhân viên phải có 3–50 ký tự (chữ, số, _, . hoặc -)";
                return null;
            case "full_name":
                if (value.isEmpty() || value.codePointCount(0, value.length()) > 100
                        || value.getBytes(StandardCharsets.UTF_8).length > 300
                        || value.matches("(?s).*\\p{Cc}.*"))
                    return "Họ tên bắt buộc, tối đa 100 ký tự";
                return null;
            case "email":
                if (value.length() > 100 || value.contains("..")
                        || value.startsWith(".") || !EMAIL.matcher(value).matches())
                    return "Email không đúng định dạng (ví dụ: a@example.com)";
                return null;
            case "phone":
                if (!value.isEmpty() && !value.matches("0[35789][0-9]{8}"))
                    return "Số di động Việt Nam phải có 10 số, bắt đầu 03/05/07/08/09";
                return null;
            case "department_id":
            case "position_id":
                if (!value.isEmpty()) {
                    if (!value.matches("[1-9][0-9]*")) return "Mã phải là số nguyên dương";
                    try { Integer.parseInt(value); }
                    catch (NumberFormatException e) { return "Mã vượt quá giới hạn số nguyên"; }
                }
                return null;
            case "hire_date":
                if (!value.isEmpty() && !validHireDate(value, xlsx))
                    return "Ngày vào làm phải hợp lệ (YYYY-MM-DD), không ở tương lai";
                return null;
            default:
                return "Cột không được hỗ trợ";
        }
    }

    static String validateDocument(ImportSheetDocument document, boolean xlsx) {
        java.util.List<String> headers = document.headers();
        java.util.Set<String> usedHeaders = new java.util.HashSet<>();
        java.util.Set<String> allowed = new java.util.HashSet<>(java.util.Arrays.asList(
                "username", "full_name", "email", "phone", "department_id", "position_id", "hire_date"));
        for (String original : headers) {
            String header = original.trim().toLowerCase(Locale.ROOT);
            if (!allowed.contains(header)) return "Tiêu đề: Cột không được hỗ trợ: " + original;
            if (!usedHeaders.add(header)) return "Tiêu đề: Cột bị trùng: " + header;
        }
        for (String required : new String[]{"username", "full_name", "email"}) {
            if (!usedHeaders.contains(required)) return "Tiêu đề: Thiếu cột bắt buộc: " + required;
        }
        java.util.Map<String, Integer> usernames = new java.util.HashMap<>();
        java.util.Map<String, Integer> emails = new java.util.HashMap<>();
        java.util.Map<String, Integer> phones = new java.util.HashMap<>();
        for (int r = 0; r < document.dataCount(); r++) {
            java.util.List<String> row = document.row(r);
            for (int c = 0; c < headers.size(); c++) {
                String header = headers.get(c).trim().toLowerCase(Locale.ROOT);
                String value = c < row.size() ? row.get(c).trim() : "";
                String error = validateValue(header, value, xlsx);
                if (error != null) return "Dòng " + (r+2) + ", cột " + header + ": " + error;
                java.util.Map<String, Integer> history =
                        "username".equals(header) ? usernames :
                        "email".equals(header) ? emails :
                        "phone".equals(header) ? phones : null;
                if (history == null || value.isEmpty()) continue;
                String key = "phone".equals(header) ? value : value.toLowerCase(Locale.ROOT);
                Integer before = history.putIfAbsent(key, r+2);
                if (before != null)
                    return "Dòng " + (r+2) + ", cột " + header
                            + ": Trùng với dòng " + before;
            }
        }
        return null;
    }

    private static boolean validHireDate(String value, boolean xlsx) {
        if (xlsx && value.matches("[0-9]{5}(?:\\.0+)?")) {
            Calendar date = Calendar.getInstance(TimeZone.getTimeZone("UTC"), Locale.ROOT);
            date.clear(); date.set(1899, Calendar.DECEMBER, 30);
            date.add(Calendar.DAY_OF_MONTH, Integer.parseInt(value.split("\\.")[0]));
            return date.getTimeInMillis() <= System.currentTimeMillis();
        }
        if (!value.matches("[0-9]{4}-[0-9]{2}-[0-9]{2}")) return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        format.setLenient(false);
        ParsePosition pos = new ParsePosition(0);
        Date parsed = format.parse(value, pos);
        return parsed != null && pos.getIndex() == value.length()
                && !parsed.after(new Date());
    }
}
