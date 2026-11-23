package com.example.employee_management_app;

import android.content.Context;
import android.content.res.AssetFileDescriptor;
import android.graphics.Bitmap;

import org.tensorflow.lite.Interpreter;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

// Trich vector dac trung khuon mat (embedding) bang mo hinh FaceNet TFLite
// chay ngay tren dien thoai: input anh mat 160x160 -> output 128 so thuc
// da chuan hoa L2. Server chi nhan vector nay de so khop cosine, khong
// nhan anh goc (nhe + rieng tu). Dung chung cho ca dang ky lan cham cong
// nen 2 phia luon cung mot chuan tien xu ly.
public class FaceNetHelper {
    private static final String MODEL_FILE = "facenet.tflite";
    private static final int INPUT_SIZE = 160;
    private static final float IMAGE_MEAN = 127.5f;
    private static final float IMAGE_STD = 127.5f;

    private static Interpreter interpreter;
    private static int embeddingDim = 128;

    private static synchronized Interpreter getInterpreter(Context ctx) throws IOException {
        if (interpreter == null) {
            Interpreter.Options opts = new Interpreter.Options();
            opts.setNumThreads(4);
            interpreter = new Interpreter(loadModel(ctx), opts);
            int[] outShape = interpreter.getOutputTensor(0).shape(); // [1, 128]
            if (outShape.length == 2 && outShape[1] > 0) embeddingDim = outShape[1];
        }
        return interpreter;
    }

    private static MappedByteBuffer loadModel(Context ctx) throws IOException {
        AssetFileDescriptor afd = ctx.getAssets().openFd(MODEL_FILE);
        try (FileInputStream fis = new FileInputStream(afd.getFileDescriptor());
             FileChannel channel = fis.getChannel()) {
            return channel.map(FileChannel.MapMode.READ_ONLY, afd.getStartOffset(), afd.getDeclaredLength());
        }
    }

    // faceCrop: anh khuon mat da cat (bat ky kich thuoc). Tra embedding da
    // chuan hoa L2 (tong binh phuong = 1) de server so cosine truc tiep.
    public static float[] getEmbedding(Context ctx, Bitmap faceCrop) throws IOException {
        Interpreter itp = getInterpreter(ctx.getApplicationContext());
        Bitmap scaled = Bitmap.createScaledBitmap(faceCrop, INPUT_SIZE, INPUT_SIZE, true);
        ByteBuffer input = ByteBuffer.allocateDirect(INPUT_SIZE * INPUT_SIZE * 3 * 4);
        input.order(ByteOrder.nativeOrder());
        int[] pixels = new int[INPUT_SIZE * INPUT_SIZE];
        scaled.getPixels(pixels, 0, INPUT_SIZE, 0, 0, INPUT_SIZE, INPUT_SIZE);
        for (int px : pixels) {
            input.putFloat((((px >> 16) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            input.putFloat((((px >> 8) & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
            input.putFloat(((px & 0xFF) - IMAGE_MEAN) / IMAGE_STD);
        }
        input.rewind();
        float[][] output = new float[1][embeddingDim];
        itp.run(input, output);
        float[] emb = output[0];
        double norm = 0;
        for (float v : emb) norm += v * v;
        norm = Math.sqrt(norm);
        if (norm > 1e-9) {
            for (int i = 0; i < emb.length; i++) emb[i] = (float) (emb[i] / norm);
        }
        return emb;
    }
}
