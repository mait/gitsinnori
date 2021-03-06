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

package kr.pe.sinnori.server.threadpool.inputmessage;

import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;

import kr.pe.sinnori.common.exception.NoMoreDataPacketBufferException;
import kr.pe.sinnori.common.io.MessageExchangeProtocolIF;
import kr.pe.sinnori.common.lib.CommonProjectInfo;
import kr.pe.sinnori.common.lib.DataPacketBufferQueueManagerIF;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.threadpool.AbstractThreadPool;
import kr.pe.sinnori.server.ClientResourceManagerIF;
import kr.pe.sinnori.server.io.LetterFromClient;
import kr.pe.sinnori.server.threadpool.inputmessage.handler.InputMessageReader;
import kr.pe.sinnori.server.threadpool.inputmessage.handler.InputMessageReaderIF;

/**
 * 입력 메시지 소켓 읽기 담당 쓰레드 폴
 * 
 * @author Jonghoon Won
 * 
 */
public class InputMessageReaderPool extends AbstractThreadPool implements
		InputMessageReaderPoolIF {
	private int maxHandler;
	private long readSelectorWakeupInterval;
	private CommonProjectInfo commonProjectInfo;
	private LinkedBlockingQueue<LetterFromClient> inputMessageQueue;
	private MessageExchangeProtocolIF messageProtocol;
	private MessageMangerIF messageManger;
	private DataPacketBufferQueueManagerIF dataPacketBufferQueueManager;
	private ClientResourceManagerIF clientResourceManager;
	
	
	/**
	 * 생성자
	 * @param size 입력 메시지 소켓 읽기 담당 쓰레드 초기 크기
	 * @param max 입력 메시지 소켓 읽기 담당 쓰레드 최대 크기
	 * @param readSelectorWakeupInterval 입력 메시지 소켓 읽기 담당 쓰레드에서 블락된 읽기 이벤트 전용 selector 를 깨우는 주기
	 * @param commonProjectInfo 공통 연결 데이터
	 * @param inputMessageQueue 입력 메시지 큐
	 * @param messageProtocol 메시지 교환 프로토콜
	 * @param messageManger 메시지 관리자
	 * @param dataPacketBufferQueueManager 데이터 패킷 버퍼 큐 관리자
	 * @param clientResourceManager 클라이언트 자원 관리자
	 */
	public InputMessageReaderPool(int size, int max,
			long readSelectorWakeupInterval,  
			CommonProjectInfo commonProjectInfo, 
			LinkedBlockingQueue<LetterFromClient> inputMessageQueue,
			MessageExchangeProtocolIF messageProtocol,
			MessageMangerIF messageManger,
			DataPacketBufferQueueManagerIF dataPacketBufferQueueManager,
			ClientResourceManagerIF clientResourceManager) {
		if (size <= 0) {
			throw new IllegalArgumentException("파라미터 초기 핸들러 갯수는 0보다 커야 합니다.");
		}
		if (max <= 0) {
			throw new IllegalArgumentException("파라미터 최대 핸들러 갯수는 0보다 커야 합니다.");
		}

		if (size > max) {
			throw new IllegalArgumentException(String.format(
					"파라미터 초기 핸들러 갯수[%d]는 최대 핸들러 갯수[%d]보다 작거나 같아야 합니다.", size,
					max));
		}

		this.maxHandler = max;
		this.readSelectorWakeupInterval = readSelectorWakeupInterval;
		this.commonProjectInfo = commonProjectInfo;
		this.inputMessageQueue = inputMessageQueue;
		this.messageProtocol = messageProtocol;
		this.messageManger = messageManger;
		this.dataPacketBufferQueueManager = dataPacketBufferQueueManager;
		this.clientResourceManager = clientResourceManager;
		

		for (int i = 0; i < size; i++) {
			addHandler();
		}
	}

	@Override
	public void addHandler() {
		synchronized (monitor) {
			int size = pool.size();

			if (size < maxHandler) {
				try {
					Thread handler = new InputMessageReader(size, readSelectorWakeupInterval, commonProjectInfo,
							inputMessageQueue, messageProtocol, messageManger, 
							dataPacketBufferQueueManager, clientResourceManager);
					pool.add(handler);
				} catch (Exception e) {
					log.warn("handler 등록 실패", e);
				}
			} else {
				throw new RuntimeException(String.format(
						"최대 핸들러 갯수[%d]를 초과할수없습니다.", maxHandler));
			}
		}
	}

	@Override
	public void addNewClient(SocketChannel sc) throws InterruptedException,
	NoMoreDataPacketBufferException {
		InputMessageReaderIF minHandler = null;
		int MIN_COUNT = Integer.MAX_VALUE;

		int size = pool.size();
		for (int i = 0; i < size; i++) {
			InputMessageReaderIF handler = (InputMessageReaderIF) pool.get(i);
			int cnt_of_clients = handler.getCntOfClients();
			if (cnt_of_clients < MIN_COUNT) {
				MIN_COUNT = cnt_of_clients;
				minHandler = handler;
			}
		}
		minHandler.addClient(sc); // 마지막으로 ReqeustHandler에 등록
	}
}
