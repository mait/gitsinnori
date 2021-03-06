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

import kr.pe.sinnori.common.exception.NoMoreDataPacketBufferException;

/**
 * 서버에 접속 승인된 클라이언트(=소켓 채널) 등록 처리 쓰레드가 바라보는 입력 메시지 소켓 읽기 담당 쓰레드 폴 인터페이스.
 * 
 * @author Jonghoon Won
 */

public interface InputMessageReaderPoolIF {
	/**
	 * 신규 소켓을 읽기 쓰레드에 등록시칸다. 이때에는 읽기 쓰레드간에는 균등하게 소켓이 할당되도록 한다.
	 * 
	 * @param sc
	 *            등록을 원하는 신규 소켓 채널
	 * @throws InterruptedException
	 *             쓰레드 인터럽트
	 * @throws NoMoreDataPacketBufferException
	 *             소켓 채널당 1:1로 읽기 전용 버퍼를 할당받는다. 이 읽기 전용 버퍼 확보 실패시 발생
	 */
	public void addNewClient(SocketChannel sc) throws InterruptedException,
	NoMoreDataPacketBufferException;
}
