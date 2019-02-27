/**
 * Copyright 2019 Anthony Trinh
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ch.qos.logback.core;

import static ch.qos.logback.core.CoreConstants.CODES_URL;

import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.ReentrantLock;

import ch.qos.logback.core.encoder.Encoder;
import ch.qos.logback.core.encoder.LayoutWrappingEncoder;
import ch.qos.logback.core.spi.DeferredProcessingAware;
import ch.qos.logback.core.status.ErrorStatus;

/**
 * OutputStreamAppender appends events to a {@link OutputStream}. This class
 * provides basic services that other appenders build upon.
 *
 * For more information about this appender, please refer to the online manual
 * at http://logback.qos.ch/manual/appenders.html#OutputStreamAppender
 *
 * @author Ceki G&uuml;lc&uuml;
 */
public class OutputStreamAppender<E> extends UnsynchronizedAppenderBase<E> {


  /**
   * It is the encoder which is ultimately responsible for writing the event to
   * an {@link OutputStream}.
   */
  protected Encoder<E> encoder;

  /**
   * All synchronization in this class is done via the lock object.
   */
  protected final ReentrantLock lock = new ReentrantLock(false);

  /**
   * This is the {@link OutputStream outputStream} where output will be written.
   */
  private OutputStream outputStream;

  private boolean immediateFlush = true;

  /**
   * The underlying output stream used by this appender.
   *
   * @return the output stream
   */
  public OutputStream getOutputStream() {
    return outputStream;
  }

  /**
   * Checks that requires parameters are set and if everything is in order,
   * activates this appender.
   */
  public void start() {
    int errors = 0;
    if (this.encoder == null) {
      addStatus(new ErrorStatus("No encoder set for the appender named \""
          + name + "\".", this));
      errors++;
    }

    if (this.outputStream == null) {
      addStatus(new ErrorStatus(
          "No output stream set for the appender named \"" + name + "\".", this));
      errors++;
    }
    // only error free appenders should be activated
    if (errors == 0) {
      super.start();
    }
  }

  public void setLayout(Layout<E> layout) {
    addWarn("This appender no longer admits a layout as a sub-component, set an encoder instead.");
    addWarn("To ensure compatibility, wrapping your layout in LayoutWrappingEncoder.");
    addWarn("See also "+CODES_URL+"#layoutInsteadOfEncoder for details");
    LayoutWrappingEncoder<E> lwe = new LayoutWrappingEncoder<E>();
    lwe.setLayout(layout);
    lwe.setContext(context);
    this.encoder = lwe;
  }

  @Override
  protected void append(E eventObject) {
    if (!isStarted()) {
      return;
    }

    subAppend(eventObject);
  }

  /**
   * Stop this appender instance. The underlying stream or writer is also
   * closed.
   *
   * <p>
   * Stopped appenders cannot be reused.
   */
  public void stop() {
    lock.lock();
    try {
      closeOutputStream();
      super.stop();
    } finally {
      lock.unlock();
    }
  }

  /**
   * Close the underlying {@link OutputStream}.
   */
  protected void closeOutputStream() {
    if (this.outputStream != null) {
      try {
        // before closing we have to output out layout's footer
        encoderClose();
        this.outputStream.close();
        this.outputStream = null;
      } catch (IOException e) {
        addStatus(new ErrorStatus(
            "Could not close output stream for OutputStreamAppender.", this, e));
      }
    }
  }

  void encoderInit() {
    if (encoder != null && this.outputStream != null) {
      try {
        byte[] header = encoder.headerBytes();
        writeBytes(header);
      } catch (IOException ioe) {
        this.started = false;
        addStatus(new ErrorStatus(
            "Failed to initialize encoder for appender named [" + name + "].",
            this, ioe));
      }
    }
  }

  void encoderClose() {
    if (encoder != null && this.outputStream != null) {
      try {
        byte[] footer = encoder.footerBytes();
        writeBytes(footer);
      } catch (IOException ioe) {
        this.started = false;
        addStatus(new ErrorStatus("Failed to write footer for appender named ["
            + name + "].", this, ioe));
      }
    }
  }

  /**
   * <p>
   * Sets the @link OutputStream} where the log output will go. The specified
   * <code>OutputStream</code> must be opened by the user and be writable. The
   * <code>OutputStream</code> will be closed when the appender instance is
   * closed.
   *
   * @param outputStream
   *          An already opened OutputStream.
   */
  public void setOutputStream(OutputStream outputStream) {
    lock.lock();
    try {
      // close any previously opened output stream
      closeOutputStream();

      this.outputStream = outputStream;
      if (encoder == null) {
        addWarn("Encoder has not been set. Cannot invoke its init method.");
        return;
      }

      encoderInit();
    } finally {
      lock.unlock();
    }
  }

  protected void writeOut(E event) throws IOException {
    byte[] byteArray = this.encoder.encode(event);
    writeBytes(byteArray);
  }

  private void writeBytes(byte[] byteArray) throws IOException {
    if (byteArray == null || byteArray.length == 0) {
      return;
    }

    lock.lock();
    try {
      this.outputStream.write(byteArray);
      if (immediateFlush) {
        this.outputStream.flush();
      }
    } finally {
      lock.unlock();
    }
  }

  /**
   * Actual writing occurs here.
   * <p>
   * Most subclasses of <code>WriterAppender</code> will need to override this
   * method.
   *
   * @param event the log event
   * @since 0.9.0
   */
  protected void subAppend(E event) {
    if (!isStarted()) {
      return;
    }
    try {
      // this step avoids LBCLASSIC-139
      if (event instanceof DeferredProcessingAware) {
        ((DeferredProcessingAware) event).prepareForDeferredProcessing();
      }
      // the synchronization prevents the OutputStream from being closed while we
      // are writing. It also prevents multiple threads from entering the same
      // converter. Converters assume that they are in a synchronized block.
      //lock.lock();
      byte[] byteArray = this.encoder.encode(event);
      writeBytes(byteArray);

    } catch (IOException ioe) {
      // as soon as an exception occurs, move to non-started state
      // and add a single ErrorStatus to the SM.
      this.started = false;
      addStatus(new ErrorStatus("IO failure in appender", this, ioe));
    }
  }

  public Encoder<E> getEncoder() {
    return encoder;
  }

  public void setEncoder(Encoder<E> encoder) {
    this.encoder = encoder;
  }

  public boolean isImmediateFlush() {
    return immediateFlush;
  }

  public void setImmediateFlush(boolean immediateFlush) {
    this.immediateFlush = immediateFlush;
  }
}
