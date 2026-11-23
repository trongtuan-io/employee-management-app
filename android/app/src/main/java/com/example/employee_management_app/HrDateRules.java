package com.example.employee_management_app;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/** Date validation independent of Android UI. Server enforces the same rule. */
public final class HrDateRules {
    private HrDateRules() { }

    /** Returns true only when hire date is on/after the employee's 18th birthday. */
    public static boolean atLeast18AtHire(String birthDate, String hireDate) {
        if (birthDate == null || hireDate == null) return true; // Optional fields.
        if (!birthDate.matches("\\d{4}-\\d{2}-\\d{2}") || !hireDate.matches("\\d{4}-\\d{2}-\\d{2}"))
            return false;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        format.setLenient(false);
        try {
            Date dobValue = format.parse(birthDate);
            Date hiredValue = format.parse(hireDate);
            if (dobValue == null || hiredValue == null) return false;
            Calendar birth = Calendar.getInstance(Locale.US);
            Calendar hired = Calendar.getInstance(Locale.US);
            birth.setTime(dobValue);
            hired.setTime(hiredValue);
            int years = hired.get(Calendar.YEAR) - birth.get(Calendar.YEAR);
            if (years != 18) return years > 18;
            // Feb 29 cannot meet an 18th birthday on Feb 28 of a non-leap year.
            int birthMonthDay = birth.get(Calendar.MONTH) * 100 + birth.get(Calendar.DAY_OF_MONTH);
            int hiredMonthDay = hired.get(Calendar.MONTH) * 100 + hired.get(Calendar.DAY_OF_MONTH);
            return hiredMonthDay >= birthMonthDay;
        } catch (ParseException e) {
            return false;
        }
    }
}
