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
import kr.pe.sinnori.common.exception.UpDownFileException;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.message.InputMessage;
import kr.pe.sinnori.common.message.OutputMessage;
import kr.pe.sinnori.common.updownfile.LocalTargetFileResource;
import kr.pe.sinnori.common.updownfile.LocalTargetFileResourceManager;
import kr.pe.sinnori.server.ClientResource;
import kr.pe.sinnori.server.ClientResourceManagerIF;
import kr.pe.sinnori.server.executor.AbstractAuthServerExecutor;
import kr.pe.sinnori.server.io.LetterListToClient;
import kr.pe.sinnori.server.io.LetterToClient;

public final class UpFileDataSExtor extends AbstractAuthServerExecutor {

	@Override
	protected void doTask(SocketChannel fromSC, InputMessage inObj,
			LetterListToClient letterToClientList,
			LinkedBlockingQueue<LetterToClient> ouputMessageQueue,
			MessageMangerIF messageManger,
			ClientResourceManagerIF clientResourceManager)
			throws MessageInfoNotFoundException, MessageItemException {
		LocalTargetFileResourceManager localTargetFileResourceManager = LocalTargetFileResourceManager.getInstance();
		
		OutputMessage outObj = messageManger.createOutputMessage("UpFileDataResult");
		
		int serverTargetFileID = (Integer)inObj.getAttribute("serverTargetFileID");
		int fileBlockNo = (Integer)inObj.getAttribute("fileBlockNo");
		byte[] fileData = (byte[])inObj.getAttribute("fileData");
		
		// FIXME!
		// log.info(inObj.toString());
		
		LocalTargetFileResource localTargetFileResource = localTargetFileResourceManager.getLocalTargetFileResource(serverTargetFileID);
		
		if (null == localTargetFileResource) {
			log.info(String.format("serverTargetFileID[%d] 업로드 파일을 받을 자원이 준비되지 않았습니다.", serverTargetFileID));
			
			outObj.setAttribute("taskResult", "N");
			outObj.setAttribute("resultMessage", "서버에서 업로드 파일을 받을 자원이 준비되지 않았습니다.");
			outObj.setAttribute("serverTargetFileID", serverTargetFileID);
			
			letterToClientList.addLetterToClient(fromSC, outObj);
			return;
		}
		
		boolean isCompletedWritingFile = false; 
		try {
			isCompletedWritingFile = localTargetFileResource.writeTargetFileData(fileBlockNo, fileData, true);
			
			// FIXME!
			// log.info(String.format("파일 쓰기 결과[%s]", isCompletedWritingFile));
			
			outObj.setAttribute("taskResult", "Y");
			outObj.setAttribute("resultMessage", "서버에 수신한 파일 조각을 성공적으로 저장했습니다.");
			outObj.setAttribute("serverTargetFileID", serverTargetFileID);
			try {
				letterToClientList.addLetterToClient(fromSC, outObj);
			} finally {
				if (isCompletedWritingFile) {
					ClientResource clientResource = clientResourceManager.getClientResource(fromSC);
					clientResource.removeLocalTargetFileID(serverTargetFileID);
					// localTargetFileResourceManager.putLocalTargetFileResource(localTargetFileResource);
				}
			}
			
		} catch (IllegalArgumentException e) {
			log.info(String.format("serverTargetFileID[%d] lock free::%s", serverTargetFileID, e.getMessage()), e);
			
			ClientResource clientResource = clientResourceManager.getClientResource(fromSC);
			clientResource.removeLocalTargetFileID(serverTargetFileID);
			
			outObj.setAttribute("taskResult", "N");
			outObj.setAttribute("resultMessage", "서버::"+e.getMessage());
			outObj.setAttribute("serverTargetFileID", serverTargetFileID);
			
			letterToClientList.addLetterToClient(fromSC, outObj);
			return;
		} catch (UpDownFileException e) {
			log.info(String.format("serverTargetFileID[%d] lock free::%s", serverTargetFileID, e.getMessage()), e);
			
			ClientResource clientResource = clientResourceManager.getClientResource(fromSC);
			clientResource.removeLocalTargetFileID(serverTargetFileID);
			
			outObj.setAttribute("taskResult", "N");
			outObj.setAttribute("resultMessage", "서버::"+e.getMessage());
			outObj.setAttribute("serverTargetFileID", serverTargetFileID);
			
			letterToClientList.addLetterToClient(fromSC, outObj);
			return;
		}
	}
}
