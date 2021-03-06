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

package kr.pe.sinnori.gui.action.fileupdownscreen;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.File;

import javax.swing.JFrame;
import javax.swing.JOptionPane;
import javax.swing.JTree;
import javax.swing.tree.TreePath;

import kr.pe.sinnori.common.lib.CommonRootIF;
import kr.pe.sinnori.gui.lib.FileUpDownScreenIF;
import kr.pe.sinnori.gui.lib.LocalFileTreeNode;

/**
 * 로컬 트리 노드 마우스 이벤트 처리 클래스
 * @author Jonghoon Won
 *
 */
public class LocalTreeMouseListener extends MouseAdapter implements CommonRootIF {
	private JFrame mainFrame = null;
	private FileUpDownScreenIF fileUpDownScreen = null;
	private JTree localTree = null;
	private LocalFileTreeNode localRootNode = null;
	
	/**
	 * 생성자
	 * @param mainFrame 메인 프레임
	 * @param fileUpDownScreen 파일 송수신 화면을 제어하는 기능 제공 인터페이스
	 * @param localTree 로컬 트리
	 * @param localRootNode 로컬 루트 노드
	 */
	public LocalTreeMouseListener(JFrame mainFrame, 
			FileUpDownScreenIF fileUpDownScreen, 
			JTree localTree,
			LocalFileTreeNode localRootNode) {
		this.mainFrame = mainFrame;
		// this.mainController = mainController;
		this.fileUpDownScreen = fileUpDownScreen;
		this.localTree = localTree;
		this.localRootNode = localRootNode;
	}
	
	@Override
	public void mousePressed(MouseEvent e) {
		JTree tree = (JTree) e.getSource();
		int selRow = tree.getRowForLocation(e.getX(), e.getY());
		TreePath selPath = tree.getPathForLocation(e.getX(), e.getY());
		if (selRow != -1) {
			LocalFileTreeNode selNode = (LocalFileTreeNode) selPath
					.getLastPathComponent();
			
			if (selNode.isDirectory() && e.getClickCount() == 2
					&& !selNode.isRoot()) {
				/**
				 * 루트 노드를 제외한 "디렉토리 노드" 더블 클릭시 처리 로직. 작업 경로를 더블 클릭한 디렉토리로 이동한다.
				 * double-click logic when a directory node which is not a root node is clicked.
				 */
				log.debug(String.format("selNode=[%s]", selNode.getFileName()));

				StringBuilder newWorkPathBuilder = new StringBuilder(localRootNode.getAbsolutePath());
				newWorkPathBuilder.append(File.separator);
				newWorkPathBuilder.append(selNode.getFileName());
				String newWorkPath = newWorkPathBuilder.toString();

				log.debug(String.format("newWorkPath=[%s]", newWorkPath));
				

				File localSelectedPathFile = new File(newWorkPath);

				if (!localSelectedPathFile.exists()) {
					// log.debug(String.format("선택된 디렉토리[%s]가 존재하지 않습니다.", newWorkPath));

					JOptionPane.showMessageDialog(mainFrame, "선택된 디렉토리가 존재하지 않습니다");
					return;
				}

				if (!localSelectedPathFile.isDirectory()) {
					// log.debug(String.format("선택된 디렉토리[%s]가 디렉토리가 아닙니다.", newWorkPath));
					
					JOptionPane.showMessageDialog(mainFrame,
							"선택된 디렉토리가 디렉토리가 아닙니다.");
					return;
				}

				localRootNode.changeFileObj(localSelectedPathFile);
				localRootNode.removeAllChildren();
				fileUpDownScreen.makeLocalTreeNode(localRootNode);
				fileUpDownScreen.repaintTree(localTree);
			}
		}
	}
}


