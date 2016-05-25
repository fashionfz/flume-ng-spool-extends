/**   
 * @Title: Log4jDeserializer.java 
 * @Package com.chinawiserv.flume.log4j.source 
 * @Description: TODO(用一句话描述该文件做什么) 
 * @author dengb@chinawiserv.com   
 * @date 2016年5月25日 上午11:26:42 
 * @version V1.0   
 */
package com.chinawiserv.flume.log4j.source;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.apache.flume.Context;
import org.apache.flume.Event;
import org.apache.flume.annotations.InterfaceAudience;
import org.apache.flume.annotations.InterfaceStability;
import org.apache.flume.event.EventBuilder;
import org.apache.flume.serialization.EventDeserializer;
import org.apache.flume.serialization.ResettableInputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.collect.Lists;

/**
 * @ClassName: Log4jDeserializer
 * @Description: TODO(这里用一句话描述这个类的作用)
 * @author dengb@chinawiserv.com
 * @date 2016年5月25日 上午11:26:42
 * 
 */
@InterfaceAudience.Private
@InterfaceStability.Evolving
public class Log4jDeserializer implements EventDeserializer {

	private static final Logger logger = LoggerFactory.getLogger(Log4jDeserializer.class);

	private final ResettableInputStream in;
	private final Charset outputCharset;
	private final int maxLineLength;
	private volatile boolean isOpen;

	public static final String OUT_CHARSET_KEY = "outputCharset";
	public static final String CHARSET_DFLT = "UTF-8";

	public static final String MAXLINE_KEY = "maxLineLength";
	public static final int MAXLINE_DFLT = 2048;
	
	private final StringBuffer sb1 = new StringBuffer();
	private final StringBuffer sb2 = new StringBuffer();
	
	public StringBuffer write = sb1;
	private StringBuffer read = sb2;
	
	private Pattern p;
	private Matcher m;
	
	//5M
	private static final int WRITE_EVENT_MAX = 5*1024*1024;

	Log4jDeserializer(Context context, ResettableInputStream in, String pattern) {
	    this.in = in;
	    this.outputCharset = Charset.forName(
	        context.getString(OUT_CHARSET_KEY, CHARSET_DFLT));
	    this.maxLineLength = context.getInteger(MAXLINE_KEY, MAXLINE_DFLT);
	    this.isOpen = true;
	    
	    p = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}");
	  }

	
	public Event readLastCache(){
		if(write.length() >0){
			Event event = EventBuilder.withBody(write.toString(), outputCharset);
			write.setLength(0);
			return event;
		}
			return null;
	}
	/**
	 * Reads a line from a file and returns an event
	 * 
	 * @return Event containing parsed line
	 * @throws IOException
	 */
	@Override
	public Event readEvent() throws IOException {
		while(true){
			ensureOpen();
			String line = readLine();
			if (line == null) {
				return readLastCache();
			} else {
				m = p.matcher(line);
				if(write.length() > WRITE_EVENT_MAX || m.find()){
					if(write == sb1){
						write = sb2;
						read = sb1;
					}else{
						write = sb1;
						read = sb2;
					}
					write.append(line);
					if(read.length() > 0){
						Event event = EventBuilder.withBody(read.toString(), outputCharset);
						read.setLength(0);
						return event;
					}
				}else{
					write.append("\n"+line);
				}
			}
		}

	}
	/**
	 * Batch line read
	 * 
	 * @param numEvents
	 *            Maximum number of events to return.
	 * @return List of events containing read lines
	 * @throws IOException
	 */
	@Override
	public List<Event> readEvents(int numEvents) throws IOException {
		ensureOpen();
		List<Event> events = Lists.newLinkedList();
		for (int i = 0; i < numEvents; i++) {
			Event event = readEvent();
			if (event != null) {
				events.add(event);
			} else {
				break;
			}
		}
		return events;
	}

	@Override
	public void mark() throws IOException {
		ensureOpen();
		in.mark();
	}

	@Override
	public void reset() throws IOException {
		ensureOpen();
		in.reset();
	}

	@Override
	public void close() throws IOException {
		if (isOpen) {
			reset();
			in.close();
			isOpen = false;
		}
	}

	private void ensureOpen() {
		if (!isOpen) {
			throw new IllegalStateException("Serializer has been closed");
		}
	}

	// TODO: consider not returning a final character that is a high surrogate
	// when truncating
	private String readLine() throws IOException {
		StringBuilder sb = new StringBuilder();
		int c;
		int readChars = 0;
		while ((c = in.readChar()) != -1) {
			readChars++;

			// FIXME: support \r\n
			if (c == '\n') {
				break;
			}

			sb.append((char) c);

			if (readChars >= maxLineLength) {
				logger.warn("Line length exceeds max ({}), truncating line!", maxLineLength);
				break;
			}
		}

		if (readChars > 0) {
			return sb.toString();
		} else {
			return null;
		}
	}

}
