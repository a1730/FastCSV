package de.siegmar.fastcsv.reader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

/// [IndexedCsvReader] builds its index on byte level while pages are decoded and parsed on character
/// level. That only holds together for charsets that map every ASCII byte to exactly its own character,
/// so everything else has to be rejected up front.
class IndexedCsvReaderCharsetTest {

    @TempDir
    private Path tmpDir;

    /// The rejected charsets, by reason:
    ///
    ///   - UTF-16/UTF-32 encode ASCII as multiple bytes.
    ///   - Variable-width charsets encode bytes of multibyte characters within the ASCII range
    ///     (`0x40`–`0x7E`, GB18030 also `0x30`–`0x39`); the stateful ISO-2022-* family uses
    ///     `0x21`–`0x7E`, which even covers the *default* control characters.
    ///   - EUC-JP and x-IBM33722 are ASCII-transparent when encoding, but their *decoders* consume the
    ///     byte after any lead byte – including a line feed the scanner already counted.
    ///   - EUC-KR is rejected as collateral of the single-byte rule, not because it desynchronizes.
    ///   - IBM037 (EBCDIC) is single-byte but puts the comma at `0x6B` instead of `0x2C`.
    ///   - IBM864 is single-byte and encodes `%` to `0x25`, but decodes `0x25` to U+066A.
    ///   - x-JISAutoDetect cannot encode at all, so no byte value can be established for it.
    ///
    /// The file is plain ASCII throughout – the charset is rejected at construction, before any parsing.
    @ParameterizedTest
    @ValueSource(strings = {"UTF-16LE", "UTF-16BE", "UTF-32LE", "UTF-32BE",
        "Shift_JIS", "windows-31j", "Big5", "GBK", "GB18030", "EUC-JP", "EUC-KR",
        "ISO-2022-JP", "ISO-2022-KR", "x-IBM33722", "x-IBM949",
        "IBM037", "IBM864", "x-JISAutoDetect"})
    void rejectsUnsupportedCharsets(final String charsetName) throws IOException {
        final Charset charset = Charset.forName(charsetName);
        final Path file = writeFile("a,b\nc,d\n", StandardCharsets.US_ASCII);

        assertThatThrownBy(() -> IndexedCsvReader.builder().ofCsvRecord(file, charset))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining(charset.name());
    }

    @Test
    void rejectsBomDetectedUtf16Le() throws IOException {
        // FF FE BOM -> BomUtil detects UTF-16LE; build() with the default UTF-8 must still reject it.
        final Path file = writeBomFile(new byte[]{(byte) 0xFF, (byte) 0xFE},
            "rec1\nrec2\nrec3\nrec4\nrec5", StandardCharsets.UTF_16LE);

        assertThatThrownBy(() -> IndexedCsvReader.builder().ofCsvRecord(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UTF-16LE");
    }

    @Test
    void rejectsBomDetectedUtf16Be() throws IOException {
        // FE FF BOM -> BomUtil detects UTF-16BE; build() with the default UTF-8 must still reject it.
        final Path file = writeBomFile(new byte[]{(byte) 0xFE, (byte) 0xFF},
            "a,\"b\nc\"", StandardCharsets.UTF_16BE);

        assertThatThrownBy(() -> IndexedCsvReader.builder().ofCsvRecord(file))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("UTF-16BE");
    }

    /// Non-ASCII data must not disturb the index of an accepted charset: the multiline quoted field
    /// puts it right next to a record boundary, so a miscounted byte would show up as a lost record.
    @ParameterizedTest
    @ValueSource(strings = {"UTF-8", "ISO-8859-1"})
    void acceptsUtf8AndSingleByteCharsets(final String charsetName) throws IOException {
        final Charset charset = Charset.forName(charsetName);
        final Path file = writeFile("a,\"üö\nä\"\nb,c\n", charset);

        // One record per page, so reading page 1 has to seek to the byte offset the index recorded
        // for the record *after* the multiline quoted field.
        try (IndexedCsvReader<CsvRecord> csv = IndexedCsvReader.builder()
            .pageSize(1)
            .ofCsvRecord(file, charset)) {

            assertThat(csv.getIndex().recordCount()).isEqualTo(2);
            assertThat(csv.readPage(0)).singleElement()
                .extracting(CsvRecord::getFields).isEqualTo(List.of("a", "üö\nä"));
            assertThat(csv.readPage(1)).singleElement()
                .extracting(CsvRecord::getFields).isEqualTo(List.of("b", "c"));
        }
    }

    private Path writeFile(final String data, final Charset charset) throws IOException {
        return Files.writeString(tmpDir.resolve("test.csv"), data, charset);
    }

    private Path writeBomFile(final byte[] bom, final String data, final Charset charset) throws IOException {
        final var bytes = new ByteArrayOutputStream();
        bytes.writeBytes(bom);
        bytes.writeBytes(data.getBytes(charset));
        return Files.write(tmpDir.resolve("test-bom.csv"), bytes.toByteArray());
    }

}
