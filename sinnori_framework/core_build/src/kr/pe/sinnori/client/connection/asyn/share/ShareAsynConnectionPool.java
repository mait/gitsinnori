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
package kr.pe.sinnori.client.connection.asyn.share;

import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import kr.pe.sinnori.client.connection.AbstractConnectionPool;
import kr.pe.sinnori.client.connection.asyn.threadpool.outputmessage.OutputMessageReaderPoolIF;
import kr.pe.sinnori.client.io.LetterFromServer;
import kr.pe.sinnori.client.io.LetterToServer;
import kr.pe.sinnori.common.exception.BodyFormatException;
import kr.pe.sinnori.common.exception.MessageInfoNotFoundException;
import kr.pe.sinnori.common.exception.NoMoreDataPacketBufferException;
import kr.pe.sinnori.common.exception.NoMoreOutputMessageQueueException;
import kr.pe.sinnori.common.exception.ServerNotReadyException;
import kr.pe.sinnori.common.lib.CommonProjectInfo;
import kr.pe.sinnori.common.lib.DataPacketBufferQueueManagerIF;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.lib.OutputMessageQueueQueueMangerIF;
import kr.pe.sinnori.common.message.InputMessage;
import kr.pe.sinnori.common.message.OutputMessage;

/**
 * 클라이언트 공유 방식의 비동기 연결 클래스 {@link ShareAsynConnection} 를 원소로 가지는 폴 관리자 클래스<br/>
 * 다른 쓰레드간에 연결 클래스를 공유하기 위해서 목록으로 관리되며 순차적으로 순환 할당한다.
 * 
 * @author Jonghoon Won
 * 
 */
public class ShareAsynConnectionPool extends AbstractConnectionPool {
	/**
	 * 공유방식으로 비동기 방식의 소켓 채널을 소유한 연결 클래스 목록. 쓰레드 공유하기 위해서 순차적으로 할당한다.
	 */
	private ArrayList<ShareAsynConnection> connectionList = null;
	/** 순차적으로 할당하기 위해서 목록내에 반환할 연결 클래스를 가르키는 인덱스 */
	private int indexOfConnection = 0;
	private int mailBoxCnt = 0;
	
	
	/**
	 * 생성자
	 * @param connectionPoolSize 연결 폴 크기
	 * @param socketTimeOut 소켓 타임 아웃
	 * @param whetherToAutoConnect 자동 연결 여부
	 * @param finishConnectMaxCall 비동기 연결 확립 시도 최대 횟수
	 * @param finishConnectWaittingTime 비동기 연결 확립 시도 간격
	 * @param mailBoxCnt 메일함 갯수
	 * @param commonProjectInfo 공통 프로젝트 정보
	 * @param serverOutputMessageQueue 서버에서 보내는 불특정 출력 메시지를 받는 큐
	 * @param inputMessageQueue 입력 메시지 큐
	 * @param outputMessageQueueQueueManager 출력 메시지 큐를 원소로 가지는 큐 관리자
	 * @param outputMessageReaderPool 서버에 접속한 소켓 채널을 균등하게 소켓 읽기 담당 쓰레드에 등록하기 위한 인터페이스
	 * @param messageManger 메시지 관리자
	 * @param dataPacketBufferQueueManager 데이터 패킷 버퍼 큐 관리자
	 * @throws NoMoreDataPacketBufferException 데이터 패킷 버퍼를 할당 받지 못했을 경우 던지는 예외
	 * @throws InterruptedException 쓰레드 인터럽트
	 * @throws NoMoreOutputMessageQueueException 출력 메시지 큐 부족시 실패시 던지는 예외
	 */
	public ShareAsynConnectionPool(
			int connectionPoolSize,
			long socketTimeOut,
			boolean whetherToAutoConnect,
			int finishConnectMaxCall,
			long finishConnectWaittingTime,
			int mailBoxCnt,			
			CommonProjectInfo commonProjectInfo,
			LinkedBlockingQueue<OutputMessage> serverOutputMessageQueue,
			LinkedBlockingQueue<LetterToServer> inputMessageQueue,
			OutputMessageQueueQueueMangerIF outputMessageQueueQueueManager, 
			OutputMessageReaderPoolIF outputMessageReaderPool,
			MessageMangerIF messageManger,
			DataPacketBufferQueueManagerIF dataPacketBufferQueueManager)
			throws NoMoreDataPacketBufferException, InterruptedException, NoMoreOutputMessageQueueException {
		super(commonProjectInfo, serverOutputMessageQueue);
		// log.info("create new MultiNoneBlockConnectionPool");
		
		
		this.mailBoxCnt = mailBoxCnt;
		
		
		connectionList = new ArrayList<ShareAsynConnection>(
				connectionPoolSize);

		for (int i = 0; i < connectionPoolSize; i++) {
			ShareAsynConnection serverConnection = null;
			
			serverConnection = new ShareAsynConnection(i, 
					socketTimeOut,
					whetherToAutoConnect,
					finishConnectMaxCall,
					finishConnectWaittingTime,
					mailBoxCnt,
					commonProjectInfo, 
					serverOutputMessageQueue, inputMessageQueue,					 
					outputMessageQueueQueueManager,
					outputMessageReaderPool, messageManger,
					dataPacketBufferQueueManager);

			connectionList.add(serverConnection);

		}

		// log.info("connectionList size=[%d]", connectionList.size());

	}
	
	
	@Override
	public int getUsedMailboxCnt() {
		int connectionPoolSize = connectionList.size();
		
		int usedMailboxCnt = 0;
		for (int i = 0; i < connectionPoolSize; i++) {
			ShareAsynConnection shareAsynConnection = connectionList.get(i);
			// shareAsynConnection.getSocketChannel();
			usedMailboxCnt += shareAsynConnection.getUsedMailboxCnt();
		}
		return usedMailboxCnt;
	}

	@Override
	public int getTotalMailbox() {
		int connectionPoolSize = connectionList.size();
		int totalClients = mailBoxCnt * connectionPoolSize;
		return totalClients;
	}
	
	@Override
	public LetterFromServer sendInputMessage(InputMessage inputMessage)
			throws ServerNotReadyException, SocketTimeoutException,
			NoMoreDataPacketBufferException, BodyFormatException, MessageInfoNotFoundException {
		ShareAsynConnection serverConnection = null;

		synchronized (monitor) {
			serverConnection = connectionList.get(indexOfConnection);
			indexOfConnection = (indexOfConnection + 1) % connectionList.size();
		}
		return serverConnection.sendInputMessage(inputMessage);
	}
}
