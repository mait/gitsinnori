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

package impl.executor.server;

import java.nio.channels.SocketChannel;
import java.util.concurrent.LinkedBlockingQueue;

import kr.pe.sinnori.common.exception.MessageInfoNotFoundException;
import kr.pe.sinnori.common.exception.MessageItemException;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.message.InputMessage;
import kr.pe.sinnori.common.message.OutputMessage;
import kr.pe.sinnori.server.ClientResourceManagerIF;
import kr.pe.sinnori.server.executor.AbstractServerExecutor;
import kr.pe.sinnori.server.io.LetterListToClient;
import kr.pe.sinnori.server.io.LetterToClient;

/**
 * 메세지 식별자 NotExist01 비지니스 로직
 * 
 * @author Jonghoon Won
 * 
 */
public final class NotExist01SExtor extends AbstractServerExecutor {

	@Override
	protected void doTask(SocketChannel fromSC, InputMessage inObj,
			LetterListToClient letterToClientList,
			LinkedBlockingQueue<LetterToClient> ouputMessageQueue,
			MessageMangerIF messageManger,
			ClientResourceManagerIF clientResourceManager)
			throws MessageInfoNotFoundException, MessageItemException {

		OutputMessage outObj = messageManger.createOutputMessage("NotExist01");

		// outObj.mCase = inObj.mCase;
		String mCase = (String) inObj.getAttribute("mCase");
		outObj.setAttribute("mCase", mCase);

		letterToClientList.addLetterToClient(fromSC, outObj);
	}

}
