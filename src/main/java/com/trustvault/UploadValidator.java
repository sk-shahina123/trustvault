package com.trustvault;

import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

public final class UploadValidator {

    public static final int MAX_BYTES = 5 * 1024 * 1024;

    private UploadValidator() {
    }

    public record ValidatedUpload(
            String name,
            String category,
            String filename,
            String contentType,
            byte[] data) {
    }

    public static final class ValidationException extends RuntimeException {
        private final int status;

        public ValidationException(int status, String message) {
            super(message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    public static ValidatedUpload validate(
            String rawName,
            String rawCategory,
            String originalFilename,
            String declaredType,
            byte[] data) {

        if (data == null || data.length == 0) {
            throw invalid("Choose a non-empty file.");
        }

        if (data.length > MAX_BYTES) {
            throw new ValidationException(
                    413, "Maximum file size is 5 MiB.");
        }

        String name = requiredText(rawName, "Document name", 255);
        String category = requiredText(rawCategory, "Category", 100);
        String filename = requiredText(
                originalFilename, "Filename", 255);

        if (!filename.matches("[A-Za-z0-9][A-Za-z0-9._ ()-]{0,254}")
                || filename.contains("..")) {
            throw invalid(
                    "Use a simple filename, such as report.pdf or notes.txt. "
                    + "Use English letters, numbers, spaces, dots, "
                    + "underscores, hyphens, or parentheses.");
        }

        String lowerName = filename.toLowerCase(Locale.ROOT);
        String contentType;

        if (lowerName.endsWith(".txt")) {
            validateText(data);
            contentType = "text/plain";
        } else if (lowerName.endsWith(".pdf")) {
            validatePdfMarkers(data);
            contentType = "application/pdf";
        } else {
            throw invalid(
                    "Only PDF and UTF-8 text (.txt) files are supported.");
        }

        // An additional consistency check, not proof of file safety.
        if (declaredType != null && !declaredType.isBlank()) {
            String suppliedType = declaredType.split(";", 2)[0].strip();

            if (!suppliedType.equalsIgnoreCase(contentType)
                    && !suppliedType.equalsIgnoreCase(
                            "application/octet-stream")) {
                throw invalid(
                        "The file type does not match its extension.");
            }
        }

        return new ValidatedUpload(
                name, category, filename, contentType, data);
    }

    private static String requiredText(
            String raw, String label, int maxLength) {

        String value = raw == null ? "" : raw.strip();

        if (value.isEmpty()
                || value.length() > maxLength
                || value.codePoints().anyMatch(Character::isISOControl)) {
            throw invalid(
                    label + " must contain 1 to " + maxLength
                    + " characters, without control characters.");
        }

        return value;
    }

    private static void validateText(byte[] data) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(data))
                    .toString();

            if (text.codePoints().anyMatch(character ->
                    Character.isISOControl(character)
                    && character != '\n'
                    && character != '\r'
                    && character != '\t')) {
                throw invalid(
                        "The text file contains unsupported binary data.");
            }
        } catch (CharacterCodingException exception) {
            throw invalid("Save the text file using UTF-8 encoding.");
        }
    }

    private static void validatePdfMarkers(byte[] data) {
        String header = new String(
                data,
                0,
                Math.min(data.length, 8),
                StandardCharsets.US_ASCII);

        int tailStart = Math.max(0, data.length - 1024);

        String tail = new String(
                data,
                tailStart,
                data.length - tailStart,
                StandardCharsets.US_ASCII);

        // Basic format checks only.
        // These do not fully parse a PDF or detect malware.
        if (!header.matches("%PDF-(1\\.[0-7]|2\\.0)")
                || !tail.stripTrailing().endsWith("%%EOF")) {
            throw invalid(
                    "The file does not have the expected PDF format.");
        }
    }

    private static ValidationException invalid(String message) {
        return new ValidationException(400, message);
    }
}