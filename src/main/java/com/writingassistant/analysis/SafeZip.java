package com.writingassistant.analysis;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/** No files are written to disk; both central metadata and actual inflation are checked. */
final class SafeZip {
    static final int MAX_ENTRIES = 512;
    static final int MAX_ENTRY_BYTES = 4 * 1024 * 1024;
    static final int MAX_EXPANDED_BYTES = 20 * 1024 * 1024;
    static final int MAX_RATIO = 100;

    private SafeZip() { }

    static Map<String, byte[]> read(byte[] bytes) throws IOException {
        Map<String, Long> declared = directory(bytes);
        Map<String, byte[]> entries = new LinkedHashMap<>();
        long total = 0;
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry entry;
            byte[] buffer = new byte[8192];
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                validateName(name);
                if (entries.size() >= MAX_ENTRIES || entries.containsKey(name) || !declared.containsKey(name)) {
                    throw new IOException("ZIP contains excessive, duplicate or inconsistent entries");
                }
                ByteArrayOutputStream output = new ByteArrayOutputStream();
                int count;
                while ((count = zip.read(buffer)) != -1) {
                    total += count;
                    if (output.size() + count > MAX_ENTRY_BYTES || total > MAX_EXPANDED_BYTES) {
                        throw new IOException("ZIP expanded byte limit exceeded");
                    }
                    output.write(buffer, 0, count);
                }
                long compressed = entry.getCompressedSize();
                if (output.size() != declared.get(name) || output.size() > Math.max(1L, compressed) * MAX_RATIO) {
                    throw new IOException("ZIP size or compression ratio limit exceeded");
                }
                entries.put(name, output.toByteArray());
                zip.closeEntry();
            }
        } catch (IllegalArgumentException e) {
            throw new IOException("Invalid ZIP encoding", e);
        }
        if (entries.size() != declared.size()) throw new IOException("ZIP directory does not match its entries");
        return entries;
    }

    private static Map<String, Long> directory(byte[] bytes) throws IOException {
        ByteBuffer b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN);
        int end = -1;
        for (int i = bytes.length - 22; i >= Math.max(0, bytes.length - 65_557); i--) {
            if (b.getInt(i) == 0x06054b50 && i + 22 + u16(b, i + 20) == bytes.length) {
                end = i;
                break;
            }
        }
        if (end < 0 || u16(b, end + 4) != 0 || u16(b, end + 6) != 0
                || u16(b, end + 8) != u16(b, end + 10)) {
            throw new IOException("ZIP central directory is missing or multi-disk");
        }
        int count = u16(b, end + 10);
        long size = u32(b, end + 12);
        long offset = u32(b, end + 16);
        if (count == 0 || count > MAX_ENTRIES || offset + size != end || offset > end) {
            throw new IOException("ZIP entry count or directory bounds exceeded (ZIP64 is unsupported)");
        }
        int position = (int) offset;
        long total = 0;
        Map<String, Long> entries = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            if (position + 46 > end || b.getInt(position) != 0x02014b50) throw new IOException("Invalid ZIP directory");
            int flags = u16(b, position + 8);
            int method = u16(b, position + 10);
            long compressed = u32(b, position + 20);
            long expanded = u32(b, position + 24);
            int nameLength = u16(b, position + 28);
            int next = position + 46 + nameLength + u16(b, position + 30) + u16(b, position + 32);
            if (next > end || (flags & 1) != 0 || (method != 0 && method != 8)
                    || u16(b, position + 34) != 0 || u32(b, position + 42) >= offset) {
                throw new IOException("ZIP uses unsupported encryption, compression or entry bounds");
            }
            total += expanded;
            if (expanded > MAX_ENTRY_BYTES || total > MAX_EXPANDED_BYTES || expanded > Math.max(1, compressed) * MAX_RATIO) {
                throw new IOException("ZIP expanded size or compression ratio limit exceeded");
            }
            String name = new String(bytes, position + 46, nameLength, StandardCharsets.UTF_8);
            validateName(name);
            if (entries.putIfAbsent(name, expanded) != null) throw new IOException("Duplicate ZIP entry");
            position = next;
        }
        if (position != end) throw new IOException("ZIP directory length mismatch");
        return entries;
    }

    private static void validateName(String name) throws IOException {
        if (name.isEmpty() || name.startsWith("/") || name.contains("\\") || name.contains(":")
                || name.contains("%") || name.indexOf('\0') >= 0) throw new IOException("Unsafe ZIP entry name");
        for (String part : name.split("/")) {
            if (part.equals("..") || part.equals(".")) throw new IOException("ZIP path traversal is forbidden");
        }
    }

    private static int u16(ByteBuffer b, int position) { return Short.toUnsignedInt(b.getShort(position)); }
    private static long u32(ByteBuffer b, int position) { return Integer.toUnsignedLong(b.getInt(position)); }
}
