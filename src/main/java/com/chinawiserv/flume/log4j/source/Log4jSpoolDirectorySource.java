/**   
 * @Title: Log4jSpoolDirectorySource.java 
 * @Package com.chinawiserv.flume.log4j.source 
 * @Description: TODO(用一句话描述该文件做什么) 
 * @author dengb@chinawiserv.com   
 * @date 2016年5月25日 上午10:32:38 
 * @version V1.0   
 */
package com.chinawiserv.flume.log4j.source;

import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.BASENAME_HEADER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.BASENAME_HEADER_KEY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.BATCH_SIZE;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.BUFFER_MAX_LINE_LENGTH;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.CONSUME_ORDER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DECODE_ERROR_POLICY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_BASENAME_HEADER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_BASENAME_HEADER_KEY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_BATCH_SIZE;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_CONSUME_ORDER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_DECODE_ERROR_POLICY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_DELETE_POLICY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_DESERIALIZER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_FILENAME_HEADER_KEY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_FILE_HEADER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_IGNORE_PAT;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_INPUT_CHARSET;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_MAX_BACKOFF;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_SPOOLED_FILE_SUFFIX;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DEFAULT_TRACKER_DIR;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DELETE_POLICY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DESERIALIZER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.FILENAME_HEADER;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.FILENAME_HEADER_KEY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.IGNORE_PAT;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.INPUT_CHARSET;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.MAX_BACKOFF;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.SPOOLED_FILE_SUFFIX;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.SPOOL_DIRECTORY;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.TRACKER_DIR;
import static com.chinawiserv.flume.log4j.source.Log4jSpoolDirectorySourceConfigurationConstants.DATE_PATTERN;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.apache.flume.ChannelException;
import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.EventDrivenSource;
import org.apache.flume.FlumeException;
import org.apache.flume.conf.Configurable;
import org.apache.flume.instrumentation.SourceCounter;
import org.apache.flume.serialization.DecodeErrorPolicy;
import org.apache.flume.serialization.LineDeserializer;
import org.apache.flume.source.AbstractSource;
import org.apache.flume.source.SpoolDirectorySourceConfigurationConstants.ConsumeOrder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Preconditions;
import com.google.common.base.Throwables;

/**
 * @ClassName: Log4jSpoolDirectorySource
 * @Description: TODO(这里用一句话描述这个类的作用)
 * @author dengb@chinawiserv.com
 * @date 2016年5月25日 上午10:32:38
 * 
 */
public class Log4jSpoolDirectorySource extends AbstractSource implements Configurable, EventDrivenSource {

	private static final Logger logger = LoggerFactory.getLogger(Log4jSpoolDirectorySource.class);

	// Delay used when polling for new files
	private static final int POLL_DELAY_MS = 500;

	/* Config options */
	private String completedSuffix;
	private String spoolDirectory;
	private boolean fileHeader;
	private String fileHeaderKey;
	private boolean basenameHeader;
	private String basenameHeaderKey;
	private int batchSize;
	private String ignorePattern;
	private String trackerDirPath;
	private String deserializerType;
	private Context deserializerContext;
	private String deletePolicy;
	private String inputCharset;
	private DecodeErrorPolicy decodeErrorPolicy;
	private volatile boolean hasFatalError = false;

	private SourceCounter sourceCounter;
	Log4jReliableSpoolingFileEventReader reader;
	private ScheduledExecutorService executor;
	private boolean backoff = true;
	private boolean hitChannelException = false;
	private int maxBackoff;
	private ConsumeOrder consumeOrder;
	private String pattern;

	@Override
	public synchronized void start() {
		logger.info("SpoolDirectorySource source starting with directory: {}", spoolDirectory);

		executor = Executors.newSingleThreadScheduledExecutor();

		File directory = new File(spoolDirectory);
		try {
			reader = new Log4jReliableSpoolingFileEventReader.Builder().spoolDirectory(directory)
					.completedSuffix(completedSuffix).ignorePattern(ignorePattern).trackerDirPath(trackerDirPath)
					.annotateFileName(fileHeader).fileNameHeader(fileHeaderKey).annotateBaseName(basenameHeader)
					.baseNameHeader(basenameHeaderKey).deserializerType(deserializerType)
					.deserializerContext(deserializerContext).deletePolicy(deletePolicy).inputCharset(inputCharset)
					.decodeErrorPolicy(decodeErrorPolicy).consumeOrder(consumeOrder).setPattern(pattern).build();
		} catch (IOException ioe) {
			throw new FlumeException("Error instantiating spooling event parser", ioe);
		}

		Runnable runner = new SpoolDirectoryRunnable(reader, sourceCounter);
		executor.scheduleWithFixedDelay(runner, 0, POLL_DELAY_MS, TimeUnit.SECONDS);

		super.start();
		logger.debug("SpoolDirectorySource source started");
		sourceCounter.start();
	}

	@Override
	public synchronized void stop() {
		executor.shutdown();
		try {
			executor.awaitTermination(10L, TimeUnit.SECONDS);
		} catch (InterruptedException ex) {
			logger.info("Interrupted while awaiting termination", ex);
		}
		executor.shutdownNow();

		super.stop();
		sourceCounter.stop();
		logger.info("SpoolDir source {} stopped. Metrics: {}", getName(), sourceCounter);
	}

	@Override
	public String toString() {
		return "Spool Directory source " + getName() + ": { spoolDir: " + spoolDirectory + " }";
	}

	@Override
	public synchronized void configure(Context context) {
		spoolDirectory = context.getString(SPOOL_DIRECTORY);
		Preconditions.checkState(spoolDirectory != null, "Configuration must specify a spooling directory");

		completedSuffix = context.getString(SPOOLED_FILE_SUFFIX, DEFAULT_SPOOLED_FILE_SUFFIX);
		deletePolicy = context.getString(DELETE_POLICY, DEFAULT_DELETE_POLICY);
		fileHeader = context.getBoolean(FILENAME_HEADER, DEFAULT_FILE_HEADER);
		fileHeaderKey = context.getString(FILENAME_HEADER_KEY, DEFAULT_FILENAME_HEADER_KEY);
		basenameHeader = context.getBoolean(BASENAME_HEADER, DEFAULT_BASENAME_HEADER);
		basenameHeaderKey = context.getString(BASENAME_HEADER_KEY, DEFAULT_BASENAME_HEADER_KEY);
		batchSize = context.getInteger(BATCH_SIZE, DEFAULT_BATCH_SIZE);
		inputCharset = context.getString(INPUT_CHARSET, DEFAULT_INPUT_CHARSET);
		decodeErrorPolicy = DecodeErrorPolicy.valueOf(
				context.getString(DECODE_ERROR_POLICY, DEFAULT_DECODE_ERROR_POLICY).toUpperCase(Locale.ENGLISH));

		ignorePattern = context.getString(IGNORE_PAT, DEFAULT_IGNORE_PAT);
		trackerDirPath = context.getString(TRACKER_DIR, DEFAULT_TRACKER_DIR);

		deserializerType = context.getString(DESERIALIZER, DEFAULT_DESERIALIZER);
		deserializerContext = new Context(context.getSubProperties(DESERIALIZER + "."));

		consumeOrder = ConsumeOrder.valueOf(
				context.getString(CONSUME_ORDER, DEFAULT_CONSUME_ORDER.toString()).toUpperCase(Locale.ENGLISH));

		// "Hack" to support backwards compatibility with previous generation of
		// spooling directory source, which did not support deserializers
		Integer bufferMaxLineLength = context.getInteger(BUFFER_MAX_LINE_LENGTH);
		if (bufferMaxLineLength != null && deserializerType != null
				&& deserializerType.equalsIgnoreCase(DEFAULT_DESERIALIZER)) {
			deserializerContext.put(LineDeserializer.MAXLINE_KEY, bufferMaxLineLength.toString());
		}

		maxBackoff = context.getInteger(MAX_BACKOFF, DEFAULT_MAX_BACKOFF);
		if (sourceCounter == null) {
			sourceCounter = new SourceCounter(getName());
		}
		
		pattern = context.getString(DATE_PATTERN, DEFAULT_DESERIALIZER);
	}

	@VisibleForTesting
	protected boolean hasFatalError() {
		return hasFatalError;
	}

	/**
	 * The class always backs off, this exists only so that we can test without
	 * taking a really long time.
	 * 
	 * @param backoff
	 *            - whether the source should backoff if the channel is full
	 */
	@VisibleForTesting
	protected void setBackOff(boolean backoff) {
		this.backoff = backoff;
	}

	@VisibleForTesting
	protected boolean hitChannelException() {
		return hitChannelException;
	}

	@VisibleForTesting
	protected SourceCounter getSourceCounter() {
		return sourceCounter;
	}

	private class SpoolDirectoryRunnable implements Runnable {
		
		//5M
		private static final int WRITE_EVENT_MAX = 5*1024*1024;
		
		private Log4jReliableSpoolingFileEventReader reader;
		private SourceCounter sourceCounter;

		public SpoolDirectoryRunnable(Log4jReliableSpoolingFileEventReader reader, SourceCounter sourceCounter) {
			this.reader = reader;
			this.sourceCounter = sourceCounter;
		}

		@Override
		public void run() {
			int backoffInterval = 250;
			try {
				while (!Thread.interrupted()) {
					List<Event> events = reader.readEvents(batchSize);
					if (events.isEmpty()) {
						break;
					}
					sourceCounter.addToEventReceivedCount(events.size());
					sourceCounter.incrementAppendBatchReceivedCount();

					try {
						getChannelProcessor().processEventBatch(events);
						reader.commit();
					} catch (ChannelException ex) {
						logger.warn("The channel is full, and cannot write data now. The "
								+ "source will try again after " + String.valueOf(backoffInterval) + " milliseconds");
						hitChannelException = true;
						if (backoff) {
							TimeUnit.MILLISECONDS.sleep(backoffInterval);
							backoffInterval = backoffInterval << 1;
							backoffInterval = backoffInterval >= maxBackoff ? maxBackoff : backoffInterval;
						}
						continue;
					}
					backoffInterval = 250;
					sourceCounter.addToEventAcceptedCount(events.size());
					sourceCounter.incrementAppendBatchAcceptedCount();
				}
			} catch (Throwable t) {
				logger.error("FATAL: " + Log4jSpoolDirectorySource.this.toString() + ": "
						+ "Uncaught exception in SpoolDirectorySource thread. "
						+ "Restart or reconfigure Flume to continue processing.", t);
				hasFatalError = true;
				Throwables.propagate(t);
			}
		}
	}
}
