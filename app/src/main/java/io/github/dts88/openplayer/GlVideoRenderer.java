package io.github.dts88.openplayer;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.view.Surface;

import io.github.dts88.openplayer.dovi.DoviShaderData;
import io.github.dts88.openplayer.dovi.ExtBlock;
import io.github.dts88.openplayer.dovi.Rpu;
import io.github.dts88.openplayer.hdr10plus.Hdr10PlusCurve;
import io.github.dts88.openplayer.hdr10plus.Hdr10PlusMeta;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Locale;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

final class GlVideoRenderer implements SurfaceTexture.OnFrameAvailableListener {
    enum Mode {
        OES("驱动转换"),
        YUV("原始 YUV");

        final String label;

        Mode(String label) {
            this.label = label;
        }
    }

    interface Listener {
        void onGlReady(GlVideoRenderer renderer, Surface input);

        void onGlError(GlVideoRenderer renderer, String what);
    }

    private static final int EGL_OPENGL_ES3_BIT_KHR = 0x40;

    private static final String VS_ES2 = ""
            + "attribute vec4 aPosition;\n"
            + "attribute vec2 aTexCoord;\n"
            + "uniform mat4 uTexMatrix;\n"
            + "uniform vec4 uRegion;\n"
            + "varying vec2 vTex;\n"
            + "void main() {\n"
            + "  gl_Position = aPosition;\n"
            + "  vec2 t = aTexCoord * uRegion.zw + uRegion.xy;\n"
            + "  vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;\n"
            + "}\n";
    private static final String FS_OES = ""
            + "#extension GL_OES_EGL_image_external : require\n"
            + "precision mediump float;\n"
            + "uniform samplerExternalOES uTex;\n"
            + "varying vec2 vTex;\n"
            + "void main() {\n"
            + "  gl_FragColor = vec4(texture2D(uTex, vTex).rgb, 1.0);\n"
            + "}\n";
    private static final String VS_ES3 = ""
            + "#version 300 es\n"
            + "in vec4 aPosition;\n"
            + "in vec2 aTexCoord;\n"
            + "uniform mat4 uTexMatrix;\n"
            + "uniform vec4 uRegion;\n"
            + "out vec2 vTex;\n"
            + "void main() {\n"
            + "  gl_Position = aPosition;\n"
            + "  vec2 t = aTexCoord * uRegion.zw + uRegion.xy;\n"
            + "  vTex = (uTexMatrix * vec4(t, 0.0, 1.0)).xy;\n"
            + "}\n";
    private static final String[] YUV_HEADERS = {
            "#version 300 es\n#extension GL_OES_EGL_image_external : require\n#extension GL_EXT_YUV_target : require\n",
            "#version 300 es\n#extension GL_OES_EGL_image_external_essl3 : require\n#extension GL_EXT_YUV_target : require\n",
            "#version 300 es\n#extension GL_EXT_YUV_target : require\n",
    };
    private static final String FS_YUV_BODY = ""
            + "precision highp float;\n"
            + "uniform __samplerExternal2DY2YEXT uTex;\n"
            + "uniform mat3 uYuvToRgb;\n"
            + "uniform vec3 uYuvOffset;\n"
            + "in vec2 vTex;\n"
            + "out vec4 outColor;\n"
            + "void main() {\n"
            + "  vec3 yuv = texture(uTex, vTex).xyz;\n"
            + "  vec3 rgb = uYuvToRgb * (yuv - uYuvOffset);\n"
            + "  outColor = vec4(clamp(rgb, 0.0, 1.0), 1.0);\n"
            + "}\n";
    private static final class Program {
        int id;
        int uTex;
        int uTexMatrix;
        int uRegion;
        int uYuvToRgb;
        int uYuvOffset;
        int uTransfer;
        int uOutput;
        int uSrcPeakPq;
        int uDstPeakPq;
        int uDstPeak;
        int uGamut;
        int uDither;
        int uLumaIn;
        int uSdrGamut;
        int uCurve;
        int uSceneMax;
        int uKnee;
        int uBezW;
        int uDvScale;
        int uDvLo;
        int uDvHi;
        int uDvPivots;
        int uDvCoef;
        int uDvNonlinear;
        int uDvOffset;
        int uDvLinear;
    }

    private static final int EGL_GL_COLORSPACE_KHR = 0x309D;
    private static final int EGL_GL_COLORSPACE_BT2020_PQ_EXT = 0x3340;
    private static final int EGL_SMPTE2086_MAX_LUMINANCE_EXT = 0x3349;
    private static final int EGL_SMPTE2086_MIN_LUMINANCE_EXT = 0x334A;
    private static final int EGL_CTA861_3_MAX_CONTENT_LIGHT_LEVEL_EXT = 0x3360;
    private static final int EGL_METADATA_SCALING = 50000;

    private final VideoFrameGate gate;
    private final DoviFrames dovi;
    private final Hdr10PlusFrames hdr10p;
    private final Handler main;
    private final Listener listener;
    private final HandlerThread thread = new HandlerThread("video-gl");
    private Handler gl;

    private EGLDisplay display = EGL14.EGL_NO_DISPLAY;
    private EGLContext context = EGL14.EGL_NO_CONTEXT;
    private EGLConfig config;
    private EGLSurface pbuffer = EGL14.EGL_NO_SURFACE;
    private EGLSurface window = EGL14.EGL_NO_SURFACE;
    private Surface windowSurface;
    private int windowWidth;
    private int windowHeight;
    private boolean presentationTime;

    private int textureId;
    private SurfaceTexture surfaceTexture;
    private Surface inputSurface;
    private Program oes;
    private Program yuv;
    private String yuvProblem = "";
    private String yuvHeader = "";
    private Program processOes;
    private Program processYuv;
    private String processProblem = "";
    private boolean processYuvDovi;
    private String doviProblem = "";
    private final DoviShaderData doviData = new DoviShaderData();
    private Rpu doviRpu;
    private float doviPeakPq = -1f;
    private Hdr10PlusMeta hdr10pMeta;
    private final Hdr10PlusCurve hdr10pCurve = new Hdr10PlusCurve();
    private boolean curveOk;
    private String curveProblem = "";
    private int hdr10pShown = -1;
    private VideoLook look = VideoLook.plain();
    private final boolean wantHdrSurface;
    private boolean hdrSurface;
    private boolean pqColorspace;
    private boolean hdrMetadata;
    private String hdrProblem = "";
    private final float[] texMatrix = new float[16];
    private final FloatBuffer positions = floats(-1, -1, 1, -1, -1, 1, 1, 1);
    private final FloatBuffer texCoords = floats(0, 0, 1, 0, 0, 1, 1, 1);

    private Mode mode;
    private YuvMatrix matrix = YuvMatrix.of(YuvMatrix.BT709, false, 8);
    private boolean hasFrame;
    private boolean failed;
    private boolean released;

    private volatile String lookText = "";

    GlVideoRenderer(VideoFrameGate gate, DoviFrames dovi, Hdr10PlusFrames hdr10p, Mode mode, boolean hdrOutput,
                    Handler main, Listener listener) {
        this.gate = gate;
        this.dovi = dovi;
        this.hdr10p = hdr10p;
        this.mode = mode;
        this.wantHdrSurface = hdrOutput;
        this.main = main;
        this.listener = listener;
    }

    void start() {
        thread.start();
        gl = new Handler(thread.getLooper());
        gl.post(this::init);
    }

    void setOutputSurface(Surface surface, int width, int height) {
        if (gl == null) return;
        if (surface != null) {
            gl.post(() -> attachWindow(surface, width, height));
            return;
        }
        CountDownLatch done = new CountDownLatch(1);
        gl.post(() -> {
            detachWindow();
            done.countDown();
        });
        await(done, 1000);
    }

    void setLook(VideoLook l) {
        if (gl == null) return;
        gl.post(() -> {
            if (l.doviProfile != look.doviProfile) {
                doviRpu = null;
                doviPeakPq = -1f;
            }
            look = l;
            matrix = YuvMatrix.of(l.standard, l.fullRange, l.bitDepth);
            if (hdr10pMeta != null) hdr10pCurve.set(hdr10pMeta, l.dstPeakNits);
            describeMode();
            if (hasFrame && !failed && window != EGL14.EGL_NO_SURFACE) {
                try {
                    drawAndSwap(0);
                } catch (Throwable t) {
                    fail(Tx.t("GPU 画不出这一帧", "The GPU could not draw this frame"), t);
                }
            }
        });
    }

    String lookText() {
        return lookText;
    }

    void resetScene() {
        if (gl == null) return;
        gl.post(() -> {
            doviRpu = null;
            doviPeakPq = -1f;
            clearHdr10Plus();
        });
    }
    void release() {
        if (gl == null) return;
        CountDownLatch done = new CountDownLatch(1);
        gl.post(() -> {
            releaseGl();
            done.countDown();
        });
        await(done, 1500);
        thread.quitSafely();
    }

    private static void await(CountDownLatch latch, long ms) {
        try {
            latch.await(ms, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private void init() {
        try {
            display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            if (display == EGL14.EGL_NO_DISPLAY) throw new IllegalStateException(Tx.t("拿不到 EGL 显示", "no EGL display"));
            int[] version = new int[2];
            if (!EGL14.eglInitialize(display, version, 0, version, 1)) throw eglProblem(Tx.t("EGL 初始化", "EGL initialisation"));
            String eglExtensions = " " + EGL14.eglQueryString(display, EGL14.EGL_EXTENSIONS) + " ";
            pqColorspace = eglExtensions.contains(" EGL_EXT_gl_colorspace_bt2020_pq ");
            hdrMetadata = eglExtensions.contains(" EGL_EXT_surface_SMPTE2086_metadata ");
            EGLConfig[] configs = new EGLConfig[1];
            int[] found = new int[1];
            if (wantHdrSurface) {
                if (!pqColorspace) {
                    hdrProblem = Tx.t("车机的 EGL 不支持 BT.2020 PQ 的出口", "EGL has no BT.2020 PQ surface");
                } else {
                    int[] deep = {
                            EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                            EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                            EGL14.EGL_RED_SIZE, 10, EGL14.EGL_GREEN_SIZE, 10, EGL14.EGL_BLUE_SIZE, 10,
                            EGL14.EGL_ALPHA_SIZE, 2, EGL14.EGL_NONE};
                    if (EGL14.eglChooseConfig(display, deep, 0, configs, 0, 1, found, 0) && found[0] > 0) {
                        config = configs[0];
                        hdrSurface = true;
                    } else {
                        hdrProblem = Tx.t("找不到 10 位的 EGL 配置", "no 10-bit EGL config");
                    }
                }
            }
            if (config == null) {
                int[] attributes = {
                        EGL14.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES3_BIT_KHR,
                        EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT | EGL14.EGL_PBUFFER_BIT,
                        EGL14.EGL_RED_SIZE, 8, EGL14.EGL_GREEN_SIZE, 8, EGL14.EGL_BLUE_SIZE, 8,
                        EGL14.EGL_ALPHA_SIZE, 8, EGL14.EGL_NONE};
                if (!EGL14.eglChooseConfig(display, attributes, 0, configs, 0, 1, found, 0) || found[0] == 0) {
                    throw eglProblem(Tx.t("找不到 ES3 的 EGL 配置", "Finding an ES3 EGL config"));
                }
                config = configs[0];
            }
            context = EGL14.eglCreateContext(display, config, EGL14.EGL_NO_CONTEXT,
                    new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION, 3, EGL14.EGL_NONE}, 0);
            if (context == null || context == EGL14.EGL_NO_CONTEXT) throw eglProblem(Tx.t("建 GL 上下文", "Creating the GL context"));
            pbuffer = EGL14.eglCreatePbufferSurface(display, config,
                    new int[]{EGL14.EGL_WIDTH, 1, EGL14.EGL_HEIGHT, 1, EGL14.EGL_NONE}, 0);
            if (pbuffer == null || pbuffer == EGL14.EGL_NO_SURFACE) throw eglProblem(Tx.t("建离屏缓冲", "Creating the off-screen surface"));
            if (!EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context)) throw eglProblem(Tx.t("启用 GL 上下文", "Activating the GL context"));

            presentationTime = eglExtensions.contains(" EGL_ANDROID_presentation_time ");
            String glExtensions = " " + GLES20.glGetString(GLES20.GL_EXTENSIONS) + " ";
            boolean yuvExtension = glExtensions.contains(" GL_EXT_YUV_target ");

            int[] ids = new int[1];
            GLES20.glGenTextures(1, ids, 0);
            textureId = ids[0];
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
            checkGl(Tx.t("建外部纹理", "creating the external texture"));

            oes = link(VS_ES2, FS_OES, Tx.t("驱动转换", "driver conversion"));
            if (yuvExtension) {
                buildYuvPrograms();
            } else {
                yuvProblem = Tx.t("显卡没报 GL_EXT_YUV_target", "GL_EXT_YUV_target not reported");
            }
            buildProcessPrograms();

            surfaceTexture = new SurfaceTexture(textureId);
            surfaceTexture.setOnFrameAvailableListener(this, gl);
            inputSurface = new Surface(surfaceTexture);
            describeMode();

            Surface input = inputSurface;
            main.post(() -> listener.onGlReady(this, input));
        } catch (Throwable t) {
            fail(Tx.t("GPU 画面通路起不来", "The GPU picture path could not start"), t);
        }
    }

    private void buildYuvPrograms() {
        StringBuilder problems = new StringBuilder();
        for (int i = 0; i < YUV_HEADERS.length && yuv == null; i++) {
            try {
                yuv = link(VS_ES3, YUV_HEADERS[i] + FS_YUV_BODY, Tx.t("原始 YUV", "raw YUV"));
                yuvHeader = YUV_HEADERS[i];
            } catch (RuntimeException e) {
                problems.append(Tx.t("写法 ", "variant ")).append(i + 1).append(Tx.t("：", ": ")).append(e.getMessage()).append(Tx.t("；", "; "));
                drainGlErrors();
            }
        }
        if (yuv == null) yuvProblem = Tx.t("着色器编不过：", " shader failed to compile: ") + problems;
    }

    private void buildProcessPrograms() {
        StringBuilder problems = new StringBuilder();
        try {
            processOes = link(VS_ES3, ColorShader.oes(true), Tx.t("画面处理（驱动转换）", "processing (driver conversion)"));
            curveOk = true;
        } catch (RuntimeException e) {
            curveProblem = e.getMessage() == null ? e.toString() : e.getMessage();
            drainGlErrors();
            try {
                processOes = link(VS_ES3, ColorShader.oes(false), Tx.t("画面处理（驱动转换）", "processing (driver conversion)"));
            } catch (RuntimeException e2) {
                problems.append(e2.getMessage());
                drainGlErrors();
            }
        }
        if (yuv != null) {
            try {
                processYuv = link(VS_ES3, ColorShader.yuv(yuvHeader, DoviShaderData.GLSL, curveOk), Tx.t("画面处理（原始 YUV）", "processing (raw YUV)"));
                processYuvDovi = true;
            } catch (RuntimeException e) {
                doviProblem = Tx.t("带杜比视界换算的着色器编不过：", "the Dolby Vision shader failed to compile: ") + e.getMessage();
                drainGlErrors();
                try {
                    processYuv = link(VS_ES3, ColorShader.yuv(yuvHeader, "", curveOk), Tx.t("画面处理（原始 YUV）", "processing (raw YUV)"));
                } catch (RuntimeException e2) {
                    problems.append(problems.length() > 0 ? Tx.t("；", "; ") : "").append(e2.getMessage());
                    drainGlErrors();
                }
            }
        } else {
            doviProblem = Tx.t("要直接读解码器的原始 YUV，这台机器读不了（", "raw YUV from the decoder is needed and cannot be read on this device (") + yuvProblem + Tx.t("）", ")");
        }
        processProblem = problems.toString();
    }

    private Program pick() {
        boolean wantYuv = mode == Mode.YUV;
        if (look.process) {
            if (wantYuv && processYuv != null) return processYuv;
            if (processOes != null) return processOes;
            if (processYuv != null) return processYuv;
        }
        return wantYuv && yuv != null ? yuv : oes;
    }

    private void attachWindow(Surface surface, int width, int height) {
        if (released || failed || context == EGL14.EGL_NO_CONTEXT) return;
        try {
            if (surface == windowSurface && window != EGL14.EGL_NO_SURFACE) {
                windowWidth = width;
                windowHeight = height;
            } else {
                detachWindow();
                if (hdrSurface) {
                    window = EGL14.eglCreateWindowSurface(display, config, surface,
                            new int[]{EGL_GL_COLORSPACE_KHR, EGL_GL_COLORSPACE_BT2020_PQ_EXT, EGL14.EGL_NONE}, 0);
                    if (window == null || window == EGL14.EGL_NO_SURFACE) {
                        hdrProblem = Tx.t("BT.2020 PQ 的出口建不起来，EGL 错误 0x", "BT.2020 PQ surface could not be created, EGL error 0x") + Integer.toHexString(EGL14.eglGetError());
                        hdrSurface = false;
                        window = EGL14.EGL_NO_SURFACE;
                    }
                }
                if (window == EGL14.EGL_NO_SURFACE) {
                    window = EGL14.eglCreateWindowSurface(display, config, surface, new int[]{EGL14.EGL_NONE}, 0);
                }
                if (window == null || window == EGL14.EGL_NO_SURFACE) {
                    window = EGL14.EGL_NO_SURFACE;
                    throw eglProblem(Tx.t("把屏上的画面区接到 GL", "Attaching the on-screen surface"));
                }
                if (!EGL14.eglMakeCurrent(display, window, window, context)) throw eglProblem(Tx.t("切到屏上的画面区", "Switching to the on-screen surface"));
                if (hdrSurface && hdrMetadata) {
                    int peak = Math.round(look.srcPeakNits) * EGL_METADATA_SCALING;
                    EGL14.eglSurfaceAttrib(display, window, EGL_SMPTE2086_MAX_LUMINANCE_EXT, peak);
                    EGL14.eglSurfaceAttrib(display, window, EGL_SMPTE2086_MIN_LUMINANCE_EXT, 0);
                    EGL14.eglSurfaceAttrib(display, window, EGL_CTA861_3_MAX_CONTENT_LIGHT_LEVEL_EXT, peak);
                    EGL14.eglGetError();
                }
                describeMode();
                windowSurface = surface;
                windowWidth = width;
                windowHeight = height;
            }
            if (hasFrame) drawAndSwap(0);
        } catch (Throwable t) {
            fail(Tx.t("GPU 画面通路接不上屏幕", "The GPU picture path could not attach to the screen"), t);
        }
    }

    private void detachWindow() {
        if (window == EGL14.EGL_NO_SURFACE) return;
        if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglMakeCurrent(display, pbuffer, pbuffer, context);
        EGL14.eglDestroySurface(display, window);
        window = EGL14.EGL_NO_SURFACE;
        windowSurface = null;
    }

    @Override
    public void onFrameAvailable(SurfaceTexture st) {
        if (released) return;
        long timestamp;
        try {
            st.updateTexImage();
            timestamp = st.getTimestamp();
            st.getTransformMatrix(texMatrix);
        } catch (RuntimeException e) {
            gate.onConsumed();
            fail(Tx.t("取不到解码器给的画面", "No frame from the decoder"), e);
            return;
        }
        gate.onConsumed();
        hasFrame = true;
        long timeUs = gate.presentationTimeFor(timestamp);
        if (look.doviProfile > 0 && dovi.enabled()) {
            Rpu rpu = timeUs != Long.MIN_VALUE ? dovi.find(timeUs) : null;
            if (rpu == null && doviRpu == null) rpu = dovi.latest();
            if (rpu != null && rpu != doviRpu) {
                boolean first = doviRpu == null;
                try {
                    useRpu(rpu);
                } catch (RuntimeException ignored) {
                }
                if (first) describeMode();
            }
        }
        if (hdr10p.enabled() && look.transfer == HdrMath.PQ) {
            Hdr10PlusMeta meta = timeUs != Long.MIN_VALUE ? hdr10p.find(timeUs) : null;
            if (meta == null && hdr10pMeta == null) meta = hdr10p.latest();
            if (meta != null && meta != hdr10pMeta) useHdr10Plus(meta);
        } else if (hdr10pMeta != null) {
            clearHdr10Plus();
            describeMode();
        }
        if (failed || window == EGL14.EGL_NO_SURFACE) return;
        try {
            drawAndSwap(timestamp);
        } catch (Throwable t) {
            fail(Tx.t("GPU 画不出这一帧", "The GPU could not draw this frame"), t);
        }
    }

    private void drawAndSwap(long presentationNs) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, windowWidth, windowHeight);
        draw(pick(), 0f, 0f, 1f, 1f);
        if (presentationTime && presentationNs > 0) {
            EGLExt.eglPresentationTimeANDROID(display, window, presentationNs);
        }
        if (!EGL14.eglSwapBuffers(display, window)) {
            int error = EGL14.eglGetError();
            if (error == EGL14.EGL_BAD_SURFACE || error == EGL14.EGL_BAD_NATIVE_WINDOW || error == EGL14.EGL_CONTEXT_LOST) {
                throw new IllegalStateException(Tx.t("提交画面失败，EGL 错误 0x", "presenting the frame failed, EGL error 0x") + Integer.toHexString(error));
            }
        }
    }

    private void draw(Program p, float x, float y, float w, float h) {
        GLES20.glUseProgram(p.id);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureId);
        GLES20.glUniform1i(p.uTex, 0);
        GLES20.glUniformMatrix4fv(p.uTexMatrix, 1, false, texMatrix, 0);
        GLES20.glUniform4f(p.uRegion, x, y, w, h);
        if (p.uYuvToRgb >= 0) GLES20.glUniformMatrix3fv(p.uYuvToRgb, 1, false, matrix.matrix, 0);
        if (p.uYuvOffset >= 0) GLES20.glUniform3f(p.uYuvOffset, matrix.offset[0], matrix.offset[1], matrix.offset[2]);
        if (p.uTransfer >= 0) {
            GLES20.glUniform1i(p.uTransfer, effectiveTransfer(p));
            GLES20.glUniform1i(p.uOutput, hdrSurface ? 1 : 0);
            GLES20.glUniform1f(p.uSrcPeakPq, srcPeakPq());
            GLES20.glUniform1f(p.uDstPeakPq, (float) HdrMath.nitsToPq(look.dstPeakNits));
            GLES20.glUniform1f(p.uDstPeak, look.dstPeakNits / 10000f);
            GLES20.glUniformMatrix3fv(p.uGamut, 1, false, look.gamut(), 0);
            GLES20.glUniform3fv(p.uLumaIn, 1, look.lumaIn(), 0);
            GLES20.glUniform1i(p.uSdrGamut, look.sdrGamut() && !hdrSurface ? 1 : 0);
            GLES20.glUniform1f(p.uDither, hdrSurface ? 0f : 1f);
        }
        if (p.uCurve >= 0) {
            boolean bezier = bezierActive() && effectiveTransfer(p) == HdrMath.PQ;
            GLES20.glUniform1i(p.uCurve, bezier ? Hdr10PlusCurve.BEZIER : Hdr10PlusCurve.NONE);
            if (bezier) {
                GLES20.glUniform1f(p.uSceneMax, hdr10pCurve.sceneMaxNits / 10000f);
                GLES20.glUniform2f(p.uKnee, hdr10pCurve.kneeX, hdr10pCurve.kneeY);
                GLES20.glUniform1fv(p.uBezW, Hdr10PlusCurve.DEGREE + 1, hdr10pCurve.weights, 0);
            }
        }
        if (p.uDvCoef >= 0 && doviRpu != null) uploadDovi(p);
        GLES20.glEnableVertexAttribArray(0);
        GLES20.glVertexAttribPointer(0, 2, GLES20.GL_FLOAT, false, 0, positions);
        GLES20.glEnableVertexAttribArray(1);
        GLES20.glVertexAttribPointer(1, 2, GLES20.GL_FLOAT, false, 0, texCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
    }

    private int effectiveTransfer(Program p) {
        if (look.transfer == HdrMath.DOVI && !(p == processYuv && doviReady())) return HdrMath.SDR;
        return look.transfer;
    }

    private boolean doviReady() {
        return processYuvDovi && doviRpu != null;
    }

    private void useRpu(Rpu rpu) {
        doviData.set(rpu, look.doviProfile);
        doviRpu = rpu;
        ExtBlock.Level1 l1 = rpu.level1();
        float target = -1f;
        if (l1 != null && l1.maxPq > 0) {
            target = l1.maxPq / 4095f;
        } else if (rpu.dm != null && rpu.dm.color != null && rpu.dm.color.sourceMaxPq > 0) {
            target = rpu.dm.color.sourceMaxPq / 4095f;
        }
        if (target > 0f) {
            target = Math.max(target, (float) HdrMath.nitsToPq(100));
            doviPeakPq = doviPeakPq < 0f || Math.abs(target - doviPeakPq) > 0.06f ? target
                    : doviPeakPq + (target - doviPeakPq) * 0.15f;
        }
    }

    private float srcPeakPq() {
        if (doviPeakActive()) return doviPeakPq;
        if (hdr10pActive()) return (float) HdrMath.nitsToPq(hdr10pCurve.sceneMaxNits);
        return (float) HdrMath.nitsToPq(look.srcPeakNits);
    }

    private boolean doviPeakActive() {
        return look.doviProfile > 0 && doviRpu != null && doviPeakPq > 0f;
    }

    private boolean hdr10pActive() {
        return look.transfer == HdrMath.PQ && hdr10pMeta != null && hdr10pCurve.mode != Hdr10PlusCurve.NONE
                && !doviPeakActive();
    }

    private boolean bezierActive() {
        return curveOk && hdr10pActive() && hdr10pCurve.mode == Hdr10PlusCurve.BEZIER;
    }

    private String hdr10PlusUse() {
        if (hdr10pCurve.mode == Hdr10PlusCurve.BEZIER && !curveOk) {
            return String.format(Locale.ROOT, Tx.t("本场景峰值 %.0f 尼特；片源曲线用不了，按峰值映射", "scene peak %.0f nits; source curve unavailable, mapped by peak"), hdr10pCurve.sceneMaxNits);
        }
        return hdr10pCurve.describe();
    }

    private void useHdr10Plus(Hdr10PlusMeta meta) {
        hdr10pMeta = meta;
        hdr10pCurve.set(meta, look.dstPeakNits);
        int shown = hdr10pCurve.mode * 100000 + Math.round(hdr10pCurve.sceneMaxNits);
        if (shown != hdr10pShown) {
            hdr10pShown = shown;
            describeMode();
        }
    }

    private void clearHdr10Plus() {
        hdr10pMeta = null;
        hdr10pShown = -1;
        hdr10pCurve.set(null, look.dstPeakNits);
    }

    private void uploadDovi(Program p) {
        DoviShaderData d = doviData;
        GLES20.glUniform1f(p.uDvScale, d.scale);
        GLES20.glUniform3fv(p.uDvLo, 1, d.lo, 0);
        GLES20.glUniform3fv(p.uDvHi, 1, d.hi, 0);
        GLES20.glUniform4fv(p.uDvPivots, DoviShaderData.PIVOT_VEC4, d.pivots, 0);
        GLES20.glUniform4fv(p.uDvCoef, DoviShaderData.COEF_VEC4, d.coef, 0);
        GLES20.glUniformMatrix3fv(p.uDvNonlinear, 1, false, d.nonlinear, 0);
        GLES20.glUniform3fv(p.uDvOffset, 1, d.offset, 0);
        GLES20.glUniformMatrix3fv(p.uDvLinear, 1, false, d.linear, 0);
    }

    private void describeMode() {
        Program p = pick();
        boolean processing = p.uTransfer >= 0;
        if (!processing) {
            lookText = look.sourceText() + Tx.t("，处理不了，原样显示", ", cannot be processed, shown as is");
            return;
        }
        String what = look.describe();
        String source = look.sourceText();
        if (hdr10pActive()) {
            source = (look.doviProfile > 0 ? Tx.t("杜比视界 P", "Dolby Vision P") + look.doviProfile + Tx.t(" 基础层，", " base layer, ") : "")
                    + Tx.t("HDR10+（", "HDR10+ (") + hdr10PlusUse() + Tx.t("）", ")");
            what = source + (hdrSurface ? Tx.t(" → HDR10 直出（动态元数据不随输出传递）", " → HDR10 output (dynamic metadata not passed on)")
                    : String.format(Locale.ROOT, Tx.t(" → 映射为 SDR（目标 %.0f 尼特）", " → mapped to SDR (target %.0f nits)"), look.dstPeakNits));
        }
        if (look.hdrOutput && !hdrSurface) {
            what = source + String.format(Locale.ROOT, Tx.t(" → 压成普通画面，按 %.0f 尼特的屏（HDR10 原样输出做不了：%s）", " → mapped to SDR for a %.0f-nit display (HDR10 output unavailable: %s)"),
                    look.dstPeakNits, hdrProblem);
        }
        if (look.transfer == HdrMath.DOVI && effectiveTransfer(p) != HdrMath.DOVI) {
            what = Tx.t("杜比视界 P5：颜色换算做不了，原样放（会偏绿偏紫）。原因：", "Dolby Vision P5: colours cannot be converted, shown as is (green and purple tint). Reason: ") + (p != processYuv || !processYuvDovi
                    ? (doviProblem.isEmpty() ? Tx.t("这一帧没走原始 YUV 的着色器", "this frame did not use the raw-YUV shader") : doviProblem)
                    : Tx.t("还没读到片子里的元数据", "no metadata read from the file yet"));
        } else if (look.transfer == HdrMath.DOVI) {
            what = Tx.t("杜比视界 P5：按片子里每一帧的元数据换算颜色", "Dolby Vision P5: colours converted from per-frame metadata") + (hdrSurface ? Tx.t(" → 按 HDR10 交给系统", " → handed to the system as HDR10")
                    : String.format(Locale.ROOT, Tx.t(" → 压成普通画面，按 %.0f 尼特的屏", " → mapped to SDR for a %.0f-nit display"), look.dstPeakNits));
        }
        lookText = what;
    }

    private void fail(String what, Throwable t) {
        if (failed) return;
        failed = true;
        drainGlErrors();
        String message = what + Tx.t("：", ": ") + (t.getMessage() != null ? t.getMessage() : t.toString());
        main.post(() -> listener.onGlError(this, message));
    }

    private void releaseGl() {
        if (released) return;
        released = true;
        try {
            if (surfaceTexture != null) surfaceTexture.setOnFrameAvailableListener(null);
            if (inputSurface != null) inputSurface.release();
            if (surfaceTexture != null) surfaceTexture.release();
            if (display != EGL14.EGL_NO_DISPLAY) {
                EGL14.eglMakeCurrent(display, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
                if (window != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, window);
                if (pbuffer != EGL14.EGL_NO_SURFACE) EGL14.eglDestroySurface(display, pbuffer);
                if (context != EGL14.EGL_NO_CONTEXT) EGL14.eglDestroyContext(display, context);
                EGL14.eglReleaseThread();
            }
        } catch (Throwable t) {
        }
        window = EGL14.EGL_NO_SURFACE;
        pbuffer = EGL14.EGL_NO_SURFACE;
        context = EGL14.EGL_NO_CONTEXT;
    }

    private static Program link(String vertex, String fragment, String what) {
        int vs = compile(GLES20.GL_VERTEX_SHADER, vertex, what + Tx.t(" 顶点", " vertex"));
        int fs = compile(GLES20.GL_FRAGMENT_SHADER, fragment, what + Tx.t(" 片元", " fragment"));
        int id = GLES20.glCreateProgram();
        GLES20.glAttachShader(id, vs);
        GLES20.glAttachShader(id, fs);
        GLES20.glBindAttribLocation(id, 0, "aPosition");
        GLES20.glBindAttribLocation(id, 1, "aTexCoord");
        GLES20.glLinkProgram(id);
        int[] ok = new int[1];
        GLES20.glGetProgramiv(id, GLES20.GL_LINK_STATUS, ok, 0);
        GLES20.glDeleteShader(vs);
        GLES20.glDeleteShader(fs);
        if (ok[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(id);
            GLES20.glDeleteProgram(id);
            throw new IllegalStateException(what + Tx.t(" 着色器连不上：", " shader failed to link: ") + oneLine(log));
        }
        Program p = new Program();
        p.id = id;
        p.uTex = GLES20.glGetUniformLocation(id, "uTex");
        p.uTexMatrix = GLES20.glGetUniformLocation(id, "uTexMatrix");
        p.uRegion = GLES20.glGetUniformLocation(id, "uRegion");
        p.uYuvToRgb = GLES20.glGetUniformLocation(id, "uYuvToRgb");
        p.uYuvOffset = GLES20.glGetUniformLocation(id, "uYuvOffset");
        p.uTransfer = GLES20.glGetUniformLocation(id, "uTransfer");
        p.uOutput = GLES20.glGetUniformLocation(id, "uOutput");
        p.uSrcPeakPq = GLES20.glGetUniformLocation(id, "uSrcPeakPq");
        p.uDstPeakPq = GLES20.glGetUniformLocation(id, "uDstPeakPq");
        p.uDstPeak = GLES20.glGetUniformLocation(id, "uDstPeak");
        p.uGamut = GLES20.glGetUniformLocation(id, "uGamut");
        p.uDither = GLES20.glGetUniformLocation(id, "uDither");
        p.uLumaIn = GLES20.glGetUniformLocation(id, "uLumaIn");
        p.uSdrGamut = GLES20.glGetUniformLocation(id, "uSdrGamut");
        p.uCurve = GLES20.glGetUniformLocation(id, "uCurve");
        p.uSceneMax = GLES20.glGetUniformLocation(id, "uSceneMax");
        p.uKnee = GLES20.glGetUniformLocation(id, "uKnee");
        p.uBezW = GLES20.glGetUniformLocation(id, "uBezW");
        p.uDvScale = GLES20.glGetUniformLocation(id, DoviShaderData.U_SCALE);
        p.uDvLo = GLES20.glGetUniformLocation(id, DoviShaderData.U_LO);
        p.uDvHi = GLES20.glGetUniformLocation(id, DoviShaderData.U_HI);
        p.uDvPivots = GLES20.glGetUniformLocation(id, DoviShaderData.U_PIVOTS);
        p.uDvCoef = GLES20.glGetUniformLocation(id, DoviShaderData.U_COEF);
        p.uDvNonlinear = GLES20.glGetUniformLocation(id, DoviShaderData.U_NONLINEAR);
        p.uDvOffset = GLES20.glGetUniformLocation(id, DoviShaderData.U_OFFSET);
        p.uDvLinear = GLES20.glGetUniformLocation(id, DoviShaderData.U_LINEAR);
        return p;
    }

    private static int compile(int type, String source, String what) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] ok = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, ok, 0);
        if (ok[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException(what + Tx.t("着色器编不过：", " shader failed to compile: ") + oneLine(log));
        }
        return shader;
    }

    private static String oneLine(String log) {
        if (log == null) return Tx.t("（驱动没给原因）", "(no reason given by the driver)");
        String s = log.trim().replace('\n', ' ');
        return s.length() > 300 ? s.substring(0, 300) + "…" : s;
    }

    private static void checkGl(String what) {
        int error = GLES20.glGetError();
        if (error == GLES20.GL_NO_ERROR) return;
        drainGlErrors();
        throw new IllegalStateException(what + Tx.t("时 GL 报错 0x", ": GL error 0x") + Integer.toHexString(error));
    }

    private static void drainGlErrors() {
        for (int i = 0; i < 16 && GLES20.glGetError() != GLES20.GL_NO_ERROR; i++) {
        }
    }

    private static IllegalStateException eglProblem(String what) {
        return new IllegalStateException(what + Tx.t("失败，EGL 错误 0x", " failed, EGL error 0x") + Integer.toHexString(EGL14.eglGetError()));
    }

    private static FloatBuffer floats(float... values) {
        FloatBuffer b = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        b.put(values).position(0);
        return b;
    }
}
