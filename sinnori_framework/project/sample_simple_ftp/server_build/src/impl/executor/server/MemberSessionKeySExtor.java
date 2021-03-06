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
import java.sql.SQLException;
import java.util.concurrent.LinkedBlockingQueue;

import kr.pe.sinnori.common.exception.MessageInfoNotFoundException;
import kr.pe.sinnori.common.exception.MessageItemException;
import kr.pe.sinnori.common.exception.SymmetricException;
import kr.pe.sinnori.common.lib.MessageMangerIF;
import kr.pe.sinnori.common.lib.SinnoriDBManager;
import kr.pe.sinnori.common.message.InputMessage;
import kr.pe.sinnori.common.message.OutputMessage;
import kr.pe.sinnori.common.sessionkey.ServerSessionKeyManager;
import kr.pe.sinnori.common.sessionkey.SymmetricKey;
import kr.pe.sinnori.server.ClientResourceManagerIF;
import kr.pe.sinnori.server.executor.AbstractServerExecutor;
import kr.pe.sinnori.server.io.LetterListToClient;
import kr.pe.sinnori.server.io.LetterToClient;

/**
 * 메세지 식별자 MemberSessionKey 비지니스 로직
 * 
 * @author Jonghoon Won
 * 
 */
public final class MemberSessionKeySExtor extends
		AbstractServerExecutor {

	@Override
	protected void doTask(SocketChannel fromSC, InputMessage inObj,
			LetterListToClient letterToClientList,
			LinkedBlockingQueue<LetterToClient> ouputMessageQueue,
			MessageMangerIF messageManger,
			ClientResourceManagerIF clientResourceManager)
			throws MessageInfoNotFoundException, MessageItemException {

		String idCipherBase64 = (String) inObj.getAttribute("idCipherBase64");
		String sessionKeyBase64 = (String) inObj
				.getAttribute("sessionKeyBase64");
		String ivBase64 = (String) inObj.getAttribute("ivBase64");
		String pwdCipherBase64 = (String) inObj
				.getAttribute("pwdCipherBase64");
		String nicknameCipherBase64 = (String) inObj
				.getAttribute("nicknameCipherBase64");
		String questionCipherBase64 = (String) inObj
				.getAttribute("questionCipherBase64");
		String answerCipherBase64 = (String) inObj
				.getAttribute("answerCipherBase64");
		// answerCipherBase64

		OutputMessage outputMessage = messageManger.createOutputMessage("MessageResult");
		// outputMessage.mMessageID = inObj.getMesgID();
		outputMessage.setAttribute("taskMessageID", inObj.getMessageID());

		// SymmetricKey symmetricKey =
		// ServerSessionKeyManager.getInstance().getSymmetricKey(inObj.sessionKeyBase64,
		// inObj.ivBase64);

		SymmetricKey symmetricKey = null;
		try {
			symmetricKey = ServerSessionKeyManager.getInstance()
					.getSymmetricKey(sessionKeyBase64, ivBase64);
		} catch (IllegalArgumentException e1) {
			outputMessage.setAttribute("taskResult", "N");
			outputMessage.setAttribute("resultMessage", e1.toString());
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (SymmetricException e1) {
			outputMessage.setAttribute("taskResult", "N");
			outputMessage.setAttribute("resultMessage", e1.toString());
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		}

		String id = null;
		String password = null;
		String nickname = null;
		String question = null;
		String answer = null;

		java.sql.Connection conn = null;
		java.sql.PreparedStatement pstmt_task = null;

		java.sql.PreparedStatement pstmt_check = null;
		java.sql.ResultSet rs_check = null;

		/*
		 * outputMessage.mResult = "N"; outputMessage.mMessage =
		 * String.format("회원 가입이 실패하였습니다.");
		 */

		outputMessage.setAttribute("taskResult", "N");
		outputMessage.setAttribute("resultMessage", "회원 가입이 실패하였습니다.");

		String sql_check = "select count(*) from member where id = ?";
		String sql_task = "insert into member (id, pwd, nickname, question, answer, last_login_date, login_fail_cnt, last_access_login_date) values (?, ?, ?, ?, ?, CURDATE(), ?, CURDATE())";

		

		try {
			id = symmetricKey.decryptStringBase64(idCipherBase64);
		} catch (IllegalArgumentException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("id::IllegalArgumentException");
			 */
			// outputMessage.setAttribute("taskResult", "N");
			outputMessage.setAttribute("resultMessage",
					"id::IllegalArgumentException");

			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (SymmetricException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("id::SymmetricException");
			 */
			outputMessage
					.setAttribute("resultMessage", "id::SymmetricException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		}

		try {
			password = symmetricKey.decryptStringBase64(pwdCipherBase64);
		} catch (IllegalArgumentException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("password::IllegalArgumentException");
			 */
			outputMessage.setAttribute("resultMessage",
					"password::IllegalArgumentException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (SymmetricException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("password::SymmetricException");
			 */
			outputMessage.setAttribute("resultMessage",
					"password::SymmetricException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		}

		try {
			nickname = symmetricKey
					.decryptStringBase64(nicknameCipherBase64);
		} catch (IllegalArgumentException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("nickname::IllegalArgumentException");
			 */
			outputMessage.setAttribute("resultMessage",
					"nickname::IllegalArgumentException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (SymmetricException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("nickname::SymmetricException");
			 */
			outputMessage.setAttribute("resultMessage",
					"nickname::SymmetricException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		}

		try {
			question = symmetricKey
					.decryptStringBase64(questionCipherBase64);
		} catch (IllegalArgumentException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("question::IllegalArgumentException");
			 */
			outputMessage.setAttribute("resultMessage",
					"question::IllegalArgumentException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (SymmetricException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("question::SymmetricException");
			 */
			outputMessage.setAttribute("resultMessage",
					"question::SymmetricException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		}

		try {
			answer = symmetricKey.decryptStringBase64(answerCipherBase64);
		} catch (IllegalArgumentException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("answer::IllegalArgumentException");
			 */
			outputMessage.setAttribute("resultMessage",
					"answer::IllegalArgumentException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (SymmetricException e) {
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("answer::SymmetricException");
			 */
			outputMessage.setAttribute("resultMessage",
					"answer::SymmetricException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		}

		log.info(String.format("id=[%s], password=[%s], nickname=[%s], question=[%s], answer=[%s]"
				, id, password, nickname, question, answer));

		// Date nowDate = Date.valueOf("2012");
		SinnoriDBManager sinnoriDBManager = SinnoriDBManager.getInstance();

		try {
			conn = sinnoriDBManager.getConnection();
			conn.setAutoCommit(false);

			pstmt_check = conn.prepareStatement(sql_check);
			pstmt_check.setString(1, id);
			rs_check = pstmt_check.executeQuery();

			if (rs_check.next()) {
				int countOfRecord = rs_check.getInt(1);
				if (countOfRecord > 0) {
					/*
					 * outputMessage.mResult = "N"; outputMessage.mMessage =
					 * String.format( "동일 아이디[%s]로 이미 가입한 회원이 있습니다.", id);
					 */

					outputMessage.setAttribute("resultMessage", String.format(
							"동일 아이디[%s]로 이미 가입한 회원이 있습니다.", id));
					letterToClientList.addLetterToClient(fromSC, outputMessage);
					return;
				}
			}

			int paramIndex = 1;
			pstmt_task = conn.prepareStatement(sql_task);
			pstmt_task.setString(paramIndex++, id);
			pstmt_task.setString(paramIndex++, password);
			pstmt_task.setString(paramIndex++, nickname);
			pstmt_task.setString(paramIndex++, question);
			pstmt_task.setString(paramIndex++, answer);
			// stmt.setDate(paramIndex++, nowDate);
			pstmt_task.setInt(paramIndex++, 0);
			// stmt.setDate(paramIndex++, nowDate);

			int rowCnt = pstmt_task.executeUpdate();
			// rs = stmt.executeQuery("select * from member where id=?");

			conn.commit();

			if (rowCnt > 0) {
				/*
				 * outputMessage.mResult = "Y"; outputMessage.mMessage =
				 * String.format("회원 가입을 축하드립니다.");
				 */

				outputMessage.setAttribute("taskResult", "Y");
				outputMessage.setAttribute("resultMessage", "회원 가입을 축하드립니다.");
				letterToClientList.addLetterToClient(fromSC, outputMessage);
				return;
			}

		} catch (java.sql.SQLException e) {
			log.warn("SQLException", e);
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("db insert fail::RuntimeException");
			 */
			outputMessage.setAttribute("resultMessage",
					"db insert fail::RuntimeException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} catch (java.lang.Exception e) {
			log.warn("Exception", e);
			/*
			 * outputMessage.mResult = "N"; outputMessage.mMessage = String
			 * .format("unknown error::RuntimeException");
			 */
			outputMessage.setAttribute("resultMessage",
					"unknown error::RuntimeException");
			letterToClientList.addLetterToClient(fromSC, outputMessage);
			return;
		} finally {
			try {
				conn.setAutoCommit(true);
			} catch (SQLException e) {
				log.warn("setAutoCommit SQLException", e);
			}

			try {
				if (rs_check != null)
					rs_check.close();
			} catch (java.sql.SQLException e) {
				log.warn("rs_check SQLException", e);
			}

			try {
				if (pstmt_check != null)
					pstmt_check.close();
			} catch (java.sql.SQLException e) {
				log.warn("pstmt_check SQLException", e);
			}

			try {
				if (pstmt_task != null)
					pstmt_task.close();
			} catch (java.sql.SQLException e) {
				log.warn("pstmt_task SQLException", e);
			}

			try {
				if (conn != null)
					conn.close();
			} catch (java.sql.SQLException e) {
				log.warn("conn SQLException", e);
			}
		}

		letterToClientList.addLetterToClient(fromSC, outputMessage);

	}

}
