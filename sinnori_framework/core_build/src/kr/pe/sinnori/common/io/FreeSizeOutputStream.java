/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package kr.pe.sinnori.common.io;

import java.nio.BufferOverflowException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.nio.charset.CharsetEncoder;
import java.util.ArrayList;
import java.util.Arrays;

import kr.pe.sinnori.common.exception.NoMoreDataPacketBufferException;
import kr.pe.sinnori.common.lib.CommonRootIF;
import kr.pe.sinnori.common.lib.CommonStaticFinal;
import kr.pe.sinnori.common.lib.DataPacketBufferQueueManagerIF;
import kr.pe.sinnori.common.lib.WrapBuffer;

/**
 * 가변 크기를 갖는 출력 이진 스트림<br/>
 * 참고) 스트림은 데이터 패킷 버퍼 큐 관리자가 관리하는 랩 버퍼들로 구현된다. 
 * @author Jonghoon Won
 *
 */
public final class FreeSizeOutputStream implements CommonRootIF, OutputStreamIF {
	private ArrayList<WrapBuffer> dataPacketBufferList = null;
	private ByteOrder streamByteOrder = null;
	private Charset streamCharset = null;
	private CharsetEncoder streamCharsetEncoder = null;
	private int dataPacketBufferMaxCntPerMessage;
	private DataPacketBufferQueueManagerIF dataPacketBufferQueueManager = null;
	
	private byte shortBytes[] = { CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE };
	private byte intBytes[] = { CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE, CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE };
	private byte longBytes[] = { CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE, CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE, CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE, CommonStaticFinal.ZERO_BYTE,
			CommonStaticFinal.ZERO_BYTE };

	private ByteBuffer shortBuffer = null;
	private ByteBuffer intBuffer = null;
	private ByteBuffer longBuffer = null;
	private ByteBuffer workBuffer = null;

	/**
	 * 생성자
	 * @param streamByteOrder 바이트 오더
	 * @param streamCharset 문자셋 
	 * @param dataPacketBufferQueueManager 데이터 패킷 버퍼 관리자
	 * @throws NoMoreDataPacketBufferException 데이터 패키 버퍼를 확보하지 못했을 경우 던지는 예외
	 */
	public FreeSizeOutputStream(ByteOrder streamByteOrder, Charset streamCharset, CharsetEncoder streamCharsetEncoder, DataPacketBufferQueueManagerIF dataPacketBufferQueueManager) throws NoMoreDataPacketBufferException {
		this(streamByteOrder, streamCharset, streamCharsetEncoder, 0, dataPacketBufferQueueManager);
	}
	
	/**
	 * 생성자 
	 * @param streamByteOrder
	 * @param streamCharset
	 * @param streamCharsetEncoder
	 * @param firtBufferPosition
	 * @param dataPacketBufferQueueManager
	 * @throws NoMoreDataPacketBufferException
	 */
	public FreeSizeOutputStream(ByteOrder streamByteOrder, Charset streamCharset, CharsetEncoder streamCharsetEncoder, int firtBufferPosition, DataPacketBufferQueueManagerIF dataPacketBufferQueueManager) throws NoMoreDataPacketBufferException {
		this.streamByteOrder = streamByteOrder;
		this.streamCharset = streamCharset;
		this.streamCharsetEncoder = streamCharsetEncoder;
		this.dataPacketBufferQueueManager = dataPacketBufferQueueManager;
		
		dataPacketBufferMaxCntPerMessage = dataPacketBufferQueueManager.getDataPacketBufferMaxCntPerMessage();
		
		dataPacketBufferList = new ArrayList<WrapBuffer>();
		WrapBuffer wrapBuffer = dataPacketBufferQueueManager.pollDataPacketBuffer(streamByteOrder);
		workBuffer = wrapBuffer.getByteBuffer();
		workBuffer.position(firtBufferPosition);
		dataPacketBufferList.add(wrapBuffer);
		
		
		shortBuffer = ByteBuffer.allocate(2);
		shortBuffer.order(streamByteOrder);

		intBuffer = ByteBuffer.allocate(4);
		intBuffer.order(streamByteOrder);

		longBuffer = ByteBuffer.allocate(8);
		longBuffer.order(streamByteOrder);
	}
	
	/**
	 * unsigned byte 쓰기를 위한 short 버퍼 초기화
	 */
	private void clearShortBuffer() {
		shortBuffer.clear();
		Arrays.fill(shortBytes, CommonStaticFinal.ZERO_BYTE);
	}

	/**
	 * unsigned short 쓰기를 위한 integer 버퍼 초기화
	 */
	private void clearIntBuffer() {
		intBuffer.clear();
		Arrays.fill(intBytes, CommonStaticFinal.ZERO_BYTE);
	}
	
	/**
	 * 랩 버퍼 확보 실패시 이전에 등록한 랩 버퍼 목록을 해제해 주는 메소드
	 */
	private void freeDataPacketBufferList() {
		if (null != dataPacketBufferList) {
			int bodyBufferSize = dataPacketBufferList.size();
			for (int i = 0; i < bodyBufferSize; i++) {
				dataPacketBufferQueueManager.putDataPacketBuffer(dataPacketBufferList.remove(0));
			}
		}
	}

	/**
	 * 스트림에 랩 버퍼를 추가하여 확장한다.
	 * @throws NoMoreDataPacketBufferException 데이터 패킷 버퍼 확보 실패시 던지는 예외
	 */
	private void addBuffer() throws NoMoreDataPacketBufferException  {
		/** 남은 용량 없이 꽉 차서 들어와야 한다. */
		if (workBuffer.hasRemaining()) {
			String errorMessage = "작업 버퍼에 남은 용량이 있습니다.";
			log.warn(errorMessage);
			System.exit(1);
		}

		if (dataPacketBufferList.size() == dataPacketBufferMaxCntPerMessage) {
			log.warn(String.format("메시지당 최대 버퍼수[%d]에 도달하여 신규 데이터 패킷 버퍼를 추가할 수 없습니다.", dataPacketBufferMaxCntPerMessage));
			throw new BufferOverflowException();
		}

		/** 새로운 바디 버퍼 받아 오기 */
		WrapBuffer newWrapBuffer = null;
		try {
			newWrapBuffer = dataPacketBufferQueueManager.pollDataPacketBuffer(streamByteOrder);
		} catch (NoMoreDataPacketBufferException e) {
			freeDataPacketBufferList();
			throw e;
		}

		workBuffer = newWrapBuffer.getByteBuffer();
		dataPacketBufferList.add(newWrapBuffer);
	}
	
	/**
	 * unsigned integer 쓰기를 위한 long 버퍼 초기화
	 */
	private void clearLongBuffer() {
		longBuffer.clear();
		Arrays.fill(longBytes, CommonStaticFinal.ZERO_BYTE);
	}
	
	/**
	 * unsigned short 을 위한 integer 버퍼에 값을 저장훈 Integer 버퍼를 반환한다.
	 * 
	 * @param value
	 *            unsigned short 타입에 대응하는 값
	 * @return 값이 저장된 unsigned short 을 위한 integer 버퍼
	 * @throws IllegalArgumentException
	 *             잘못된 파라미터를 입력하여 발생한다.
	 */
	private ByteBuffer getIntegerBufferForUnsignedShort(int value)
			throws IllegalArgumentException {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 음수입니다.", value));
		}
		/*
		if (value > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 unsigned short 최대값[%d]을 넘을 수 없습니다.", value,
					CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		clearIntBuffer();
		intBuffer.putInt(value);

		if (ByteOrder.BIG_ENDIAN == streamByteOrder) {
			intBuffer.position(2);
			intBuffer.limit(4);
		} else {
			intBuffer.position(0);
			intBuffer.limit(2);
		}
		return intBuffer;
	}
	/**
	 * unsigned integer 를 위한 long 버퍼에 값을 저장후 long 버퍼를 반환한다.
	 * 
	 * @param value
	 *            unsigned integer 타입에 대응하는 값
	 * @return 값이 저장된 unsigned integer 를 위한 long 버퍼
	 * @throws IllegalArgumentException
	 *             잘못된 파라미터를 입력하여 발생한다.
	 */
	private ByteBuffer getLongBufferForUnsignedInt(long value)
			throws IllegalArgumentException {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 음수입니다.", value));
		}
		if (value > CommonStaticFinal.MAX_UNSIGNED_INT) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 unsigned int 최대값[%d]을 넘을 수 없습니다.", value,
					CommonStaticFinal.MAX_UNSIGNED_INT));
		}
		clearLongBuffer();
		longBuffer.putLong(value);

		if (ByteOrder.BIG_ENDIAN == streamByteOrder) {
			longBuffer.position(4);
			longBuffer.limit(8);
		} else {
			longBuffer.position(0);
			longBuffer.limit(4);
		}
		return longBuffer;
	}
	
	/**
	 * 스트림을 확장해가면서 스트림에 소스 바이트 버퍼의 내용을 저장한다. 단 스트림은 최대 크기 제약이 있다.
	 * 
	 * @param srcBuffer
	 *            소스 바이트 버퍼
	 * @throws BufferOverflowException
	 *             버퍼 크기를 벗어난 쓰기 시도시 발생
	 * @throws IllegalArgumentException
	 *             잘못된 파라미터를 입력하여 발생한다.
	 * @throws NoMoreDataPacketBufferException
	 *             스트림 확장을 위한 랩 버퍼 확보 실패시 발생
	 */
	private void putBytesToWorkBuffer(ByteBuffer srcBuffer)
			throws BufferOverflowException, IllegalArgumentException,
			NoMoreDataPacketBufferException {
		do {
			try {
				workBuffer.put(srcBuffer);
			} catch (BufferOverflowException e) {
				int newLimit = srcBuffer.position() + workBuffer.remaining();
				srcBuffer.limit(newLimit);
				workBuffer.put(srcBuffer);
				srcBuffer.limit(srcBuffer.capacity());
			}

			if (!srcBuffer.hasRemaining())
				break;

			addBuffer();
		} while (true);
	}
	
	/** 
	 * @return 출력 스트림 쓰기 과정 결과 물이 담긴 버퍼 목록, 모든 버퍼는 읽기 가능 상태 flip 되어진다.
	 */
	public ArrayList<WrapBuffer> getDataPacketBufferList() {
		int bufferSize = dataPacketBufferList.size();
		for (int i = 0; i < bufferSize; i++) {
			dataPacketBufferList.get(i).getByteBuffer().flip();
		}
		return dataPacketBufferList;
	}
	
	/** 
	 * @return flip 없는 출력 스트림 쓰기 과정 결과 물이 담긴 버퍼 목록
	 */
	public ArrayList<WrapBuffer> getNoFlipDataPacketBufferList() {
		return dataPacketBufferList;
	}
	
	@Override
	public Charset getCharset() {
		return streamCharset;
	}
	
	@Override
	public void putByte(byte value) throws BufferOverflowException,
			NoMoreDataPacketBufferException {
		try {
			workBuffer.put(value);
		} catch (BufferOverflowException e) {
			addBuffer();
			workBuffer.put(value);
		}
	}

	@Override
	public void putUnsignedByte(short value) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]이 음수입니다.", value));
		}

		if (value > CommonStaticFinal.MAX_UNSIGNED_BYTE) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 unsigned byte 최대값[%d]을 넘을 수 없습니다.", value,
					CommonStaticFinal.MAX_UNSIGNED_BYTE));
		}

		/**
		 * 우분투 jdk 1.6.x에서 테스트한 시스템 디폴트 ByteOrder는 LITTLE_ENDIAN 로 정수(=Integer)
		 * 0xff 를 byte 형 변환하면 0xff 이다.
		 */
		putByte((byte) value);
		
	}
	
	@Override
	public void putUnsignedByte(int value) throws BufferOverflowException, IllegalArgumentException, NoMoreDataPacketBufferException {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]이 음수입니다.", value));
		}
		
		if (value > CommonStaticFinal.MAX_UNSIGNED_BYTE) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 unsigned byte 최대값[%d]을 넘을 수 없습니다.", value,
					CommonStaticFinal.MAX_UNSIGNED_BYTE));
		}
		
		/**
		 * 우분투 jdk 1.6.x에서 테스트한 시스템 디폴트 ByteOrder는 LITTLE_ENDIAN 로 정수(=Integer)
		 * 0xff 를 byte 형 변환하면 0xff 이다.
		 */
		putByte((byte) value);
	}
	

	@Override
	public void putShort(short value) throws BufferOverflowException,
			NoMoreDataPacketBufferException {
		try {
			workBuffer.putShort(value);
		} catch (BufferOverflowException e) {
			clearShortBuffer();
			shortBuffer.putShort(value);
			shortBuffer.position(0);
			shortBuffer.limit(workBuffer.remaining());
			workBuffer.put(shortBuffer);
			shortBuffer.limit(shortBuffer.capacity());
			addBuffer();
			workBuffer.put(shortBuffer);
		}
	}

	@Override
	public void putUnsignedShort(int value) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]이 음수입니다.", value));
		}

		if (value > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 unsigned short 최대값[%d]을 넘을 수 없습니다.", value,
					CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		
		ByteBuffer integerBuffer = getIntegerBufferForUnsignedShort(value);
		// log.info("1. unsingedShortBuffer=[%s]",
		// unsingedShortBuffer.toString());
		// log.info("1. workBuffer=[%s]", workBuffer.toString());

		try {
			workBuffer.put(integerBuffer);
		} catch (BufferOverflowException e) {
			int limit = integerBuffer.limit();
			integerBuffer.limit(integerBuffer.position()+workBuffer.remaining());
			workBuffer.put(integerBuffer);
			integerBuffer.limit(limit);

			addBuffer();
			workBuffer.put(integerBuffer);
		}
	}

	@Override
	public void putInt(int value) throws BufferOverflowException,
			NoMoreDataPacketBufferException {
		try {
			workBuffer.putInt(value);
		} catch (BufferOverflowException e) {
			clearIntBuffer();
			intBuffer.putInt(value);
			intBuffer.position(0);
			intBuffer.limit(workBuffer.remaining());
			workBuffer.put(intBuffer);
			intBuffer.limit(intBuffer.capacity());

			addBuffer();
			workBuffer.put(intBuffer);
		}
		
	}
	
	
	@Override
	public void putUnsignedInt(long value) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (value < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]이 음수입니다.", value));
		}

		if (value > CommonStaticFinal.MAX_UNSIGNED_INT) {
			throw new IllegalArgumentException(String.format(
					"파라미터 값[%d]은 unsigned integer 최대값[%d]을 넘을 수 없습니다.", value,
					CommonStaticFinal.MAX_UNSIGNED_INT));
		}
		
		ByteBuffer longBuffer = getLongBufferForUnsignedInt(value);
		try {
			workBuffer.put(longBuffer);
		} catch (BufferOverflowException e) {
			int limit = longBuffer.limit();
			longBuffer.limit(longBuffer.position() + workBuffer.remaining());
			workBuffer.put(longBuffer);
			longBuffer.limit(limit);

			addBuffer();
			workBuffer.put(longBuffer);
		}
	}

	@Override
	public void putLong(long value) throws BufferOverflowException,
			NoMoreDataPacketBufferException {
		try {
			workBuffer.putLong(value);
		} catch (BufferOverflowException e) {
			clearLongBuffer();
			longBuffer.putLong(value);
			longBuffer.position(0);
			longBuffer.limit(workBuffer.remaining());
			workBuffer.put(longBuffer);
			longBuffer.limit(longBuffer.capacity());
			
			addBuffer();
			workBuffer.put(longBuffer);
		}
	}

	@Override
	public void putString(int len, String str,
			CharsetEncoder wantedCharsetEncoder)
			throws BufferOverflowException, IllegalArgumentException,
			NoMoreDataPacketBufferException{
		if (len < 0) {
			throw new IllegalArgumentException(String.format(
					"파리미터 문자열 길이(=len)의 값[%d]은  0 보다 크거나 같아야 합니다.", len));
		}

		/*
		if (len > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(
					String.format(
							"파리미터 문자열 길이(=len)의 값[%d]은  unsigned short 최대값[%d] 보다 작거나 같아야 합니다.",
							len, CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		long remainingBytes = remaining();
		if (len > remainingBytes) {
			throw new IllegalArgumentException(String.format(
					"파라미터 길이[%d]는 남아 있은 버퍼 크기[%d] 보다 작거나 같아야 합니다.", len,
					remainingBytes));
		}

		if (str == null) {
			throw new IllegalArgumentException("파리미터 문자열(=str)의 값이 null 입니다.");
		}

		if (wantedCharsetEncoder == null) {
			throw new IllegalArgumentException(
					"파리미터 문자셋(=clientEncoder)의 값이 null 입니다.");
		}
		
		CharBuffer strCharBuffer = CharBuffer.wrap(str);

		ByteBuffer strByteBuffer = ByteBuffer.allocate(len);
		
		
		Arrays.fill(strByteBuffer.array(), CommonStaticFinal.ZERO_BYTE);
		wantedCharsetEncoder.encode(strCharBuffer, strByteBuffer, true);
		// log.info("strBufer=[%s]", strBufer.toString());
		strByteBuffer.rewind();

		putBytesToWorkBuffer(strByteBuffer);
		
	}

	@Override
	public void putString(int len, String str) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		putString(len, str, streamCharsetEncoder);
	}

	@Override
	public void putStringAll(String str) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (str == null) {
			throw new IllegalArgumentException("파리미터 문자열(=str)의 값이 null 입니다.");
		}

		byte strBytes[] = str.getBytes(streamCharset);
		/*
		int len = strBytes.length;
		if (len > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(
					String.format(
							"파리미터 문자열 길이(=len)의 값[%d]은  unsigned short 최대값[%d] 보다 작거나 같아야 합니다.",
							len, CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		putBytes(strBytes);
	}

	@Override
	public void putPascalString(String str) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		putUBPascalString(str);
	}

	@Override
	public void putSIPascalString(String str) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (str == null) {
			throw new IllegalArgumentException("파리미터 문자열(=str)의 값이 null 입니다.");
		}

		byte strBytes[] = str.getBytes(streamCharset);

		/*
		if (strBytes.length > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(
					String.format(
							"파리미터 문자열 길이(=len)의 값[%d]은  unsigned short 최대값[%d] 보다 작거나 같아야 합니다.",
							strBytes.length,
							CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		putInt(strBytes.length);
		putBytes(strBytes);
	}

	@Override
	public void putUSPascalString(String str) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (str == null) {
			throw new IllegalArgumentException("파리미터 문자열(=str)의 값이 null 입니다.");
		}

		byte strBytes[] = str.getBytes(streamCharset);
		/*
		if (strBytes.length > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(String.format(
					"파라미터 문자열의 길이[%d]는 unsigned short 최대값[%d]을 넘을 수 없습니다.", strBytes.length, CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/
		
		putUnsignedShort(strBytes.length);
		putBytes(strBytes);
	}

	@Override
	public void putUBPascalString(String str) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (str == null) {
			throw new IllegalArgumentException("파리미터 문자열(=str)의 값이 null 입니다.");
		}

		byte strBytes[] = str.getBytes(streamCharset);
		/*
		if (strBytes.length > CommonStaticFinal.MAX_UNSIGNED_BYTE) {
			throw new IllegalArgumentException(String.format(
					"파라미터 문자열의 길이[%d]는 unsigned byte 최대값[%d]을 넘을 수 없습니다.", strBytes.length, CommonStaticFinal.MAX_UNSIGNED_BYTE));
		}
		*/
		
		putUnsignedByte(strBytes.length);
		putBytes(strBytes);
		
	}

	@Override
	public void putBytes(byte[] srcBuffer, int offset, int length)
			throws BufferOverflowException, IllegalArgumentException,
			NoMoreDataPacketBufferException {
		if (srcBuffer == null) {
			throw new IllegalArgumentException(
					"파리미터 소스 바이트 배열(=srcBuffer)의 값이 null 입니다.");
		}

		if (offset < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 옵셋[%d]은  0 보다 커야 합니다.", offset));
		}

		if (length < 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 소스 바이트 배열 길이[%d]는  0 보다 커야 합니다.", length));
		}
		/*
		if (length > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(
					String.format(
							"파리미터 소스 바이트 배열 길이[%d]는  unsigned short 최대값[%d] 보다 작거나 같아야 합니다.",
							length, CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		long remainingBytes = remaining();
		if (length > remainingBytes) {
			throw new IllegalArgumentException(String.format(
					"파라미터 소스 바이트 배열 길이[%d]는 남아 있은 버퍼 크기[%d] 보다 작거나 같아야 합니다.",
					length, remainingBytes));
		}

		ByteBuffer srcByteBuffer = ByteBuffer
				.wrap(srcBuffer, offset, length);

		putBytesToWorkBuffer(srcByteBuffer);
	}

	@Override
	public void putBytes(byte[] srcBuffer) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (srcBuffer == null) {
			throw new IllegalArgumentException(
					"파리미터 소스 바이트 배열(=srcBuffer)의 값이 null 입니다.");
		}
		/*
		if (srcBuffer.length > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(
					String.format(
							"파리미터 소스 바이트 배열 길이[%d]는  unsigned short 최대값[%d] 보다 작거나 같아야 합니다.",
							srcBuffer.length,
							CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		ByteBuffer srcByteBuffer = ByteBuffer.wrap(srcBuffer);

		putBytesToWorkBuffer(srcByteBuffer);
		
	}

	@Override
	public void putBytes(ByteBuffer srcBuffer) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (srcBuffer == null) {
			throw new IllegalArgumentException("파리미터 소스 버퍼의 값이 null 입니다.");
		}

		/*
		int remainingBytes = srcBuffer.remaining();
		if (remainingBytes > CommonStaticFinal.MAX_UNSIGNED_SHORT) {
			throw new IllegalArgumentException(
					String.format(
							"파리미터 바이트 버퍼의 길이[%d]는 unsigned short 최대값[%d] 보다 작거나 같아야 합니다.",
							remainingBytes,
							CommonStaticFinal.MAX_UNSIGNED_SHORT));
		}
		*/

		/**
		 * slice 메소드를 통해 원본 버퍼의 속성과 별개의 속성을 갖는 <br/>
		 * 그리고 실제 필요한 영역만을 가지는 바이트 버퍼를 만든다.
		 */
		ByteBuffer sliceBuffer = srcBuffer.slice();

		putBytesToWorkBuffer(sliceBuffer);
		
	}

	@Override
	public void skip(int skipBytes) throws BufferOverflowException,
			IllegalArgumentException, NoMoreDataPacketBufferException {
		if (skipBytes <= 0) {
			throw new IllegalArgumentException(String.format(
					"파라미터 생략할 크기[%d]는 0보다 커야 합니다.", skipBytes));
		}

		if (skipBytes >= CommonStaticFinal.MAX_UNSIGNED_BYTE) {
			throw new IllegalArgumentException(String.format(
					"파라미터 생략할 크기[%d]는 unsinged byte 최대값[%d]보다 작어야 합니다.",
					skipBytes, CommonStaticFinal.MAX_UNSIGNED_BYTE));
		}
		
		int dstRemainingByte = skipBytes;
		do {
			int workRemainingByte = workBuffer.remaining();
			if (dstRemainingByte < workRemainingByte) {
				workBuffer.position(workBuffer.position() + dstRemainingByte);
				dstRemainingByte = 0;
				break;
			}

			workBuffer.position(workBuffer.limit());
			dstRemainingByte -= workRemainingByte;
			if (dstRemainingByte <= 0)
				break;
			addBuffer();
		} while (true);
	}

	@Override
	public long remaining() {
		long remainingBufferCount = dataPacketBufferMaxCntPerMessage - dataPacketBufferList.size();
		long remainingByte = workBuffer.capacity()*remainingBufferCount
				+ workBuffer.remaining();
		return remainingByte;
	}
	
	@Override
	public long postion() {
		int dataPacketBufferListSize = dataPacketBufferList.size();
		
		if (0 == dataPacketBufferListSize) return 0;
		
		long position = (dataPacketBufferListSize - 1)*workBuffer.capacity()+workBuffer.position();
		
		return position;
	}

}
