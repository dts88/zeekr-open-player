package io.github.dts88.openplayer;

final class ColorShader {
    private static final String UNIFORMS = ""
            + "uniform int uTransfer;\n"
            + "uniform int uOutput;\n"
            + "uniform float uSrcPeakPq;\n"
            + "uniform float uDstPeakPq;\n"
            + "uniform float uDstPeak;\n"
            + "uniform mat3 uGamut;\n"
            + "uniform float uDither;\n"
            + "uniform vec3 uLumaIn;\n"
            + "uniform int uSdrGamut;\n";

    private static final String CURVE_UNIFORMS = ""
            + "uniform int uCurve;\n"
            + "uniform float uSceneMax;\n"
            + "uniform vec2 uKnee;\n"
            + "uniform float uBezW[17];\n";

    private static final String BASICS = ""
            + "const float M1 = 0.1593017578125;\n"
            + "const float M2 = 78.84375;\n"
            + "const float C1 = 0.8359375;\n"
            + "const float C2 = 18.8515625;\n"
            + "const float C3 = 18.6875;\n"
            + "vec3 pqToLinear(vec3 code) {\n"
            + "  vec3 p = pow(max(code, 0.0), vec3(1.0 / M2));\n"
            + "  return pow(max(p - C1, 0.0) / (C2 - C3 * p), vec3(1.0 / M1));\n"
            + "}\n"
            + "vec3 linearToPq(vec3 lin) {\n"
            + "  vec3 p = pow(max(lin, 0.0), vec3(M1));\n"
            + "  return pow((C1 + C2 * p) / (1.0 + C3 * p), vec3(M2));\n"
            + "}\n"
            + "vec3 hlgToScene(vec3 code) {\n"
            + "  vec3 e = max(code, 0.0);\n"
            + "  vec3 low = e * e / 3.0;\n"
            + "  vec3 high = (exp((e - 0.55991073) / 0.17883277) + 0.28466892) / 12.0;\n"
            + "  return mix(low, high, step(0.5, e));\n"
            + "}\n"
            + "float eetf(float code) {\n"
            + "  if (uDstPeakPq >= uSrcPeakPq) return min(code, uDstPeakPq);\n"
            + "  float e1 = min(code / uSrcPeakPq, 1.0);\n"
            + "  float maxLum = uDstPeakPq / uSrcPeakPq;\n"
            + "  float knee = 1.5 * maxLum - 0.5;\n"
            + "  if (e1 < knee) return e1 * uSrcPeakPq;\n"
            + "  float t = (e1 - knee) / (1.0 - knee);\n"
            + "  float t2 = t * t;\n"
            + "  float t3 = t2 * t;\n"
            + "  float e2 = (2.0 * t3 - 3.0 * t2 + 1.0) * knee + (t3 - 2.0 * t2 + t) * (1.0 - knee)\n"
            + "      + (-2.0 * t3 + 3.0 * t2) * maxLum;\n"
            + "  return e2 * uSrcPeakPq;\n"
            + "}\n";

    private static final String COMPRESS_WITH_CURVE = ""
            + "float bezier16(float t) {\n"
            + "  float s = 1.0 - t;\n"
            + "  float acc;\n"
            + "  if (t <= 0.5) {\n"
            + "    float u = t / s;\n"
            + "    acc = uBezW[16];\n"
            + "    for (int i = 15; i >= 0; i--) acc = acc * u + uBezW[i];\n"
            + "    float s2 = s * s;\n"
            + "    float s4 = s2 * s2;\n"
            + "    float s8 = s4 * s4;\n"
            + "    return acc * s8 * s8;\n"
            + "  }\n"
            + "  float u = s / t;\n"
            + "  acc = uBezW[0];\n"
            + "  for (int i = 1; i <= 16; i++) acc = acc * u + uBezW[i];\n"
            + "  float t2 = t * t;\n"
            + "  float t4 = t2 * t2;\n"
            + "  float t8 = t4 * t4;\n"
            + "  return acc * t8 * t8;\n"
            + "}\n"
            + "float st2094(float m) {\n"
            + "  float v = min(m / uSceneMax, 1.0);\n"
            + "  float y;\n"
            + "  if (v <= uKnee.x && uKnee.x > 0.0) {\n"
            + "    y = v * uKnee.y / uKnee.x;\n"
            + "  } else {\n"
            + "    y = uKnee.y + (1.0 - uKnee.y) * bezier16((v - uKnee.x) / (1.0 - uKnee.x));\n"
            + "  }\n"
            + "  return y * uDstPeak;\n"
            + "}\n"
            + "float compress(float m) {\n"
            + "  if (uCurve == 2) return st2094(m);\n"
            + "  return pqToLinear(vec3(eetf(linearToPq(vec3(m)).x))).x;\n"
            + "}\n";

    private static final String COMPRESS_PLAIN = ""
            + "float compress(float m) {\n"
            + "  return pqToLinear(vec3(eetf(linearToPq(vec3(m)).x))).x;\n"
            + "}\n";

    private static final String FINISH = ""
            + "vec3 fitGamut(vec3 c, float y, float peak) {\n"
            + "  float lo = min(min(c.r, c.g), c.b);\n"
            + "  if (lo < 0.0) c = y + (c - y) * (y / (y - lo));\n"
            + "  float hi = max(max(c.r, c.g), c.b);\n"
            + "  if (hi > peak && hi > y) c = y + (c - y) * (max(peak - y, 0.0) / (hi - y));\n"
            + "  return pow(clamp(c / peak, 0.0, 1.0), vec3(1.0 / 2.2));\n"
            + "}\n"
            + "vec3 linearToSdr(vec3 lin) {\n"
            + "  float m = max(max(lin.r, lin.g), lin.b);\n"
            + "  if (m > 0.0) lin *= compress(m) / m;\n"
            + "  return fitGamut(uGamut * lin, dot(lin, uLumaIn), uDstPeak);\n"
            + "}\n"
            + "float ditherNoise(vec2 p) {\n"
            + "  return fract(52.9829189 * fract(dot(p, vec2(0.06711056, 0.00583715))));\n"
            + "}\n"
            + "vec3 finish(vec3 rgb, vec3 lin, bool haveLinear) {\n"
            + "  if (uTransfer == 0 && !haveLinear) {\n"
            + "    if (uSdrGamut == 0) return clamp(rgb, 0.0, 1.0);\n"
            + "    vec3 sl = pow(clamp(rgb, 0.0, 1.0), vec3(2.2));\n"
            + "    vec3 so = pow(clamp(uGamut * sl, 0.0, 1.0), vec3(1.0 / 2.2));\n"
            + "    return clamp(so + (ditherNoise(gl_FragCoord.xy) - 0.5) * uDither / 255.0, 0.0, 1.0);\n"
            + "  }\n"
            + "  if (!haveLinear) {\n"
            + "    if (uTransfer == 2) {\n"
            + "      vec3 s = hlgToScene(rgb);\n"
            + "      float ys = dot(s, vec3(0.2627, 0.6780, 0.0593));\n"
            + "      lin = s * (0.1 * pow(max(ys, 1e-6), 0.2));\n"
            + "    } else {\n"
            + "      if (uOutput == 1) return clamp(rgb, 0.0, 1.0);\n"
            + "      lin = pqToLinear(rgb);\n"
            + "    }\n"
            + "  }\n"
            + "  if (uOutput == 1) return linearToPq(lin);\n"
            + "  vec3 sdr = linearToSdr(lin);\n"
            + "  return clamp(sdr + (ditherNoise(gl_FragCoord.xy) - 0.5) * uDither / 255.0, 0.0, 1.0);\n"
            + "}\n";

    private ColorShader() {
    }

    private static String uniforms(boolean curve) {
        return curve ? UNIFORMS + CURVE_UNIFORMS : UNIFORMS;
    }

    private static String functions(boolean curve) {
        return BASICS + (curve ? COMPRESS_WITH_CURVE : COMPRESS_PLAIN) + FINISH;
    }

    static String oes(boolean curve) {
        return "#version 300 es\n"
                + "#extension GL_OES_EGL_image_external_essl3 : require\n"
                + "precision highp float;\n"
                + "precision highp int;\n"
                + "uniform samplerExternalOES uTex;\n"
                + uniforms(curve)
                + "in vec2 vTex;\n"
                + "out vec4 outColor;\n"
                + functions(curve)
                + "void main() {\n"
                + "  vec3 rgb = texture(uTex, vTex).rgb;\n"
                + "  outColor = vec4(finish(rgb, vec3(0.0), false), 1.0);\n"
                + "}\n";
    }

    static String yuv(String header, String dovi, boolean curve) {
        boolean withDovi = !dovi.isEmpty();
        return header
                + "precision highp float;\n"
                + "precision highp int;\n"
                + "uniform __samplerExternal2DY2YEXT uTex;\n"
                + "uniform mat3 uYuvToRgb;\n"
                + "uniform vec3 uYuvOffset;\n"
                + uniforms(curve)
                + "in vec2 vTex;\n"
                + "out vec4 outColor;\n"
                + functions(curve)
                + dovi
                + "void main() {\n"
                + "  vec3 yuv = texture(uTex, vTex).xyz;\n"
                + (withDovi
                ? "  if (uTransfer == 3) {\n"
                + "    outColor = vec4(finish(vec3(0.0), doviToLinearBt2020(doviReshape(yuv)), true), 1.0);\n"
                + "    return;\n"
                + "  }\n" : "")
                + "  vec3 rgb = uYuvToRgb * (yuv - uYuvOffset);\n"
                + "  outColor = vec4(finish(rgb, vec3(0.0), false), 1.0);\n"
                + "}\n";
    }
}
