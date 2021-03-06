/*
   Copyright 2018 Nationale-Nederlanden, 2020 WeAreFrank!

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

       http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License.
*/
package nl.nn.adapterframework.pipes;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

import org.junit.Test;
import org.mockito.Mock;

import nl.nn.adapterframework.configuration.ConfigurationException;
import nl.nn.adapterframework.core.IPipeLineSession;
import nl.nn.adapterframework.core.PipeRunException;
import nl.nn.adapterframework.core.PipeRunResult;
import nl.nn.adapterframework.core.PipeStartException;
import nl.nn.adapterframework.stream.StreamingPipeTestBase;
import nl.nn.adapterframework.util.Misc;

public class Base64PipeTest extends StreamingPipeTestBase<Base64Pipe> {

	@Mock
	private IPipeLineSession session;

	private String input = "Bacon ipsum dolor amet chuck pork loin flank picanha.";
	private String output = "QmFjb24gaXBzdW0gZG9sb3IgYW1ldCBjaHVjayBwb3JrIGxvaW4gZmxhbmsgcGljYW5oYS4=";

	@Override
	public Base64Pipe createPipe() {
		return new Base64Pipe();
	}

	@Test(expected = ConfigurationException.class)
	public void noDirection() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setDirection("");
		pipe.configure();
	}

	@Test(expected = ConfigurationException.class)
	public void wrongDirection() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setDirection("not encode");
		pipe.configure();
	}

	@Test(expected = ConfigurationException.class)
	public void wrongOutputType() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("not string or stream or bytes");
		pipe.configure();
	}

	@Test(expected = PipeRunException.class)
	public void wrongInputEncoding() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		assumeFalse(provideStreamForInput); // when providing an outputstream, the charset is not used for decoding the input
		pipe.setCharset("test123");
		pipe.configure();
		pipe.start();

		doPipe(pipe,input, session);
	}

	@Test(expected = PipeRunException.class)
	public void wrongOutputEncoding() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setCharset("test123");
		pipe.setOutputType("string");
		pipe.configure();
		pipe.start();

		doPipe(pipe, input.getBytes(), session);
	}

	@Test
	public void encodeConvert2StringTrue() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setConvert2String(true);
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,input, session);
		String result = prr.getResult().asString();
		assertEquals(output, result.trim());
	}

	@Test
	public void encodeConvert2StringFalse() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setConvert2String(false);
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, input.getBytes(), session);
		String result = prr.getResult().asString();
		assertEquals(output, result.trim());
	}

	@Test
	public void decodeConvert2StringTrue() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setConvert2String(true);
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,output, session);
		String result = prr.getResult().asString();
		assertEquals(input, result.trim());
	}

	@Test
	public void decodeConvert2StringFalse() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setConvert2String(false);
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,output, session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(input, new String(result).trim());
	}

	//String input encode
	@Test
	public void inputStringOutputString() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("string");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,input, session);
		String result = prr.getResult().asString();
		assertEquals(output, result.trim());
	}

	@Test
	public void inputStringOutputBytes() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("bytes");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,input, session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(output, new String(result).trim());
	}

	@Test
	public void inputStringOutputStream() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("stream");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,input, session);
		InputStream result = provideStreamForInput ? prr.getResult().asInputStream() : (InputStream)prr.getResult().asObject();
		assertEquals(output, (Misc.streamToString(result)).trim());
	}

	//String bytes encode
	@Test
	public void inputBytesOutputString() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("string");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, input.getBytes(), session);
		String result = prr.getResult().asString();
		assertEquals(output, result.trim());
	}

	@Test
	public void inputBytesOutputBytes() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("bytes");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, input.getBytes(), session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(output, new String(result).trim());
	}

	@Test
	public void inputBytesOutputStream() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("stream");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, input.getBytes(), session);
		InputStream result = provideStreamForInput ? prr.getResult().asInputStream() : (InputStream)prr.getResult().asObject();
		assertEquals(output, (Misc.streamToString(result)).trim());
	}

	//String stream encode
	@Test
	public void inputStreamOutputString() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("string");
		pipe.configure();
		pipe.start();

		InputStream stream = new ByteArrayInputStream(input.getBytes());
		PipeRunResult prr = doPipe(pipe, stream, session);
		String result = prr.getResult().asString();
		assertEquals(output, result.trim());
	}

	@Test
	public void inputStreamOutputBytes() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("bytes");
		pipe.configure();
		pipe.start();

		InputStream stream = new ByteArrayInputStream(input.getBytes());
		PipeRunResult prr = doPipe(pipe, stream, session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(output, new String(result).trim());
	}

	@Test
	public void inputStreamOutputStream() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("stream");
		pipe.configure();
		pipe.start();

		InputStream stream = new ByteArrayInputStream(input.getBytes());
		PipeRunResult prr = doPipe(pipe, stream, session);
		InputStream result = provideStreamForInput ? prr.getResult().asInputStream() : (InputStream)prr.getResult().asObject();
		assertEquals(output, (Misc.streamToString(result)).trim());
	}

	//String input decode
	@Test
	public void inputStringOutputStringDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("string");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,output, session);
		String result = prr.getResult().asString();
		assertEquals(input, result.trim());
	}

	@Test
	public void inputStringOutputBytesDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("bytes");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,output, session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(input, new String(result).trim());
	}

	@Test
	public void inputStringOutputStreamDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("stream");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe,output, session);
		InputStream result = provideStreamForInput ? prr.getResult().asInputStream() : (InputStream)prr.getResult().asObject();
		assertEquals(input, (Misc.streamToString(result)).trim());
	}

	//String bytes decode
	@Test
	public void inputBytesOutputStringDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("string");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, output.getBytes(), session);
		String result = prr.getResult().asString();
		assertEquals(input, result.trim());
	}

	@Test
	public void inputBytesOutputBytesDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("bytes");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, output.getBytes(), session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(input, new String(result).trim());
	}

	@Test
	public void inputBytesOutputStreamDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("stream");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		PipeRunResult prr = doPipe(pipe, output.getBytes(), session);
		InputStream result = provideStreamForInput ? prr.getResult().asInputStream() : (InputStream)prr.getResult().asObject();
		assertEquals(input, (Misc.streamToString(result)).trim());
	}

	//String stream decode
	@Test
	public void inputStreamOutputStringDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("string");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		InputStream stream = new ByteArrayInputStream(output.getBytes());
		PipeRunResult prr = doPipe(pipe, stream, session);
		String result = prr.getResult().asString();
		assertEquals(input, result.trim());
	}

	@Test
	public void inputStreamOutputBytesDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("bytes");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		InputStream stream = new ByteArrayInputStream(output.getBytes());
		PipeRunResult prr = doPipe(pipe, stream, session);
		byte[] result = (byte[]) prr.getResult().asObject();
		assertEquals(input, new String(result).trim());
	}

	@Test
	public void inputStreamOutputStreamDecode() throws ConfigurationException, PipeStartException, IOException, PipeRunException {
		pipe.setOutputType("stream");
		pipe.setDirection("decode");
		pipe.configure();
		pipe.start();

		InputStream stream = new ByteArrayInputStream(output.getBytes());
		PipeRunResult prr = doPipe(pipe, stream, session);
		InputStream result = provideStreamForInput ? prr.getResult().asInputStream() : (InputStream)prr.getResult().asObject();
		assertEquals(input, (Misc.streamToString(result)).trim());
	}
}