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

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetSocketAddress;
import java.net.SocketTimeoutException;
import java.net.StandardSocketOptions;
import java.net.UnknownHostException;
import java.nio.channels.ClosedChannelException;
import java.nio.channels.SocketChannel;
import java.util.Hashtable;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import kr.pe.sinnori.client.connection.asyn.AbstractAsynConnection;
import kr.pe.sinnori.client.connection.asyn.share.mailbox.PrivateMailbox;
import kr.pe.sinnori.client.connection.asyn.threadpool.outputmessage.OutputMessageReaderPoolIF;
import kr.pe.sinnori.client.io.LetterFromServer;
import kr.pe.sinnori.client.io.LetterToServer;
import kr.pe.sinnori.common.exception.BodyFormatException;
import kr.pe.sinnori.common.exception.MessageInfoNotFoundException;
import kr.pe.sinnori.common.exception.NoMoreDataPacketBufferException;
import kr.pe.sinnori.common.exception.NoMoreOutputMessageQueueException;
import kr.pe.sinnori.common.exception.ServerNotReadyException;
import kr.pe.sinnori.common.lib.CommonProjectInfo;
import kr.pe.sinnori.common.lib.CommonStaticFinal;
import kr.pe.sinnori.common.lib.DataPacketBufferQueueManagerIF;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.lib.OutputMessageQueueQueueMangerIF;
import kr.pe.sinnori.common.message.InputMessage;
import kr.pe.sinnori.common.message.OutputMessage;

/**
 * 클라이언트 공유 방식의 비동기 연결 클래스.<br/>
 * 참고) 공유 방식은 목록으로 관리되며 순차적으로 공유 방식의 비동기 연결 클래스를 배정한다. <br/>
 * 이렇게 배정 받은 공유 방식의 비동기 연결 클래스는 메시지 송수신 순간에 <br/> 
 * 메일함 큐에서 메일함을 할당받아 메일함을 통해서 메시지 교환을 수행한다.<br/>
 * 자세한 내용은 기술 문서를 참고 하세요.<br/>
 * 참고) 소켓 채널을 감싸아 소켓 채널관련 서비스를 구현하는 클래스, 즉 소켓 채널 랩 클래스를 연결 클래스로 명명한다.
 * 
 * @see PrivateMailbox
 * @author Jonghoon Won
 * 
 */
public class ShareAsynConnection extends AbstractAsynConnection {
	// private MessageMangerIF messageManger = null;
	
	/** 개인 메일함 큐 */
	private LinkedBlockingQueue<PrivateMailbox> PrivateMailboxWaitingQueue = null;
	/** 메일 식별자를 키로 활성화된 메일함을 값으로 가지는 해쉬 */
	private Map<Integer, PrivateMailbox> hashActiveMailBox = new Hashtable<Integer, PrivateMailbox>();
	
	/**
	 * 생성자
	 * @param index 연결 클래스 번호
	 * @param socketTimeOut 소켓 타임 아웃 시간
	 * @param whetherToAutoConnect 자동 접속 여부
	 * @param finishConnectMaxCall 비동기 방식에서 연결 확립 시도 최대 호출 횟수
	 * @param finishConnectWaittingTime 비동기 연결 확립 시도 간격
	 * @param mailBoxCnt 메일 박스 갯수
	 * @param commonProjectInfo 연결 공통 데이터 
	 * @param serverOutputMessageQueue 서버에서 보내는 불특정 출력 메시지를 받는 큐
	 * @param inputMessageQueue 입력 메시지 큐
	 * @param outputMessageQueueQueueManager 출력 메시지 큐를 원소로 가지는 큐 관리자
	 * @param outputMessageReaderPool 서버에 접속한 소켓 채널을 균등하게 소켓 읽기 담당 쓰레드에 등록하기 위한 인터페이스
	 * @param messageManger 메시지 관리자
	 * @param dataPacketBufferQueueManager 데이터 패킷 버퍼 큐 관리자
	 * @throws InterruptedException 쓰레드 인터럽트
	 * @throws NoMoreDataPacketBufferException  데이터 패킷 버퍼 부족시 실패시 던지는 예외
	 * @throws NoMoreOutputMessageQueueException 출력 메시지 큐 부족시 실패시 던지는 예외
	 */
	public ShareAsynConnection(int index, 
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
			DataPacketBufferQueueManagerIF dataPacketBufferQueueManager) throws InterruptedException, 
			NoMoreDataPacketBufferException, NoMoreOutputMessageQueueException {
		
		super(index, socketTimeOut, whetherToAutoConnect, 
				finishConnectMaxCall, finishConnectWaittingTime, commonProjectInfo, 
				serverOutputMessageQueue, inputMessageQueue,
				outputMessageReaderPool, dataPacketBufferQueueManager);
		
		log.info(String.format("create MultiNoneBlockConnection, projectName=[%s%02d], mailBoxCnt=[%d]",
				commonProjectInfo.projectName, index, mailBoxCnt));

		// this.messageManger = messageManger;
		PrivateMailboxWaitingQueue = new LinkedBlockingQueue<PrivateMailbox>(
				mailBoxCnt);
		
		boolean isInterrupted = false;

		/**
		 * <pre>
		 * 연결은 메일 박스 갯수만큼의 메일함을 소유한다. 
		 * 메일함 1개당 1개의 입력 메시지큐와 1개의 출력 메시지큐를 갖는다.
		 * 입력 메시지 큐는 모든 연결 클래스간에 공유하며 , 출력 메시지 큐는 메일함마다 각각 존재한다.
		 * </pre>
		 */
		for (int i = 0; i < mailBoxCnt; i++) {
			
			
			PrivateMailbox mailBox = new PrivateMailbox(this, i+1,
					inputMessageQueue, outputMessageQueueQueueManager);
			
			try {
				PrivateMailboxWaitingQueue.put(mailBox);
			} catch (InterruptedException e) {
				if (isInterrupted) {
					log.fatal("인터럽트 받아 후속 처리중 발생", e);
					System.exit(1);
				} else {
					try {
						PrivateMailboxWaitingQueue.put(mailBox);
					} catch (InterruptedException e1) {
						log.fatal("인터럽트 받아 후속 처리중 발생", e1);
						System.exit(1);
					}
					isInterrupted = true;
					continue;
				}
			}
		}
		
		if (isInterrupted) Thread.currentThread().interrupt();
	}
	
	
	@Override
	public void serverOpen() throws ServerNotReadyException, InterruptedException {
		// log.info("projectName[%s%02d] call serverOpen start", projectName,
		// index);

		// int oldServerSCHashCode = -1;
		//boolean isNew = true;
		
		/**
		 * <pre>
		 * 서버와 연결하는 시간보다 연결 이후 시간이 훨씬 길기때문에,
		 * 연결시 비용이 더 들어가도 연결후 비용을 더 줄일 수 만 있다면 좋을 것이다.
		 * 아래와 같이 (재)연결 판단 로직 문을 2번 사용하여 이를 달성한다.
		 * 그러면 소켓 채널이 서버와 연결된 후에는 동기화 비용 없어지고
		 * 연결 할때만 if 문 중복 비용만 더 추가될 것이다.
		 * </pre>
		 */
		/** (재)연결 판단 로직 */
		if (null != serverSC && serverSC.isConnected()) return;
		
		try {
			synchronized (monitor) {
				StringBuilder infoStringBuilder = null;
				/** (재)연결 판단 로직, 2번이상 SocketChannel.open() 호출하는것을 막는 역활을 한다. */
				if (null == serverSC) {
					infoStringBuilder = new StringBuilder("projectName[");
					infoStringBuilder.append(commonProjectInfo.projectName);
					infoStringBuilder.append("] asyn connection[");
					infoStringBuilder.append(index);
					infoStringBuilder.append("] ");
				} else if (!serverSC.isConnected()) {
					infoStringBuilder = new StringBuilder("projectName[");
					infoStringBuilder.append(commonProjectInfo.projectName);
					infoStringBuilder.append("] asyn connection[");
					infoStringBuilder.append(index);
					infoStringBuilder.append("] old serverSC[");
					infoStringBuilder.append(serverSC.hashCode());
					infoStringBuilder.append("] ");
				} else {
					return;
				}
				
				
				serverSC = SocketChannel.open();
				
				infoStringBuilder.append("new serverSC[");
				infoStringBuilder.append(serverSC.hashCode());
				infoStringBuilder.append("] ");
				
				log.info(infoStringBuilder.toString());
				

				serverSC.configureBlocking(false);
				serverSC.setOption(StandardSocketOptions.SO_KEEPALIVE, true);
				serverSC.setOption(StandardSocketOptions.TCP_NODELAY, true);
				serverSC.setOption(StandardSocketOptions.SO_LINGER, 0);
				
				// sc.setSendBufferSize(io_buffer_size);
				// sc.setReceiveBufferSize(io_buffer_size);
				InetSocketAddress remoteAddr = new InetSocketAddress(
						commonProjectInfo.serverHost,
						commonProjectInfo.serverPort);
				serverSC.connect(remoteAddr);
				finishConnect();
				initSocketResource();
				// log.info("serverSC isConnected=[%s]",
				// serverSC.isConnected());
				afterConnectionWork();
			}
			

		} catch (ConnectException e) {
			throw new ServerNotReadyException(
					String.format(
							"ConnectException::%s conn index[%02d], host[%s], port[%d]", 
							commonProjectInfo.projectName,
							index, commonProjectInfo.serverHost,
							commonProjectInfo.serverPort));
		} catch (UnknownHostException e) {
			throw new ServerNotReadyException(
					String.format(
							"UnknownHostException::%s conn index[%02d], host[%s], port[%d]", 
							commonProjectInfo.projectName,
							index, commonProjectInfo.serverHost,
							commonProjectInfo.serverPort));
		} catch (ClosedChannelException e) {
			throw new ServerNotReadyException(
					String.format(
							"ClosedChannelException::%s conn index[%02d], host[%s], port[%d]", 
							commonProjectInfo.projectName,
							index, commonProjectInfo.serverHost,
							commonProjectInfo.serverPort));
		} catch (IOException e) {
			closeServer();
			
			throw new ServerNotReadyException(
					String.format(
							"IOException::index[%d], projectName[%s], host[%s], port[%d]",
							index, commonProjectInfo.projectName, commonProjectInfo.serverHost,
							commonProjectInfo.serverPort));
		} catch (ServerNotReadyException e) {
			throw e;
		} catch (InterruptedException e) {
			closeServer();
			
			throw e;
		} catch (Exception e) {
			closeServer();
			
			log.warn("unknown exception", e);
			throw new ServerNotReadyException(String.format(
					"unknown::%s conn index[%02d], host[%s], port[%d]", 
					commonProjectInfo.projectName,
					index, commonProjectInfo.serverHost,
					commonProjectInfo.serverPort));
		}

		// log.info("projectName[%s%02d] call serverOpen end", projectName,
		// index);
	}

	@Override
	public void putToOutputMessageQueue(OutputMessage outObj) {
		int mailboxID = outObj.messageHeaderInfo.mailboxID;
		
		if (mailboxID == CommonStaticFinal.SERVER_MAILBOX_ID) {	
			/** 서버에서 보내는 공지등 불특정 다수한테 보내는 출력 메시지 */
			boolean result = false;
			try {
				result = serverOutputMessageQueue.offer(outObj, socketTimeOut, TimeUnit.MILLISECONDS);
			} catch (InterruptedException e) {
				/**
				 * 인터럽트 발생시 메소드 끝가지 로직 수행후 인터럽트 상태를 복귀 시켜 최종 인터럽트 처리를 마무리 하도록 유도
				 */				
				try {
					result = serverOutputMessageQueue.offer(outObj, socketTimeOut, TimeUnit.MILLISECONDS);
				} catch (InterruptedException e1) {
					log.fatal("인터럽트 받아 후속 처리중 발생", e1);
					System.exit(1);
				}
				Thread.currentThread().interrupt();
			}
			
			if (!result) {
				String errorMsg = String
						.format("서버 공용 출력 메시지 큐 응답 시간[%d]이 초과되었습니다. serverConnection=[%s], mailboxID=[%d], mailID=[%d]",
								socketTimeOut, toString(),
								mailboxID, outObj.messageHeaderInfo.mailID);
				
				log.warn(errorMsg);
			}
		} else {
			PrivateMailbox mailbox = hashActiveMailBox.get(mailboxID);
			if (null == mailbox) {
				
				log.warn(String.format("no match mailid, projectName=[%s%02d], serverSC=[%d], outputMessage=[%s]",
						commonProjectInfo.projectName, index, serverSC.hashCode(),
						outObj.toString()));
				return;
			}			
			mailbox.putToOutputMessageQueue(outObj);
		}
	}

	@Override
	public LetterFromServer sendInputMessage(InputMessage inObj)
			throws ServerNotReadyException, SocketTimeoutException,
			NoMoreDataPacketBufferException, BodyFormatException, MessageInfoNotFoundException {
		long startTime = 0;
		long endTime = 0;
		startTime = new java.util.Date().getTime();

		// log.info("projectName[%s] inputMessage=[%s]", projectName,
		// inObj.toString());

		LetterFromServer letterFromServer = null;
		
		try {
			serverOpen();
		} catch (InterruptedException e1) {
			Thread.currentThread().interrupt();
			return letterFromServer;	
		}

		PrivateMailbox mailbox = null;
		int mailboxID = -1;
		
		
		boolean isInterrupted = false;
		
		try {
			try {
				mailbox = PrivateMailboxWaitingQueue.take();
				
				
			} catch (InterruptedException e) {
				try {
					mailbox = PrivateMailboxWaitingQueue.take();
				} catch (InterruptedException e1) {
					log.fatal("인터럽트 받아 후속 처리중 발생", e1);
					System.exit(1);
				}
				isInterrupted = true;
			}
			mailbox.setActive();
			mailboxID = mailbox.getMailboxID();
			hashActiveMailBox.put(mailboxID, mailbox);
			
			LetterToServer letterToServer = new LetterToServer(this, inObj);
			try {
				mailbox.putInputMessage(letterToServer);
			} catch (InterruptedException e) {
				isInterrupted = true;
				try {
					mailbox.putInputMessage(letterToServer);
				} catch (InterruptedException e1) {
					log.fatal("인터럽트 받아 후속 처리중 발생", e);
					System.exit(1);
				}
			}
			
			OutputMessage workOutObj = null;
			
			try {				
				workOutObj = mailbox.takeOutputMessage();
				
				letterFromServer = new LetterFromServer(workOutObj);
			} catch(InterruptedException e) {
				/** 인터럽트 발생시 메소드 끝가지 로직 수행후 인터럽트 상태를 복귀 시켜 최종 인터럽트 처리를 마무리 하도록 유도 */					
				if (isInterrupted) {
					log.fatal("인터럽트 받아 후속 처리중 발생", e);
					System.exit(1);
				} else {
					try {
						workOutObj = mailbox.takeOutputMessage();
					} catch(InterruptedException e1) {
						log.fatal("인터럽트 받아 후속 처리중 발생", e1);
						System.exit(1);
					}
					isInterrupted = true;
				}
			}	
			
			
		} finally {
			if (null != mailbox) {
				hashActiveMailBox.remove(mailboxID);
				mailbox.setDisable();
				/**
				 * InterruptedException 를 발생시키지 않는 offer 메소드 사용. 개인 메일함
				 * 큐(=PrivateMailboxWaitingQueue) 는 메일함의 갯수를 고정으로 갖으며, 그것을 넘어서는
				 * 메일함을 가질 이유는 없다. 단, 2번이상 넣기 시도등 큐에 2개 이상 중복되는 경우에 오동작을 한다. 하지만
				 * 큐에 대한 사용자 개입을 원천적으로 차단되어 2개이상 중복하여 큐에 들어갈 일은 없다.
				 */
				PrivateMailboxWaitingQueue.offer(mailbox);
			}
			
			if (isInterrupted) Thread.currentThread().interrupt();
		}

		endTime = new java.util.Date().getTime();
		log.info(String.format("sendInputMessage 시간차=[%d]", (endTime - startTime)));
		
		// log.info("sendInputMessage end");
		return letterFromServer;
	}

	/**
	 * @return 사용중인 메일함 갯수
	 */
	public int getUsedMailboxCnt() {
		return PrivateMailboxWaitingQueue.remainingCapacity();
	}
	
	@Override
	protected void afterConnectionWork() throws InterruptedException {
		outputMessageReaderPool.addNewServer(this);
	}

	
	@Override
	public void finalize() {
		messageInputStreamResource.destory();
		log.warn(String.format("MultiNoneBlockConnection 소멸::[%s]", toString()));
	}

}
