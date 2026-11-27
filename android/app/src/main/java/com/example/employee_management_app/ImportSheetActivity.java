package com.example.employee_management_app;

import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListView;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Viewer + cell editor in Android itself; validates cells before applying or saving. */
public class ImportSheetActivity extends AppCompatActivity {
    public static final String EXTRA_URI="import_sheet_uri";
    public static final String EXTRA_NAME="import_sheet_name";
    public static final String EXTRA_EDIT="import_sheet_edit";
    public static final String EXTRA_BATCH_ID="import_sheet_batch_id";
    private static final Set<String> ALLOWED_HEADERS = new HashSet<>(Arrays.asList(
            "username", "full_name", "email", "phone", "department_id", "position_id", "hire_date"));

    private ImportSheetDocument sheet;
    private Uri uri;
    private boolean editable;
    private long batchId;
    private TextView info;
    private ArrayAdapter<String> adapter;

    private static final class ValidationIssue {
        final int rowIndex; // Data index 0 means actual Excel row 2; -1 for a header error.
        final int columnIndex;
        final String message;
        ValidationIssue(int rowIndex, int columnIndex, String message) {
            this.rowIndex = rowIndex;
            this.columnIndex = columnIndex;
            this.message = message;
        }
    }

    @Override protected void onCreate(Bundle b) {
        super.onCreate(b);
        String uriText = getIntent().getStringExtra(EXTRA_URI);
        String name = getIntent().getStringExtra(EXTRA_NAME);
        if (uriText == null || name == null) { finish(); return; }
        uri = Uri.parse(uriText);
        editable = getIntent().getBooleanExtra(EXTRA_EDIT, false);
        batchId = getIntent().getLongExtra(EXTRA_BATCH_ID, 0);
        setTitle((editable ? "Sửa: " : "Xem: ") + name);
        LinearLayout container = new LinearLayout(this);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setPadding(20, 16, 20, 16);
        info = new TextView(this); container.addView(info);
        ListView list = new ListView(this);
        adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, new ArrayList<>());
        list.setAdapter(adapter);
        list.setOnItemClickListener((parent, view, index, id) -> showRow(index));
        container.addView(list, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1));
        if (editable) {
            Button save = new Button(this); save.setText("LƯU THAY ĐỔI VÀO FILE");
            save.setOnClickListener(v -> saveFile()); container.addView(save);
        }
        setContentView(container);
        try (InputStream in = new java.io.ByteArrayInputStream(ImportFileStore.read(this, uri))) {
            sheet = ImportSheetDocument.open(in, name);
            info.setText("Số nhân viên: " + sheet.dataCount() + ". Chọn một dòng để "
                    + (editable ? "sửa dữ liệu." : "xem chi tiết."));
            refresh();
        } catch (Exception ex) {
            info.setText("Không mở được file: " + ex.getMessage());
        }
    }

    private void refresh() {
        adapter.clear();
        if (sheet == null) return;
        int nameIdx = columnIndex("full_name");
        int userIdx = columnIndex("username");
        for (int i = 0; i < sheet.dataCount(); i++) {
            List<String> row = sheet.row(i);
            String name = cell(row, nameIdx);
            String username = cell(row, userIdx);
            adapter.add((i + 1) + ". " + name + " (" + username + ")");
        }
        adapter.notifyDataSetChanged();
    }

    private void showRow(int index) {
        if (sheet == null) return;
        ScrollView scroll = new ScrollView(this);
        LinearLayout fields = new LinearLayout(this);
        fields.setPadding(24, 12, 24, 12);
        fields.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(fields);
        List<String> headers = sheet.headers(), vals = sheet.row(index);
        List<EditText> editors = new ArrayList<>();
        for (int c = 0; c < headers.size(); c++) {
            String header = normalizeHeader(headers.get(c));
            TextView label = new TextView(this);
            label.setText(headers.get(c)); label.setTextSize(14);
            fields.addView(label);
            EditText input = new EditText(this);
            input.setSingleLine(true);
            if ("email".equals(header)) {
                input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS);
            } else if ("phone".equals(header)) {
                input.setInputType(InputType.TYPE_CLASS_PHONE);
            } else if ("department_id".equals(header) || "position_id".equals(header)) {
                input.setInputType(InputType.TYPE_CLASS_NUMBER);
            }
            input.setText(cell(vals, c));
            // The batch sync API uses username as a stable identity. Editing it would
            // cause sync to be rejected; show it as read-only on imported batches.
            input.setEnabled(editable && !(batchId > 0 && "username".equals(header)));
            fields.addView(input);
            editors.add(input);
        }

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Dòng " + (index + 2)).setView(scroll).setNegativeButton("Đóng", null);
        if (editable) builder.setPositiveButton("Áp dụng và lưu", null);
        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(ignored -> {
            if (!editable) return;
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
                List<String> proposed = new ArrayList<>();
                for (EditText editor : editors) proposed.add(editor.getText().toString().trim());
                ValidationIssue issue = checkHeaders();
                if (issue == null) issue = validateRow(index, proposed);
                if (issue != null) {
                    String message = describe(issue);
                    info.setText(message);
                    if (issue.columnIndex >= 0 && issue.columnIndex < editors.size()) {
                        EditText field = editors.get(issue.columnIndex);
                        field.setError(issue.message);
                        field.requestFocus();
                    }
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show();
                    return; // Keep dialog open: user must correct the error.
                }
                try {
                    for (int c = 0; c < proposed.size(); c++) sheet.setValue(index, c, proposed.get(c));
                    refresh();
                    // Apply + persist in ONE action. Exiting the editor must never discard
                    // an apparently accepted edit without warning.
                    boolean changed = sheet.hasEdits();
                    if (!changed || persistChanges()) {
                        if (!changed) info.setText("Không có thay đổi cần lưu. MySQL không thay đổi.");
                        dialog.dismiss();
                    } else {
                        Toast.makeText(this, "Chưa lưu được file, hãy thử lại trước khi thoát", Toast.LENGTH_LONG).show();
                    }
                } catch (Exception ex) {
                    info.setText("Lỗi: " + ex.getMessage());
                    Toast.makeText(this, "Không áp dụng được: " + ex.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        });
        dialog.show();
    }

    private static String normalizeHeader(String header) {
        return header == null ? "" : header.trim().toLowerCase(Locale.ROOT);
    }

    private int columnIndex(String name) {
        if (sheet == null) return -1;
        List<String> headers = sheet.headers();
        for (int i = 0; i < headers.size(); i++) {
            if (name.equals(normalizeHeader(headers.get(i)))) return i;
        }
        return -1;
    }

    private static String cell(List<String> values, int index) {
        return index >= 0 && index < values.size() && values.get(index) != null
                ? values.get(index) : "";
    }

    private ValidationIssue checkHeaders() {
        if (sheet == null) return new ValidationIssue(-1, -1, "Chưa mở được file");
        Set<String> used = new HashSet<>();
        List<String> headers = sheet.headers();
        for (int c = 0; c < headers.size(); c++) {
            String h = normalizeHeader(headers.get(c));
            if (!ALLOWED_HEADERS.contains(h))
                return new ValidationIssue(-1, c, "Tên cột không được hỗ trợ: " + headers.get(c));
            if (!used.add(h)) return new ValidationIssue(-1, c, "Cột " + h + " bị trùng");
        }
        for (String required : new String[]{"username", "full_name", "email"}) {
            if (!used.contains(required)) return new ValidationIssue(-1, -1, "Thiếu cột bắt buộc: " + required);
        }
        return null;
    }

    private ValidationIssue validateRow(int rowIndex, List<String> values) {
        List<String> headers = sheet.headers();
        for (int c = 0; c < headers.size(); c++) {
            String header = normalizeHeader(headers.get(c));
            String value = cell(values, c).trim();
            String problem = validateValue(header, value);
            if (problem != null) return new ValidationIssue(rowIndex, c, problem);
        }
        for (String key : new String[]{"username", "email", "phone"}) {
            int c = columnIndex(key);
            String value = cell(values, c).trim();
            if (value.isEmpty()) continue; // Phone is optional.
            for (int other = 0; other < sheet.dataCount(); other++) {
                if (other == rowIndex) continue;
                String candidate = cell(sheet.row(other), c).trim();
                if (!candidate.isEmpty() && candidate.equalsIgnoreCase(value)) {
                    return new ValidationIssue(rowIndex, c,
                            "Trùng " + key + " với dòng " + (other + 2));
                }
            }
        }
        return null;
    }

    private String validateValue(String header, String value) {
        String filename = getIntent().getStringExtra(EXTRA_NAME);
        boolean xlsx = filename != null && filename.toLowerCase(Locale.ROOT).endsWith(".xlsx");
        return ImportSheetRules.validateValue(header, value, xlsx);
    }

    private ValidationIssue validateAllRows() {
        ValidationIssue issue = checkHeaders();
        if (issue != null) return issue;
        for (int i = 0; i < sheet.dataCount(); i++) {
            issue = validateRow(i, sheet.row(i));
            if (issue != null) return issue;
        }
        return null;
    }

    private String describe(ValidationIssue issue) {
        if (issue.rowIndex < 0) return "Tiêu đề: " + issue.message;
        String column = issue.columnIndex >= 0 && issue.columnIndex < sheet.headers().size()
                ? sheet.headers().get(issue.columnIndex) : "?";
        return "Dòng " + (issue.rowIndex + 2) + ", cột " + column + ": " + issue.message;
    }

    private void showSaveError(ValidationIssue issue) {
        String message = describe(issue);
        info.setText("Không thể lưu: " + message);
        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle("Dữ liệu không hợp lệ")
                .setMessage(message)
                .setNegativeButton("Đóng", null);
        if (issue.rowIndex >= 0) {
            builder.setPositiveButton("Sửa dòng " + (issue.rowIndex + 2),
                    (d, w) -> showRow(issue.rowIndex));
        }
        builder.show();
    }

    /** Verify that the document contains the same data after it is saved and opened again. */
    private static void requireSameSheet(ImportSheetDocument expected, ImportSheetDocument actual)
            throws java.io.IOException {
        if (!expected.headers().equals(actual.headers())
                || expected.dataCount() != actual.dataCount()) {
            throw new java.io.IOException("File lưu lại không khớp số dòng hoặc tiêu đề");
        }
        for (int i = 0; i < expected.dataCount(); i++) {
            if (!expected.row(i).equals(actual.row(i))) {
                throw new java.io.IOException("Dữ liệu dòng " + (i + 2)
                        + " sau khi lưu không giống dữ liệu vừa sửa");
            }
        }
    }

    private boolean persistChanges() {
        if (sheet == null) return false;
        ValidationIssue issue = validateAllRows();
        if (issue != null) { showSaveError(issue); return false; }
        try {
            String filename = getIntent().getStringExtra(EXTRA_NAME);
            byte[] output = sheet.save();
            // Verify XLSX/CSV serialization before touching the stored copy.
            ImportSheetDocument generated = ImportSheetDocument.open(
                    new java.io.ByteArrayInputStream(output), filename);
            requireSameSheet(sheet, generated);

            // Internal batch copies are saved atomically; legacy SAF files are written
            // through their persisted URI. Never write to a different chosen document.
            ImportFileStore.write(this, uri, output);
            ImportSheetDocument onDisk = ImportSheetDocument.open(
                    new java.io.ByteArrayInputStream(ImportFileStore.read(this, uri)), filename);
            requireSameSheet(sheet, onDisk);
            sheet = onDisk;
            refresh();
            info.setText("Đã lưu và đọc lại file thành công"
                    + (batchId > 0 ? " (đợt #" + batchId + ")" : "")
                    + ". MySQL chưa thay đổi; chọn Cập nhật MySQL để đồng bộ.");
            Toast.makeText(this, "Đã lưu thay đổi vào Excel/CSV", Toast.LENGTH_SHORT).show();
            return true;
        } catch (Exception ex) {
            String message = "Không xác nhận được file đã lưu: " + ex.getMessage()
                    + ". Chưa đồng bộ MySQL.";
            info.setText(message);
            new AlertDialog.Builder(this).setTitle("Lỗi lưu Excel/CSV")
                    .setMessage(message).setPositiveButton("Đóng", null).show();
            return false;
        }
    }

    private void saveFile() {
        if (sheet == null) return;
        if (!sheet.hasEdits()) { info.setText("Tất cả thay đổi đã được lưu; không còn dữ liệu chờ lưu."); return; }
        new AlertDialog.Builder(this).setTitle("Lưu file Excel/CSV?")
                .setMessage("Chỉ lưu dữ liệu trên thiết bị. MySQL chưa thay đổi.")
                .setNegativeButton("Hủy",null)
                .setPositiveButton("Lưu file",(d,w)->persistChanges()).show();
    }

    @Override public void onBackPressed() {
        if (sheet != null && sheet.hasEdits()) {
            new AlertDialog.Builder(this).setTitle("Còn thay đổi chưa lưu")
                    .setMessage("File chưa lưu thành công. Bạn muốn thử lưu lại trước khi thoát?")
                    .setNegativeButton("Ở lại",null)
                    .setNeutralButton("Thoát, bỏ thay đổi",(d,w)->ImportSheetActivity.super.onBackPressed())
                    .setPositiveButton("Lưu",(d,w)->{ if (persistChanges()) ImportSheetActivity.super.onBackPressed(); })
                    .show();
            return;
        }
        super.onBackPressed();
    }
}
