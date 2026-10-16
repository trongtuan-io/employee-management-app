package com.example.employee_management_app;

import android.content.Context;
import android.net.Uri;
import android.provider.DocumentsContract;
import android.util.AtomicFile;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.IOException;
import java.util.Locale;

/** An independent, private Excel/CSV source for each successful import batch. */
final class ImportFileStore {
    private static final String FOLDER = "imported_spreadsheets";
    private static final int MAX_BYTES = 3 * 1024 * 1024;
    private ImportFileStore() {}

    static boolean isLocal(Uri uri) { return "file".equalsIgnoreCase(uri.getScheme()); }

    static File folder(Context context) throws IOException {
        File root = new File(context.getFilesDir(), FOLDER);
        if (!root.isDirectory() && !root.mkdirs()) throw new IOException("Không tạo được thư mục Excel nội bộ");
        return root;
    }

    static File checkedFile(Context context, Uri uri) throws IOException {
        if (!isLocal(uri)) throw new IOException("Không phải file nội bộ");
        File root = folder(context).getCanonicalFile();
        File file = new File(uri.getPath()).getCanonicalFile();
        if (!root.equals(file.getParentFile())) throw new IOException("Đường dẫn file không hợp lệ");
        return file;
    }

    static String visibleName(Context context, Uri uri) {
        if (isLocal(uri)) {
            try {
                String name = checkedFile(context, uri).getName();
                return name.replaceFirst("^batch_[0-9]+__", "");
            } catch (IOException ignored) { return "File không hợp lệ"; }
        }
        return null; // The caller obtains the title from OpenableColumns for legacy URIs.
    }

    static byte[] read(Context context, Uri uri) throws IOException {
        try (InputStream in = isLocal(uri)
                ? new java.io.FileInputStream(checkedFile(context, uri))
                : context.getContentResolver().openInputStream(uri)) {
            if (in == null) throw new IOException("Không mở được file Excel/CSV");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192]; int n;
            while ((n = in.read(buffer)) != -1) {
                if (bytes.size() + n > MAX_BYTES) throw new IOException("File lớn hơn 3 MB");
                bytes.write(buffer, 0, n);
            }
            if (bytes.size() == 0) throw new IOException("File rỗng");
            return bytes.toByteArray();
        }
    }

    /** Store the exact bytes that were uploaded, not a later revision of the selected file. */
    static Uri saveBatch(Context context, long batchId, String fileName, byte[] bytes) throws IOException {
        if (batchId <= 0) throw new IOException("ID đợt import không hợp lệ");
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("File không hợp lệ hoặc quá 3 MB");
        String name = fileName.replaceAll("[^\\p{L}\\p{N}._ -]", "_");
        if (name.length() > 80) {
            int dot = name.lastIndexOf('.');
            String ext = dot >= 0 ? name.substring(dot) : "";
            name = name.substring(0, Math.max(1, 80 - ext.length())) + ext;
        }
        if (!(name.toLowerCase(Locale.ROOT).endsWith(".xlsx")
                || name.toLowerCase(Locale.ROOT).endsWith(".csv")))
            throw new IOException("Chỉ lưu file .xlsx/.csv");
        File dest = new File(folder(context), "batch_" + batchId + "__" + name);
        if (dest.exists()) {
            // Retrying a previously interrupted SharedPreferences migration is safe
            // only if the existing snapshot is identical to the source bytes.
            if (java.util.Arrays.equals(read(context, Uri.fromFile(dest)), bytes))
                return Uri.fromFile(dest);
            throw new IOException("Đợt import đã có bản Excel khác, không ghi đè");
        }
        writeAtomic(dest, bytes);
        return Uri.fromFile(dest);
    }

    static void write(Context context, Uri uri, byte[] bytes) throws IOException {
        if (bytes.length == 0 || bytes.length > MAX_BYTES) throw new IOException("File không hợp lệ hoặc quá 3 MB");
        if (isLocal(uri)) {
            writeAtomic(checkedFile(context, uri), bytes);
        } else {
            // Legacy files selected with ACTION_OPEN_DOCUMENT are not owned by our app.
            try (OutputStream out = context.getContentResolver().openOutputStream(uri, "rwt")) {
                if (out == null) throw new IOException("File gốc không cho phép ghi");
                out.write(bytes);
                out.flush();
            }
        }
    }

    private static void writeAtomic(File target, byte[] bytes) throws IOException {
        AtomicFile atomic = new AtomicFile(target);
        FileOutputStream out = null;
        try {
            out = atomic.startWrite();
            out.write(bytes);
            atomic.finishWrite(out);
        } catch (IOException | RuntimeException ex) {
            if (out != null) atomic.failWrite(out);
            throw ex;
        }
    }

    static boolean delete(Context context, Uri uri) throws Exception {
        if (isLocal(uri)) {
            File file = checkedFile(context, uri);
            return !file.exists() || file.delete();
        }
        return DocumentsContract.deleteDocument(context.getContentResolver(), uri);
    }
}
