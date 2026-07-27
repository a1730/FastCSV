package de.siegmar.fastcsv.reader;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;

import org.junit.jupiter.api.Test;

/// Streams the library opens itself (the `Path` based `build` methods) must not leak when reader
/// construction fails.
@SuppressWarnings("PMD.CloseResource")
class CsvReaderBuilderStreamOwnershipTest {

    /// A builder configuration that is only rejected when the parser is constructed – long after the
    /// file has been opened.
    private static final CsvReader.CsvReaderBuilder BROKEN_CONFIG = CsvReader.builder()
        .fieldSeparator(',')
        .quoteCharacter(',');

    @Test
    void closesStreamWhenParserConstructionFails() {
        final TrackingInputStream inputStream = new TrackingInputStream();

        assertThatThrownBy(() -> BROKEN_CONFIG.buildOwning(CsvRecordHandler.of(), inputStream, UTF_8))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessage("Control characters must differ (fieldSeparator=,, quoteCharacter=,)");

        assertThat(inputStream.closed).isTrue();
    }

    /// Deliberately an *unchecked* close failure: those are the ones that used to displace the
    /// construction failure instead of being suppressed.
    @Test
    void reportsCloseFailureAsSuppressed() {
        final TrackingInputStream inputStream = new TrackingInputStream();
        inputStream.closeFailure = new IllegalStateException("close failed");

        assertThatThrownBy(() -> BROKEN_CONFIG.buildOwning(CsvRecordHandler.of(), inputStream, UTF_8))
            .isInstanceOf(IllegalArgumentException.class)
            .satisfies(e -> assertThat(e.getSuppressed()).singleElement()
                .isSameAs(inputStream.closeFailure));
    }

    /// The counterpart contract: streams handed in by the caller stay the caller's to close.
    @Test
    void keepsCallerSuppliedStreamOpenWhenParserConstructionFails() {
        final TrackingInputStream inputStream = new TrackingInputStream();

        assertThatThrownBy(() -> BROKEN_CONFIG.build(CsvRecordHandler.of(), inputStream, UTF_8))
            .isInstanceOf(IllegalArgumentException.class);

        assertThat(inputStream.closed).isFalse();
    }

    private static final class TrackingInputStream extends ByteArrayInputStream {

        private boolean closed;

        private RuntimeException closeFailure;

        TrackingInputStream() {
            super(new byte[0]);
        }

        @Override
        public void close() {
            closed = true;
            if (closeFailure != null) {
                throw closeFailure;
            }
        }

    }

}
