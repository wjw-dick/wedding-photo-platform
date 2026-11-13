package com.wedding.common.util;

import com.wedding.common.api.BizException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Set;

/**
 * 上传文件类型白名单：同时校验扩展名与文件头（魔数），防止改后缀伪装。
 * 仅放行业务需要的图片、视频、音频、PDF 与 Office/压缩包。
 */
public final class FileTypeValidator {

    private static final Set<String> IMAGE = Set.of("jpg", "jpeg", "png", "gif", "webp", "bmp");
    private static final Set<String> VIDEO = Set.of("mp4", "mov", "m4v");
    private static final Set<String> AUDIO = Set.of("mp3", "wav", "amr", "m4a");
    private static final Set<String> DOC = Set.of("pdf", "doc", "docx", "xls", "xlsx", "ppt", "pptx", "zip");

    private FileTypeValidator() {
    }

    public static void requireAllowed(String fileName, String contentType, InputStream in) {
        String ext = extensionOf(fileName);
        if (ext.isEmpty()) {
            throw new BizException(415, "文件缺少扩展名，已拒绝上传");
        }
        if (!(IMAGE.contains(ext) || VIDEO.contains(ext) || AUDIO.contains(ext) || DOC.contains(ext))) {
            throw new BizException(415, "不支持的文件类型：" + ext);
        }
        byte[] head;
        try {
            head = in.readNBytes(16);
        } catch (IOException e) {
            throw new BizException(415, "文件读取失败，无法校验类型");
        }
        if (!matches(ext, head, contentType)) {
            throw new BizException(415, "文件内容与扩展名不符，已拒绝上传");
        }
    }

    public static String extensionOf(String fileName) {
        if (fileName == null) return "";
        int slash = Math.max(fileName.lastIndexOf('/'), fileName.lastIndexOf('\\'));
        int dot = fileName.lastIndexOf('.');
        if (dot <= slash || dot == fileName.length() - 1) return "";
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean matches(String ext, byte[] h, String contentType) {
        return switch (ext) {
            case "jpg", "jpeg" -> startsWith(h, 0xFF, 0xD8, 0xFF);
            case "png" -> startsWith(h, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif" -> ascii(h, 0, 6).equals("GIF87a") || ascii(h, 0, 6).equals("GIF89a");
            case "webp" -> ascii(h, 0, 4).equals("RIFF") && ascii(h, 8, 4).equals("WEBP");
            case "bmp" -> startsWith(h, 0x42, 0x4D);
            case "mp4", "mov", "m4v" -> ascii(h, 4, 4).equals("ftyp");
            case "mp3" -> ascii(h, 0, 3).equals("ID3") || startsWith(h, 0xFF, 0xFB)
                    || startsWith(h, 0xFF, 0xF3) || startsWith(h, 0xFF, 0xF2);
            case "wav" -> ascii(h, 0, 4).equals("RIFF") && ascii(h, 8, 4).equals("WAVE");
            case "amr" -> ascii(h, 0, 6).equals("#!AMR\n");
            case "m4a" -> ascii(h, 4, 4).equals("ftyp");
            case "pdf" -> ascii(h, 0, 4).equals("%PDF");
            case "doc", "xls", "ppt", "zip" -> startsWith(h, 0x50, 0x4B, 0x03, 0x04)
                    || startsWith(h, 0x50, 0x4B, 0x05, 0x06)
                    || startsWith(h, 0xD0, 0xCF, 0x11, 0xE0, 0xA1, 0xB1, 0x1A, 0xE1);
            case "docx", "xlsx", "pptx" -> startsWith(h, 0x50, 0x4B, 0x03, 0x04)
                    || startsWith(h, 0x50, 0x4B, 0x05, 0x06);
            default -> false;
        };
    }

    private static boolean startsWith(byte[] h, int... sig) {
        if (h.length < sig.length) return false;
        for (int i = 0; i < sig.length; i++) {
            if ((h[i] & 0xFF) != sig[i]) return false;
        }
        return true;
    }

    private static String ascii(byte[] h, int offset, int len) {
        if (h.length < offset + len) return "";
        return new String(h, offset, len, StandardCharsets.US_ASCII);
    }
}
