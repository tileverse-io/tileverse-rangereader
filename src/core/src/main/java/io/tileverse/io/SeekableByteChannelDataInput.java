/*
 * (c) Copyright 2025 Multiversio LLC. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *          http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.tileverse.io;

import java.io.DataInput;
import java.io.EOFException;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.SeekableByteChannel;
import java.util.Objects;

/**
 * A {@link DataInput} adapter that wraps a {@link SeekableByteChannel} to provide
 * standard Java DataInput semantics with optimized seeking capabilities.
 * <p>
 * This adapter provides the same functionality as {@link ReadableByteChannelDataInput}
 * but with an optimized {@link #skipBytes(int)} implementation that uses the channel's
 * seeking capability instead of reading and discarding data, making it much more
 * efficient for large skip operations.
 * <p>
 * <strong>Thread Safety:</strong> This class is not thread-safe. External synchronization
 * is required if multiple threads access the same instance concurrently.
 * <p>
 * <strong>Read-Only Adapter:</strong> This adapter only provides reading functionality.
 * It maintains an internal buffer for efficient reading of primitive types.
 *
 * <p><strong>Usage Example:</strong></p>
 * <pre>{@code
 * try (SeekableByteChannel channel = Files.newByteChannel(path, StandardOpenOption.READ)) {
 *     DataInput dataInput = SeekableByteChannelDataInput.of(channel);
 *
 *     // Read header at the beginning
 *     int version = dataInput.readInt();
 *
 *     // Efficiently skip to data section (no actual reading)
 *     dataInput.skipBytes(1024);
 *
 *     // Read data after the skip
 *     double value = dataInput.readDouble();
 * }
 * }</pre>
 */
public final class SeekableByteChannelDataInput implements DataInput {

    /** The underlying SeekableByteChannel that provides seeking capability. */
    private final SeekableByteChannel channel;

    /** Internal buffer for efficient reading of primitive types. */
    private final ByteBuffer buffer;

    /** Default buffer size for internal operations. */
    private static final int DEFAULT_BUFFER_SIZE = 8192;

    /**
     * Creates a new DataInput adapter for the given SeekableByteChannel.
     *
     * @param channel the SeekableByteChannel to wrap
     * @throws IllegalArgumentException if channel is null
     */
    private SeekableByteChannelDataInput(SeekableByteChannel channel) {
        this(channel, DEFAULT_BUFFER_SIZE);
    }

    /**
     * Creates a new DataInput adapter for the given SeekableByteChannel with specified buffer size.
     *
     * @param channel the SeekableByteChannel to wrap
     * @param bufferSize the size of the internal buffer
     * @throws IllegalArgumentException if channel is null or bufferSize is non-positive
     */
    private SeekableByteChannelDataInput(SeekableByteChannel channel, int bufferSize) {
        this.channel = Objects.requireNonNull(channel, "channel cannot be null");
        if (bufferSize <= 0) {
            throw new IllegalArgumentException("bufferSize must be positive: " + bufferSize);
        }
        this.buffer = ByteBuffer.allocate(bufferSize);
        this.buffer.limit(0); // Start with empty buffer
    }

    /**
     * Creates a new DataInput adapter for the given SeekableByteChannel.
     *
     * @param channel the SeekableByteChannel to wrap
     * @return a new DataInput adapter
     * @throws IllegalArgumentException if channel is null
     */
    public static SeekableByteChannelDataInput of(SeekableByteChannel channel) {
        return new SeekableByteChannelDataInput(channel);
    }

    /**
     * Creates a new DataInput adapter for the given SeekableByteChannel with specified buffer size.
     *
     * @param channel the SeekableByteChannel to wrap
     * @param bufferSize the size of the internal buffer
     * @return a new DataInput adapter
     * @throws IllegalArgumentException if channel is null or bufferSize is non-positive
     */
    public static SeekableByteChannelDataInput of(SeekableByteChannel channel, int bufferSize) {
        return new SeekableByteChannelDataInput(channel, bufferSize);
    }

    @Override
    public void readFully(byte[] b) throws IOException {
        readFully(b, 0, b.length);
    }

    @Override
    public void readFully(byte[] b, int off, int len) throws IOException {
        Objects.requireNonNull(b, "byte array cannot be null");
        if (off < 0 || len < 0 || off + len > b.length) {
            throw new IndexOutOfBoundsException("off=" + off + ", len=" + len + ", array.length=" + b.length);
        }

        int totalRead = 0;
        while (totalRead < len) {
            int bytesRead = read(b, off + totalRead, len - totalRead);
            if (bytesRead < 0) {
                throw new EOFException("Unexpected end of stream after reading " + totalRead + " of " + len + " bytes");
            }
            totalRead += bytesRead;
        }
    }

    /**
     * {@inheritDoc}
     * <p>
     * This implementation uses the channel's seeking capability to efficiently skip
     * over data without reading it, making it much faster than reading and discarding
     * for large skip operations.
     *
     * @param n the number of bytes to skip
     * @return the actual number of bytes skipped
     * @throws IOException if an I/O error occurs
     */
    @Override
    public int skipBytes(int n) throws IOException {
        if (n <= 0) {
            return 0;
        }

        // Calculate the current logical position (accounting for buffered data)
        long currentLogicalPosition = position();
        long channelSize = channel.size();

        // Calculate how many bytes we can actually skip
        long maxSkip = channelSize - currentLogicalPosition;
        int actualSkip = (int) Math.min(n, maxSkip);

        if (actualSkip <= 0) {
            return 0;
        }

        // Clear the buffer since we're changing position
        buffer.clear();
        buffer.limit(0);

        // Seek to new logical position
        channel.position(currentLogicalPosition + actualSkip);

        return actualSkip;
    }

    @Override
    public boolean readBoolean() throws IOException {
        return readByte() != 0;
    }

    @Override
    public byte readByte() throws IOException {
        ensureAvailable(1);
        return buffer.get();
    }

    @Override
    public int readUnsignedByte() throws IOException {
        return readByte() & 0xFF;
    }

    @Override
    public short readShort() throws IOException {
        ensureAvailable(2);
        return buffer.getShort();
    }

    @Override
    public int readUnsignedShort() throws IOException {
        return readShort() & 0xFFFF;
    }

    @Override
    public char readChar() throws IOException {
        ensureAvailable(2);
        return buffer.getChar();
    }

    @Override
    public int readInt() throws IOException {
        ensureAvailable(4);
        return buffer.getInt();
    }

    @Override
    public long readLong() throws IOException {
        ensureAvailable(8);
        return buffer.getLong();
    }

    @Override
    public float readFloat() throws IOException {
        ensureAvailable(4);
        return buffer.getFloat();
    }

    @Override
    public double readDouble() throws IOException {
        ensureAvailable(8);
        return buffer.getDouble();
    }

    @Override
    public String readLine() throws IOException {
        StringBuilder line = new StringBuilder();
        int c;

        try {
            while ((c = readUnsignedByte()) != -1) {
                if (c == '\n') {
                    break;
                }
                if (c == '\r') {
                    // Peek ahead for \n
                    mark();
                    try {
                        int next = readUnsignedByte();
                        if (next != '\n' && next != -1) {
                            reset();
                        }
                    } catch (EOFException e) {
                        // End of stream after \r, that's fine
                    }
                    break;
                }
                line.append((char) c);
            }
        } catch (EOFException e) {
            // End of stream reached
            c = -1;
        }

        return line.length() > 0 || c != -1 ? line.toString() : null;
    }

    @Override
    public String readUTF() throws IOException {
        int utflen = readUnsignedShort();
        byte[] bytearr = new byte[utflen];
        readFully(bytearr);

        // Use DataInputStream's modified UTF-8 decoding for compatibility
        return readModifiedUTF8(bytearr);
    }

    /**
     * Reads a modified UTF-8 string as used by DataInputStream/DataOutputStream.
     * This handles the special encoding used by Java's writeUTF/readUTF methods.
     */
    private String readModifiedUTF8(byte[] bytearr) throws IOException {
        int utflen = bytearr.length;
        char[] chararr = new char[utflen];
        int c, char2, char3;
        int count = 0;
        int chararr_count = 0;

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            if (c > 127) break;
            count++;
            chararr[chararr_count++] = (char) c;
        }

        while (count < utflen) {
            c = (int) bytearr[count] & 0xff;
            switch (c >> 4) {
                case 0:
                case 1:
                case 2:
                case 3:
                case 4:
                case 5:
                case 6:
                case 7:
                    /* 0xxxxxxx*/
                    count++;
                    chararr[chararr_count++] = (char) c;
                    break;
                case 12:
                case 13:
                    /* 110x xxxx   10xx xxxx*/
                    count += 2;
                    if (count > utflen) throw new IOException("malformed input: partial character at end");
                    char2 = (int) bytearr[count - 1];
                    if ((char2 & 0xC0) != 0x80) throw new IOException("malformed input around byte " + count);
                    chararr[chararr_count++] = (char) (((c & 0x1F) << 6) | (char2 & 0x3F));
                    break;
                case 14:
                    /* 1110 xxxx  10xx xxxx  10xx xxxx */
                    count += 3;
                    if (count > utflen) throw new IOException("malformed input: partial character at end");
                    char2 = (int) bytearr[count - 2];
                    char3 = (int) bytearr[count - 1];
                    if (((char2 & 0xC0) != 0x80) || ((char3 & 0xC0) != 0x80))
                        throw new IOException("malformed input around byte " + (count - 1));
                    chararr[chararr_count++] =
                            (char) (((c & 0x0F) << 12) | ((char2 & 0x3F) << 6) | ((char3 & 0x3F) << 0));
                    break;
                default:
                    /* 10xx xxxx,  1111 xxxx */
                    throw new IOException("malformed input around byte " + count);
            }
        }
        // The number of chars produced may be less than utflen
        return new String(chararr, 0, chararr_count);
    }

    /**
     * Reads data into the provided byte array.
     *
     * @param b the byte array to read into
     * @param off the offset in the array
     * @param len the number of bytes to read
     * @return the number of bytes read, or -1 if end of stream
     * @throws IOException if an I/O error occurs
     */
    private int read(byte[] b, int off, int len) throws IOException {
        if (len == 0) {
            return 0;
        }

        // Try to read from buffer first
        int fromBuffer = Math.min(buffer.remaining(), len);
        if (fromBuffer > 0) {
            buffer.get(b, off, fromBuffer);
            return fromBuffer;
        }

        // Buffer is empty, fill it from channel
        if (!fillBuffer()) {
            return -1; // End of stream
        }

        // Read from newly filled buffer
        int available = Math.min(buffer.remaining(), len);
        buffer.get(b, off, available);
        return available;
    }

    /**
     * Ensures that at least the specified number of bytes are available in the buffer.
     *
     * @param bytes the number of bytes required
     * @throws EOFException if not enough bytes are available
     * @throws IOException if an I/O error occurs
     */
    private void ensureAvailable(int bytes) throws IOException {
        while (buffer.remaining() < bytes) {
            if (!fillBuffer()) {
                throw new EOFException(
                        "Unexpected end of stream, needed " + bytes + " bytes, got " + buffer.remaining());
            }
        }
    }

    /**
     * Fills the internal buffer from the channel.
     *
     * @return true if data was read, false if end of stream
     * @throws IOException if an I/O error occurs
     */
    private boolean fillBuffer() throws IOException {
        // If buffer has remaining data, compact it to preserve unread bytes
        if (buffer.hasRemaining()) {
            buffer.compact();
        } else {
            buffer.clear();
        }

        int bytesRead = channel.read(buffer);
        buffer.flip();

        return bytesRead > 0;
    }

    /**
     * Marks the current position for potential reset (simplified implementation).
     */
    private void mark() {
        buffer.mark();
    }

    /**
     * Resets to the marked position (simplified implementation).
     */
    private void reset() {
        buffer.reset();
    }

    /**
     * Gets the underlying SeekableByteChannel.
     *
     * @return the underlying seekable channel
     */
    public SeekableByteChannel getChannel() {
        return channel;
    }

    /**
     * Gets the current logical position for reading, accounting for buffered data.
     *
     * @return the current logical position in bytes
     * @throws IOException if an I/O error occurs
     */
    public long position() throws IOException {
        // The logical position is the channel position minus any remaining buffer data
        return channel.position() - buffer.remaining();
    }

    /**
     * Gets the size of the channel.
     *
     * @return the size of the channel in bytes
     * @throws IOException if an I/O error occurs
     */
    public long size() throws IOException {
        return channel.size();
    }

    @Override
    public String toString() {
        try {
            return String.format(
                    "SeekableByteChannelDataInput[channel=%s, position=%d, size=%d, bufferSize=%d, bufferRemaining=%d]",
                    channel, channel.position(), channel.size(), buffer.capacity(), buffer.remaining());
        } catch (IOException e) {
            return String.format(
                    "SeekableByteChannelDataInput[channel=%s, position=unknown, size=unknown, bufferSize=%d, bufferRemaining=%d]",
                    channel, buffer.capacity(), buffer.remaining());
        }
    }
}
