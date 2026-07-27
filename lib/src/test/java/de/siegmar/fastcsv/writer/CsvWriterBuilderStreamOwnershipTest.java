package de.siegmar.fastcsv.writer;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.nio.charset.Charset;
import java.nio.charset.CharsetDecoder;
import java.nio.charset.CharsetEncoder;

import org.junit.jupiter.api.Test;

/// Streams the library opens itself (the `Path` based `build` methods) must not leak when writer
/// construction fails. Control character rules are checked before the file is opened, but the
/// encoder and the buffer are only built afterwards.
@SuppressWarnings("PMD.CloseResource")
class CsvWriterBuilderStreamOwnershipTest {

    @Test
    void closesStreamWhenWriterConstructionFails() {
        final TrackingOutputStream outputStream = new TrackingOutputStream();

        assertThatThrownBy(() -> CsvWriter.builder().buildOwning(outputStream, new BrokenCharset()))
            .isInstanceOf(AssertionError.class)
            .hasMessage("no encoder");

        assertThat(outputStream.closed).isTrue();
    }

    /// The counterpart contract: streams handed in by the caller stay the caller's to close.
    @Test
    void keepsCallerSuppliedStreamOpenWhenWriterConstructionFails() {
        final TrackingOutputStream outputStream = new TrackingOutputStream();

        assertThatThrownBy(() -> CsvWriter.builder().build(outputStream, new BrokenCharset()))
            .isInstanceOf(AssertionError.class);

        assertThat(outputStream.closed).isFalse();
    }

    @Test
    void keepsStreamOpenWhenWriterConstructionSucceeds() {
        final TrackingOutputStream outputStream = new TrackingOutputStream();

        assertThat(CsvWriter.builder().buildOwning(outputStream, UTF_8)).isNotNull();

        assertThat(outputStream.closed).isFalse();
    }

    private static final class TrackingOutputStream extends ByteArrayOutputStream {

        private boolean closed;

        @Override
        public void close() {
            closed = true;
        }

    }

    /// Fails while [java.io.OutputStreamWriter] is being constructed, after the file is already open.
    private static final class BrokenCharset extends Charset {

        BrokenCharset() {
            super("x-fastcsv-broken", null);
        }

        @Override
        public boolean contains(final Charset cs) {
            return false;
        }

        @Override
        public CharsetDecoder newDecoder() {
            throw new AssertionError("no decoder");
        }

        @Override
        public CharsetEncoder newEncoder() {
            throw new AssertionError("no encoder");
        }

    }

}
