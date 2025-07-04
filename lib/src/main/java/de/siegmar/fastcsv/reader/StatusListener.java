package de.siegmar.fastcsv.reader;

/// Custom status listeners have to implement this interface.
///
/// FastCSV will call these methods synchronously –
/// make sure **not to perform time-consuming / blocking** tasks!
///
/// @see IndexedCsvReader.IndexedCsvReaderBuilder#statusListener(StatusListener)
public interface StatusListener {

    /// Called on initialization.
    ///
    /// @param fileSize the total file size.
    default void onInit(final long fileSize) {
    }

    /// Called when a new record has been read.
    default void onReadRecord() {
    }

    /// Called when a new read operation has been performend.
    ///
    /// @param bytes number of bytes read.
    default void onReadBytes(final int bytes) {
    }

    /// Called when the indexing finished successfully (without an exception).
    default void onComplete() {
    }

    /// Called when there was an error while indexing.
    ///
    /// @param throwable the exception thrown.
    default void onError(final Throwable throwable) {
    }

}
