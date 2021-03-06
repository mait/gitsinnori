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

package kr.pe.sinnori.server.executor;

import java.nio.channels.SocketChannel;
import java.util.ArrayList;
import java.util.concurrent.LinkedBlockingQueue;

import kr.pe.sinnori.common.exception.MessageInfoNotFoundException;
import kr.pe.sinnori.common.exception.MessageItemException;
import kr.pe.sinnori.common.lib.CommonProjectInfo;
import kr.pe.sinnori.common.lib.CommonRootIF;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.message.InputMessage;
import kr.pe.sinnori.server.ClientResourceManagerIF;
import kr.pe.sinnori.server.io.LetterListToClient;
import kr.pe.sinnori.server.io.LetterToClient;

/**
 * <pre>
 * 로그인을 요구하지 않는 서버 비지니스 로직 부모 클래스. 
 * 메시지는 자신만의 서버 비지니스를 갖는다. 
 * 개발자는 이 클래스를 상속 받은 메시지별 비지니스 로직을 개발하며, 
 * 이렇게 개발된 비지니스 로직 모듈은 동적으로 로딩된다.
 * </pre> 
 * 
 * @author Jonghoon Won
 * 
 */
public abstract class AbstractServerExecutor implements CommonRootIF {
	
	/**
	 * 파일 관련 2개의 메시지 UpFileInfo, DownFileInfo 들을 제외한 메시지에 호출되는 메소드로,
	 * 클라이언트에서 보낸 입력 메시지 내용에 따라 비지니스 로직을 수행 후 
	 * 결과로 생긴 출력 메시지들을 편지에 넣어 반환한다.
	 * 참고) 서버가 다루는 편지는 메시지와 소켓 채널 묶음이다. 
	 *       이렇게 소켓 채널과 메시지를 묶은 이유는 
	 *       소켓 채널을 통해서만 서버와의 데이터 교환을 할 수 있기때문이다. 
	 * 
	 * @param fromSC 입력 메시지를 보낸 클라이언트와 연결된 소켓
	 * @param inObj 입력 메시지
	 * @param commonProjectInfo 공통 프로젝트 정보
	 * @param messageManger 메시지 관리자
	 * @param clientResourceManager 클라리언트 자원 관리자
	 * @return 비지니스 로직 수행후 클라이언트로 보내는 편지 목록
	 * @throws MessageInfoNotFoundException 메시지 정보 파일이 존재하지 않을때 던지는 예외
	 * @throws MessageItemException 메시지 항목 값을 얻을때 혹은 항목 값을 설정할때 항목 관련 에러 발생시 던지는 예외
	 */
	
	public ArrayList<LetterToClient> executeInputMessage(
			SocketChannel fromSC,
			InputMessage inObj,
			CommonProjectInfo commonProjectInfo,
			MessageMangerIF messageManger,
			ClientResourceManagerIF clientResourceManager) throws MessageInfoNotFoundException, MessageItemException {
		// InputMessage inObj = fromLetter.getInputMessage();
		// SocketChannel fromSC = fromLetter.getFromSC();
		

		LetterListToClient letterListToClient = new LetterListToClient(fromSC, inObj, clientResourceManager);

		// FIXME!
		// log.info("inputMessage=[%s]", inputMessage.toString());

		
		doTask(fromSC, inObj, letterListToClient, null, messageManger, clientResourceManager);
		

		ArrayList<LetterToClient> list = letterListToClient.getList();
		return list;
	}
	
	/**
	 * <pre>
	 * 파일 관련 2개의 메시지 UpFileInfo, DownFileInfo 에서 호출되는 메소드로,
	 * 클라이언트에서 보낸 입력 메시지 내용에 따라 비지니스 로직을 수행 후 
	 * 결과로 생긴 출력 메시지들을 편지에 넣어 반환한다.
	 * 
	 * 참고) 서버가 다루는 편지는 메시지와 소켓 채널 묶음이다. 
	 *       이렇게 소켓 채널과 메시지를 묶은 이유는 
	 *       소켓 채널을 통해서만 서버와의 데이터 교환을 할 수 있기때문이다.
	 * </pre> 
	 * 
	 * @param fromSC 입력 메시지를 보낸 클라이언트와 연결된 소켓
	 * @param inObj 입력 메시지
	 * @param commonProjectInfo 공통 프로젝트 정보
	 * @param ouputMessageQueue 출력 메시지 큐
	 * @param messageManger 메시지 관리자
	 * @param clientResourceManager 클라리언트 자원 관리자
	 * @return 비지니스 로직 수행후 클라이언트로 보내는 편지 목록
	 * @throws MessageInfoNotFoundException 메시지 정보 파일이 존재하지 않을때 던지는 예외
	 * @throws MessageItemException 메시지 항목 값을 얻을때 혹은 항목 값을 설정할때 항목 관련 에러 발생시 던지는 예외 
	 */
	public ArrayList<LetterToClient> executeInputMessage(
			SocketChannel fromSC,
			InputMessage inObj,
			CommonProjectInfo commonProjectInfo,
			LinkedBlockingQueue<LetterToClient> ouputMessageQueue,
			MessageMangerIF messageManger,
			ClientResourceManagerIF clientResourceManager) throws MessageInfoNotFoundException, MessageItemException {
		// InputMessage inObj = fromLetter.getInputMessage();
		// SocketChannel fromSC = fromLetter.getFromSC();
		

		LetterListToClient letterListToClient = new LetterListToClient(fromSC, inObj, clientResourceManager);

		// FIXME!
		// log.info("inputMessage=[%s]", inputMessage.toString());

		
		doTask(fromSC, inObj, letterListToClient, ouputMessageQueue, messageManger, clientResourceManager);
		

		ArrayList<LetterToClient> list = letterListToClient.getList();
		return list;
	}
	
	/**
	 * 출력메시지 직접 전송하는 개발자가 직접 작성해야할 비지니스 로직
	 * @param fromSC 입력 메시지를 보낸 클라이언트와 연결된 소켓
	 * @param inObj 입력 메시지
	 * @param letterListToClient 직접 출력 메시지 큐에 출력 메시지를 전달하므로 추가된 출력 메시지가 하나도 없을 수 있다.
	 * @param ouputMessageQueue 출력 메시지 큐, 파일 관련 2개의 메시지 UpFileInfo, DownFileInfo 에서만 유효하다. 
	 * @param messageManger 메시지 관리자
	 * @param clientResourceManager 클라이언트 자원 관리자
	 * @throws MessageInfoNotFoundException 메시지 정보 파일이 존재하지 않을때 던지는 예외
	 * @throws MessageItemException 메시지 항목 값을 얻을때 혹은 항목 값을 설정할때 항목 관련 에러 발생시 던지는 예외
	 */
	abstract protected void doTask(SocketChannel fromSC, InputMessage inObj,
			LetterListToClient letterListToClient, 
			LinkedBlockingQueue<LetterToClient> ouputMessageQueue,
			MessageMangerIF messageManger,			
			ClientResourceManagerIF clientResourceManager)
			throws MessageInfoNotFoundException, MessageItemException;
}
