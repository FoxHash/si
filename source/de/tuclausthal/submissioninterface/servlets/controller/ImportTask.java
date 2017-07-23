/*
 * Copyright 2013-2014 Sven Strickroth <email@cs-ware.de>
 * 
 * This file is part of the SubmissionInterface.
 * 
 * SubmissionInterface is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License version 3 as
 * published by the Free Software Foundation.
 * 
 * SubmissionInterface is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with SubmissionInterface. If not, see <http://www.gnu.org/licenses/>.
 */

package de.tuclausthal.submissioninterface.servlets.controller;

import java.io.BufferedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Collection;
import java.util.Date;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.jar.Attributes;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.servlet.ServletException;
import javax.servlet.annotation.MultipartConfig;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.Part;
import javax.tools.JavaCompiler;
import javax.tools.ToolProvider;
import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.transform.Source;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;
import javax.xml.namespace.NamespaceContext;

import org.apache.tomcat.util.http.fileupload.FileItem;
import org.apache.tomcat.util.http.fileupload.FileItemFactory;
import org.apache.tomcat.util.http.fileupload.FileUpload;
import org.apache.tomcat.util.http.fileupload.FileUploadBase;
import org.apache.tomcat.util.http.fileupload.FileUploadException;
import org.apache.tomcat.util.http.fileupload.RequestContext;
import org.apache.tomcat.util.http.fileupload.disk.DiskFileItemFactory;
import org.apache.tomcat.util.http.fileupload.servlet.ServletFileUpload;
import org.apache.tomcat.util.http.fileupload.servlet.ServletRequestContext;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import de.tuclausthal.submissioninterface.persistence.dao.DAOFactory;
import de.tuclausthal.submissioninterface.persistence.dao.LectureDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.ParticipationDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.PointCategoryDAOIf;
import de.tuclausthal.submissioninterface.persistence.dao.TestDAOIf;
import de.tuclausthal.submissioninterface.persistence.datamodel.JUnitTest;
import de.tuclausthal.submissioninterface.persistence.datamodel.Lecture;
import de.tuclausthal.submissioninterface.persistence.datamodel.Participation;
import de.tuclausthal.submissioninterface.persistence.datamodel.ParticipationRole;
import de.tuclausthal.submissioninterface.persistence.datamodel.Task;
import de.tuclausthal.submissioninterface.persistence.datamodel.TaskGroup;
import de.tuclausthal.submissioninterface.persistence.datamodel.Test;
import de.tuclausthal.submissioninterface.servlets.RequestAdapter;
import de.tuclausthal.submissioninterface.testframework.tests.impl.JavaSyntaxTest;
import de.tuclausthal.submissioninterface.util.ContextAdapter;
import de.tuclausthal.submissioninterface.util.Util;
import de.tuclausthal.submissioninterface.util.NamespaceContextMap;

/**
 * Controller-Servlet for importing tasks by advisors
 * @author Sven Strickroth
 */
@MultipartConfig
public class ImportTask extends HttpServlet {
	@Override
	public void doGet(HttpServletRequest request, HttpServletResponse response) throws IOException, ServletException {
		Session session = RequestAdapter.getSession(request);

		LectureDAOIf lectureDAO = DAOFactory.LectureDAOIf(session);
		Lecture lecture = lectureDAO.getLecture(Util.parseInteger(request.getParameter("lecture"), 0));
		if (lecture == null) {
			request.setAttribute("title", "Vorlesung nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		// check Lecture Participation
		ParticipationDAOIf participationDAO = DAOFactory.ParticipationDAOIf(session);
		Participation participation = participationDAO.getParticipation(RequestAdapter.getUser(request), lecture);
		if (participation == null || participation.getRoleType().compareTo(ParticipationRole.ADVISOR) != 0) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "operation not allowed");
			return;
		}

		request.setAttribute("lecture", lecture);
		request.getRequestDispatcher("ImportTaskView").forward(request, response);
	}

	@Override
	protected void doPost(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
		Session session = RequestAdapter.getSession(request);

		LectureDAOIf lectureDAO = DAOFactory.LectureDAOIf(session);
		Lecture lecture = lectureDAO.getLecture(Util.parseInteger(request.getParameter("lecture"), 0));
		if (lecture == null) {
			request.setAttribute("title", "Vorlesung nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		// check Lecture Participation
		ParticipationDAOIf participationDAO = DAOFactory.ParticipationDAOIf(session);
		Participation participation = participationDAO.getParticipation(RequestAdapter.getUser(request), lecture);
		if (participation == null || participation.getRoleType().compareTo(ParticipationRole.ADVISOR) != 0) {
			response.sendError(HttpServletResponse.SC_FORBIDDEN, "operation not allowed");
			return;
		}
        
		Part file = request.getPart("file");
		if (file == null) {
			request.setAttribute("title", "Keine Datei gefunden.");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}
		
		File tempDir = Util.createTemporaryDirectory("import", null);
		File taskFile = new File(tempDir, "task.xml");

		String fileName = Util.getFileName(file);
		String title = "";
		
		if (!request.getParameter("title").isEmpty()){
			title = request.getParameter("title");
		}
		
		String taskGroupString = request.getParameter("taskGroup");

		//Process a file upload
		if (!fileName.endsWith(".xml") && !fileName.endsWith(".zip")) {
			request.setAttribute("title", "Dateiname ungültig.");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}
		try {
			if (fileName.endsWith(".zip")) {
				ZipInputStream zipFile = new ZipInputStream(file.getInputStream());
				ZipEntry entry = null;
				while ((entry = zipFile.getNextEntry()) != null) {
					if (entry.isDirectory()) {
						continue;
					}
					if (!isPathSafe(tempDir, entry.getName())) {
						System.err.println("Ignored entry: " + entry.getName() + "; is invalid");
						continue;
					}
					StringBuffer archivedFileName = new StringBuffer(entry.getName().replace("\\", "/"));
					Util.lowerCaseExtension(archivedFileName);
					File fileToCreate = new File(tempDir, archivedFileName.toString());
					if (!fileToCreate.getParentFile().exists()) {
						fileToCreate.getParentFile().mkdirs();
					}
					Util.copyInputStreamAndClose(zipFile, new BufferedOutputStream(new FileOutputStream(fileToCreate)));
				}
				zipFile.close();
			} else {
				file.write(taskFile.toString());
			}
			if (!taskFile.exists()) {
				throw new IOException("task.xml not found after uploading!");
			}
		} catch (Exception e) {
			e.printStackTrace();
			request.setAttribute("title", "Upload error.");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		TaskGroup taskGroup = DAOFactory.TaskGroupDAOIf(session).getTaskGroup(Util.parseInteger(taskGroupString, 0));
		if (taskGroup == null) {
			request.setAttribute("title", "Aufgabengruppe nicht gefunden");
			request.getRequestDispatcher("MessageView").forward(request, response);
			return;
		}

		Task task = new Task();
		task.setTitle(title);
		task.setTaskGroup(taskGroup);

		task.setStart(new Date());
		task.setDeadline(new Date());
		task.setShowPoints(new Date());

		Transaction tx = null;

		try {
			
			//Define namespaces
			NamespaceContext context = new NamespaceContextMap(
					  "proforma", "urn:proforma:task:v1.0.1",
			          "unit", "urn:proforma:tests:unittest:v1",
			          "gate", "urn:proforma:gate");
			
			//We need a Document
			DocumentBuilderFactory dbfac = DocumentBuilderFactory.newInstance();
			dbfac.setNamespaceAware(true);
			DocumentBuilder docBuilder = dbfac.newDocumentBuilder();
			Document doc = docBuilder.parse(taskFile);

			/*SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
			Source schemaFile = new StreamSource(new File("task.xsd"));
			Schema schema = factory.newSchema(schemaFile);
			Validator validator = schema.newValidator();
			validator.validate(new DOMSource(doc));*/

			XPath xPath = XPathFactory.newInstance().newXPath();
			xPath.setNamespaceContext(context);

			Node root = doc.getFirstChild();
			if (root == null || !root.getNodeName().equals("task")) {
				throw new Exception("Not an exchange format XML file");
			}
			
			Node lang = root.getAttributes().getNamedItem("lang");
			if (lang == null){
		        throw new Exception("Not a valid exchange format XML file");
			}
			
			Node uuid = root.getAttributes().getNamedItem("uuid");
			if (uuid == null){
		        throw new Exception("Not a valid exchange format XML file");
			} else {
				task.setUUID(uuid.getTextContent());
			}
			
			Node parentuuid = root.getAttributes().getNamedItem("parent-uuid");
			{
				task.setParentUUID(parentuuid.getTextContent());
			}
			

			Node description = (Node) xPath.evaluate("/proforma:task/proforma:description", doc.getDocumentElement(), XPathConstants.NODE);
			if (description == null) {
				throw new Exception("Not a valid exchange format XML file");
			}
			if (description.getTextContent() != null) {
				task.setDescription(description.getTextContent());
			}

			Node proglang = (Node) xPath.evaluate("/proforma:task/proforma:proglang", doc.getDocumentElement(), XPathConstants.NODE);
			if (proglang == null) {
				throw new Exception("Not a valid exchange format XML file");
			}
			if (!proglang.getTextContent().equals("java")) {
				throw new Exception("Only java tasks are supported ATM");
			}
			Node proglangVersion = proglang.getAttributes().getNamedItem("version");
			if (proglangVersion == null) {
			    throw new Exception("Not a valid exchange format XML file");
			}

			//if (xPath.evaluate("/proforma:task/proforma:submission-restrictions", doc.getDocumentElement(), XPathConstants.NODE) == null) {
				//throw new Exception("Not a valid exchange format XML file");
			//}
			
			Node submissionRestrictions = (Node) xPath.evaluate("/proforma:task/proforma:submission-restrictions", doc.getDocumentElement(), XPathConstants.NODE);
			if (submissionRestrictions == null) {
			    throw new Exception("Not a valid exchange format XML file");   
			}
			
			//Import Regexp-Restrictions
			Node regexpRestriction = (Node) xPath.evaluate("proforma:regexp-restriction", submissionRestrictions, XPathConstants.NODE);
			if (regexpRestriction != null) {
				Node maxSize = regexpRestriction.getAttributes().getNamedItem("max-size");
				if (maxSize != null) {
					task.setMaxsize(Long.parseLong(maxSize.getNodeValue()));
				}
				task.setFilenameRegexp(regexpRestriction.getTextContent());	
			}
			
			//Import Filename-Restrictions
			Node filesRestriction = (Node) xPath.evaluate("proforma:files-restriction", submissionRestrictions, XPathConstants.NODE);
			if (filesRestriction != null) {
				Node required = (Node) xPath.evaluate("proforma:required", filesRestriction, XPathConstants.NODE);
				Node maxSize = required.getAttributes().getNamedItem("max-size");
				if (maxSize != null) {
					task.setMaxsize(Long.parseLong(maxSize.getNodeValue()));
				}
				Node filename = required.getAttributes().getNamedItem("filename");
				if (filename == null) {
				    throw new Exception("Not a valid exchange format XML file");
				} else {
					task.setFilenameRegexp("^" + filename.getNodeValue());
				}
			}
			
			//Import Archive-Restrictions
			Node archiveRestriction = (Node) xPath.evaluate("proforma:archive-restriction", submissionRestrictions, XPathConstants.NODE);
			if (archiveRestriction != null) {
				Node allowedArchiveFilename = archiveRestriction.getAttributes().getNamedItem("allowed-archive-filename");
				if (allowedArchiveFilename != null) {
					task.setFilenameRegexp(allowedArchiveFilename.getNodeValue());
				}
				Node unpackFilesFromArchiveRegexp = (Node) xPath.evaluate("proforma:unpack-files-from-archive-regexp", archiveRestriction, XPathConstants.NODE);
				Node fileRestrictions = (Node) xPath.evaluate("proforma:file-restrictions", archiveRestriction, XPathConstants.NODE);
				if (unpackFilesFromArchiveRegexp != null) {
					task.setArchiveFilenameRegexp(unpackFilesFromArchiveRegexp.getTextContent());
				} else if (fileRestrictions != null) {
					Node required = (Node) xPath.evaluate("proforma:required", fileRestrictions, XPathConstants.NODE);
					if (required != null) {
						Node path = required.getAttributes().getNamedItem("path");
						if (path == null) {
							throw new Exception("Not a valid exchange format XML file");
						} else {
							task.setArchiveFilenameRegexp("^" + path.getNodeValue());
						}
					}
				}
			}

			tx = session.beginTransaction();

			session.save(task);

			Node files = (Node) xPath.evaluate("/proforma:task/proforma:files", doc.getDocumentElement(), XPathConstants.NODE);
			if (files == null) {
				throw new Exception("Not a valid exchange format XML file");
			}
			ContextAdapter contextAdapter = new ContextAdapter(getServletContext());
			File taskPath = new File(contextAdapter.getDataPath().getAbsolutePath() + System.getProperty("file.separator") + task.getTaskGroup().getLecture().getId() + System.getProperty("file.separator") + task.getTaskid());
			File advisorFilesPath = new File(taskPath, "advisorfiles");
			if (advisorFilesPath.exists() == false) {
				advisorFilesPath.mkdirs();
			}
			NodeList fileEntries = (NodeList) xPath.evaluate("proforma:file", files, XPathConstants.NODESET);
			for (int i = 0; i < fileEntries.getLength(); ++i) {
				Node node = fileEntries.item(i);
				Node fileClass = node.getAttributes().getNamedItem("class");
				if (fileClass == null || fileClass.getNodeValue() == null || fileClass.getNodeValue().isEmpty()) {
					throw new Exception("Not a valid exchange format XML file");
				}
				if (fileClass.getNodeValue().equals("template") || fileClass.getNodeValue().equals("library") || fileClass.getNodeValue().equals("instruction")) {
					saveReferencedFile(advisorFilesPath, tempDir, node, false);
				}
			}

			//Import modelsolution 
			NodeList modelSolutionEntry = (NodeList) xPath.evaluate("/proforma:task/proforma:model-solutions/proforma:model-solution", doc.getDocumentElement(), XPathConstants.NODESET);
			if (modelSolutionEntry.getLength() > 0) {
				File modelSolutionPath = new File(taskPath, "modelsolutionfiles");
				if (modelSolutionPath.exists() == false) {
					modelSolutionPath.mkdirs();
				}
				for (int i = 0; i < modelSolutionEntry.getLength(); ++i) {
					NodeList referencedFileEntries = (NodeList) xPath.evaluate("proforma:filerefs/proforma:fileref", modelSolutionEntry.item(i), XPathConstants.NODESET);
					Node modelsolutionRefId = referencedFileEntries.item(i).getAttributes().getNamedItem("refid");
					if (modelsolutionRefId == null || modelsolutionRefId.getNodeValue() == null || modelsolutionRefId.getNodeValue().isEmpty()){
						throw new Exception("Not a valid exchange format XML file");
				    } else {
					    for (int j = 0; j < referencedFileEntries.getLength(); ++j) {
						    Node referencedFileEntry = (Node) xPath.evaluate("/proforma:task/proforma:files/proforma:file[@id=\"" + modelsolutionRefId.getNodeValue() + "\"]", doc.getDocumentElement(), XPathConstants.NODE);
						    if (referencedFileEntry == null) {
							    throw new Exception("Not a valid exchange format XML file");
					        }
					        saveReferencedFile(modelSolutionPath, tempDir, referencedFileEntry, true);
				        }
				    }
				}
			}

			//Import tests
			NodeList testEntry = (NodeList) xPath.evaluate("/proforma:task/proforma:tests/proforma:test", doc.getDocumentElement(), XPathConstants.NODESET);
			if (testEntry.getLength() > 0) {
				for (int i = 0; i < testEntry.getLength(); ++i) {
					Node testType = (Node) xPath.evaluate("proforma:test-type", testEntry.item(i), XPathConstants.NODE);
					Node testTitle = (Node) xPath.evaluate("proforma:title", testEntry.item(i), XPathConstants.NODE);
					Node testConfiguration = (Node) xPath.evaluate("proforma:test-configuration", testEntry.item(i), XPathConstants.NODE);

					if (testType == null || testType.getTextContent().isEmpty() || testTitle == null || testTitle.getTextContent().isEmpty() || testConfiguration == null) {
						throw new Exception("Not a valid exchange format XML file");
					}

					Node testTimeout = (Node) xPath.evaluate("proforma:timeout", testConfiguration, XPathConstants.NODE);
					Node testDescription = (Node) xPath.evaluate("proforma:test-meta-data/gate:test-description", testConfiguration, XPathConstants.NODE);
					Node testRunnableByStudents = (Node) xPath.evaluate("proforma:test-meta-data/gate:times-runnable-by-students", testConfiguration, XPathConstants.NODE);
					Node testTutorTest = (Node) xPath.evaluate("proforma:test-meta-data/gate:tutor-test", testConfiguration, XPathConstants.NODE);
					Node testShowDetails = (Node) xPath.evaluate("proforma:test-meta-data/gate:show-details-to-students", testConfiguration, XPathConstants.NODE);

					TestDAOIf testDAO = DAOFactory.TestDAOIf(session);
					Test test = null;
					if (testType.getTextContent().equals("java-compilation")) {
						test = testDAO.createCompileTest(task);
					} else if (testType.getTextContent().equals("unittest")) {
						Node testUnittest = (Node) xPath.evaluate("unit:unittest", testConfiguration, XPathConstants.NODE);
						Node framework = testUnittest.getAttributes().getNamedItem("framework");
						if (framework == null) {
							throw new Exception("Not a valid exchange format XML file");
						}
						Node testUnittestVersion = testUnittest.getAttributes().getNamedItem("version");
						if (testUnittestVersion == null) {
							throw new Exception("Not a valid exchange format XML file");
						}
						
						Node testUnittestMainclass = (Node) xPath.evaluate("unit:main-class", testUnittest, XPathConstants.NODE);
					    if (testUnittestMainclass == null || testUnittestMainclass.getTextContent() == null || testUnittestMainclass.getTextContent().isEmpty()) {
						    throw new Exception("Not a valid exchange format XML file");
					    }
					    Node unitTestTimeout = (Node) xPath.evaluate("unit:timeout", testUnittest, XPathConstants.NODE);
					    
					    JUnitTest juTest = testDAO.createJUnitTest(task);
						test = juTest;
						
						File modelSolution = new File(taskPath, "modelsolutionfiles");
						File classPath = new File(contextAdapter.getDataPath().getAbsolutePath());

						File testTmpDir = Util.createTemporaryDirectory("junit", null);
						File modelsolutionTmpDir = Util.createTemporaryDirectory("solution", null);
						Util.recursiveCopy(modelSolution, modelsolutionTmpDir);
					 	File jarFile = new File(taskPath, "junittest" + test.getId() + ".jar");

						juTest.setMainClass(testUnittestMainclass.getTextContent());

						NodeList referencedFileEntries = (NodeList) xPath.evaluate("proforma:test-configuration/proforma:filerefs/proforma:fileref", testEntry.item(i), XPathConstants.NODESET);
						for (int j = 0; j < referencedFileEntries.getLength(); ++j) {
						    Node junitTestRefId = referencedFileEntries.item(j).getAttributes().getNamedItem("refid");
						    if (junitTestRefId == null || junitTestRefId.getNodeValue() == null || junitTestRefId.getNodeValue().isEmpty()){
								throw new Exception("Not a valid exchange format XML file");
						    } else {
						        Node referencedFileEntry = (Node) xPath.evaluate("/proforma:task/proforma:files/proforma:file[@id=\"" + junitTestRefId.getNodeValue() + "\"]", doc.getDocumentElement(), XPathConstants.NODE);
							    if (referencedFileEntry == null) {
							        throw new Exception("Not a valid exchange format XML file");
							    }
							    saveReferencedFile(testTmpDir, tempDir, referencedFileEntry, true);
						    }
						}

						compileJava(testTmpDir, new File(classPath, "junit.jar"), modelsolutionTmpDir);
						writeToJarfile(testTmpDir, jarFile);
						Util.recursiveDelete(testTmpDir);
						Util.recursiveDelete(modelsolutionTmpDir);
						
						if (unitTestTimeout != null) {
							test.setTimeout(Integer.parseInt(unitTestTimeout.getTextContent()));
						} else {
							test.setTimeout(15);
						}
					}
					if (test != null) {
						test.setTestTitle(testTitle.getTextContent());
						if (testDescription != null) {
							test.setTestDescription(testDescription.getTextContent());
						}
						if (testRunnableByStudents != null) {
							test.setTimesRunnableByStudents(Integer.parseInt(testRunnableByStudents.getTextContent()));
						}
						if (testTutorTest != null) {
							test.setForTutors(Boolean.parseBoolean(testTutorTest.getTextContent()));
						}
						if (testShowDetails != null) {
							test.setGiveDetailsToStudents(Boolean.parseBoolean(testShowDetails.getTextContent()));
						}
						if (testTimeout != null) {
							test.setTimeout(Integer.parseInt(testTimeout.getTextContent()));
						} else {
							test.setTimeout(15);
						}
						testDAO.saveTest(test);
					}
				}
			}

			//Import GATE grading-hints
			Node gateGradingHints = (Node) xPath.evaluate("/proforma:task/proforma:grading-hints", doc.getDocumentElement(), XPathConstants.NODE);
			if (gateGradingHints != null) {
				Node minPointStep = (Node) xPath.evaluate("gate:min-point-step", gateGradingHints, XPathConstants.NODE);
				if (minPointStep != null) {
					task.setMinPointStep(Integer.parseInt(minPointStep.getTextContent()));
				}
				Node maxPoints = (Node) xPath.evaluate("gate:max-points", gateGradingHints, XPathConstants.NODE);
				if (maxPoints != null) {
					task.setMaxPoints(Integer.parseInt(maxPoints.getTextContent()));
				} else {
					PointCategoryDAOIf pointCategoryDAO = DAOFactory.PointCategoryDAOIf(session);
					NodeList pointCategories = (NodeList) xPath.evaluate("gate:pointy-category", gateGradingHints, XPathConstants.NODESET);
					for (int i = 0; i < pointCategories.getLength(); ++i) {
						Node node = pointCategories.item(i);
						Node pointCategoryMaxPoints = node.getAttributes().getNamedItem("max-points");
						if (pointCategoryMaxPoints == null) {
							throw new Exception("Not a valid exchange format XML file");
						}
						Node pointCategoryOptional = node.getAttributes().getNamedItem("optional");
						boolean optional = false;
						if (pointCategoryOptional != null) {
							optional = pointCategoryOptional.getNodeValue().equals("yes") || pointCategoryOptional.getNodeValue().equals("true");
						}
						pointCategoryDAO.newPointCategory(task, Integer.parseInt(pointCategoryMaxPoints.getNodeValue()), node.getTextContent(), optional);
					}
					task.setMaxPoints(pointCategoryDAO.countPoints(task));
				}
			}

			//Import task meta-data
			if (title.isEmpty()) {
				Node taskTitle = (Node) xPath.evaluate("/proforma:task/proforma:meta-data/proforma:title", doc.getDocumentElement(), XPathConstants.NODE);
				if (taskTitle == null) {
					throw new Exception("Not a valid exchange format XML file");
				}
				if (!taskTitle.getTextContent().isEmpty())
					task.setTitle(taskTitle.getTextContent());
			}

			session.update(task);
			tx.commit();

			Util.recursiveDelete(tempDir);
		} catch (Exception e) {
			e.printStackTrace();
			System.out.println(e.toString());
			System.err.println(e.getMessage());
			if (tx != null) {
				tx.rollback();
			}
			request.setAttribute("title", "Import fehlgeschlagen");
			request.getRequestDispatcher("MessageView").forward(request, response);
			Util.recursiveDelete(tempDir);
			return;
		}
		response.sendRedirect(response.encodeRedirectURL("TaskManager?lecture=" + task.getTaskGroup().getLecture().getId() + "&taskid=" + task.getTaskid() + "&action=editTask"));
	}

	private static void saveReferencedFile(File destDir, File sourceDir, Node node, boolean relocateJavaFiles) throws Exception {
		Node destFileNameNode = node.getAttributes().getNamedItem("filename");
		Node fileType = node.getAttributes().getNamedItem("type");

		if (node.getTextContent() == null) {
			throw new Exception("Not a valid exchange format XML file");
		}

		String destFilename = null;
		if (destFileNameNode != null && isSafeFilename(destFileNameNode.getNodeValue())) {
			destFilename = destFileNameNode.getNodeValue();
		}
		if (destFilename == null) {
			throw new Exception("Not a valid exchange format XML file");
		}

		File destFile = new File(sourceDir, destFilename);
		// type is either file or embedded
		if (fileType != null && "file".equals(fileType.getNodeValue())) {
			String sourceFilename = node.getTextContent();
			if (!isPathSafe(sourceDir, sourceFilename)) {
				throw new Exception("Not a valid exchange format XML file");
			}
			Util.recursiveCopy(new File(sourceDir, sourceFilename), destFile);
		} else { // fileType is embedded by default
			FileWriter fileWriter = new FileWriter(destFile);
			fileWriter.write(node.getTextContent());
			fileWriter.close();
		}

		if (relocateJavaFiles) {
			Util.relocateJavaFile(destDir, destFile, destFile.getName());
		}
	}

	static boolean isPathSafe(File path, String filename) {
		Pattern safeFileNamePattern = Pattern.compile("^(([/a-zA-Z0-9_ .-]*?/)?([a-zA-Z0-9_ .-]+))$");
		if (filename == null) {
			return false;
		}
		try {
			if (!new File(path, filename).getCanonicalPath().startsWith(path.getCanonicalPath()))
				return false;
		} catch (IOException e) {
			// i.e. filename not valid on system
			return false;
		}
		StringBuffer filenameStringBuffer = new StringBuffer(filename);
		if (!safeFileNamePattern.matcher(filenameStringBuffer).matches()) {
			System.err.println("Ignored entry: " + filenameStringBuffer + ";" + safeFileNamePattern.pattern());
			return false;
		}
		return true;
	}

	static boolean isSafeFilename(String filename) {
		if (filename == null) {
			return false;
		}
		StringBuffer filenameStringBuffer = new StringBuffer(filename);
		Pattern safeFileNamePattern = Pattern.compile("^([a-zA-Z0-9_ .-]+)$");
		if (!safeFileNamePattern.matcher(filenameStringBuffer).matches()) {
			System.err.println("Ignored entry: " + filenameStringBuffer + ";" + safeFileNamePattern.pattern());
			return false;
		}
		return true;
	}

	// Compile JUnit testfile
	public void compileJava(File javaPath, File jUnitFile, File sourcePath) throws Exception {
		int compiles = 1;
		JavaCompiler jc = ToolProvider.getSystemJavaCompiler();
		ByteArrayOutputStream errorOutputStream = new ByteArrayOutputStream();
		try {
			List<String> javaFiles = new LinkedList<String>();
			javaFiles.add("-classpath");
			javaFiles.add(jUnitFile.getAbsolutePath()); // TODO: libraries
			javaFiles.add("-sourcepath");
			javaFiles.add(sourcePath.getAbsolutePath());
			JavaSyntaxTest.getRecursivelyAllJavaFiles(javaPath, javaFiles);

			if (javaFiles.size() > 0) {
				compiles = jc.run(null, null, errorOutputStream, javaFiles.toArray(new String[] {}));
			}
		} catch (Exception e) {
			System.err.println("System.getProperty(\"java.home\") should point to a jre in a jdk directory and tools.jar must be in the classpath");
			throw e;
		}
		if (compiles != 0) {
			System.out.println(errorOutputStream.toString());
		}
	}

	// Generate jar file for JUnit-test import
	public void writeToJarfile(File testTmpDir, File outPath) throws IOException {
		Manifest manifest = new Manifest();
		manifest.getMainAttributes().put(Attributes.Name.MANIFEST_VERSION, "1.0");

		JarOutputStream jarOutputStream = new JarOutputStream(new FileOutputStream(outPath), manifest);
		for (String file : Util.listFilesAsRelativeStringList(testTmpDir)) {
			File jarEntry = new File(testTmpDir, file);
			jarOutputStream.putNextEntry(new JarEntry(jarEntry.getName()));
			Util.copyInputStreamAndClose(new FileInputStream(jarEntry), jarOutputStream);
		}
		jarOutputStream.close();
	}
}
