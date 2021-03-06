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

package kr.pe.sinnori.server.threadpool.accept.processor;

import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;

import kr.pe.sinnori.common.threadpool.AbstractThreadPool;
import kr.pe.sinnori.server.threadpool.accept.processor.handler.AcceptProcessor;
import kr.pe.sinnori.server.threadpool.inputmessage.InputMessageReaderPoolIF;

/**
 * 서버에 접속 승인된 클라이언트(=소켓 채널) 등록 처리 쓰레드 폴.
 * 
 * @author Jonghoon Won
 * 
 */
public class AcceptProcessorPool extends AbstractThreadPool {
	private LinkedBlockingQueue<SocketChannel> acceptQueue;
	private int maxHandler;
	private InputMessageReaderPoolIF inputMessageReaderPoolIF = null;

	/**
	 * 생성자
	 * 
	 * @param size
	 *            소켓 채널의 신규 등록 처리를 수행하는 쓰레드 갯수
	 * @param max
	 *            소켓 채널의 신규 등록 처리를 수행하는 쓰레드 최대 갯수
	 * @param acceptQueue
	 *            생산자 신규 소켓 채널 요청자, 소비자 소켓 채널의 신규 등록 처리를 수행하는 쓰레드인 큐
	 */
	public AcceptProcessorPool(int size, int max,
			LinkedBlockingQueue<SocketChannel> acceptQueue,
			InputMessageReaderPoolIF inputMessageReaderPoolIF) {
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

		this.acceptQueue = acceptQueue;
		this.maxHandler = max;
		this.inputMessageReaderPoolIF = inputMessageReaderPoolIF;

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
					Thread handler = new AcceptProcessor(size, acceptQueue,
							inputMessageReaderPoolIF);
					pool.add(handler);
				} catch (Exception e) {
					log.warn("AcceptProcessor handler 등록 실패", e);
				}
			} else {
				throw new RuntimeException(String.format(
						"최대 핸들러 갯수[%d]를 초과할수없습니다.", maxHandler));
			}
		}
	}
}
